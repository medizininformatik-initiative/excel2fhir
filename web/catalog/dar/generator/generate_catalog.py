#!/usr/bin/env python3
"""Build the reviewed DAR catalogue from local profile packages and semantic rules."""
import hashlib
import json
from pathlib import Path
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'web/catalog/dar/source/field-rules.json'
OUTPUT = ROOT / 'web/catalog/dar/generated/catalog.json'
RESOURCE_DIR = ROOT / 'src/main/resources'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_profiles():
    definitions, packages = {}, []
    manifest = RESOURCE_DIR / 'fhir-packages.txt'
    for line in manifest.read_text().splitlines():
        name = line.strip()
        if not name or name.startswith('#'):
            continue
        path = RESOURCE_DIR / 'fhir' / name
        packages.append({'file': name, 'sha256': digest(path)})
        with tarfile.open(path) as archive:
            for member in archive.getmembers():
                if not member.name.endswith('.json'):
                    continue
                try:
                    value = json.load(archive.extractfile(member))
                except (ValueError, AttributeError):
                    continue
                if value.get('resourceType') == 'StructureDefinition':
                    definitions[value['url']] = value
    return definitions, packages


def elements(definitions, url, stack=()):
    if url in stack:
        raise ValueError('Cyclic profile inheritance: ' + url)
    profile = definitions[url]
    if profile.get('snapshot'):
        return profile['snapshot']['element']
    base = profile.get('baseDefinition')
    merged = {e['id']: dict(e) for e in elements(definitions, base, stack + (url,))} if base else {}
    for change in profile.get('differential', {}).get('element', []):
        original = merged.get(change['id'], {})
        combined = {**original, **change}
        if 'constraint' in change:
            constraints = {c['key']: c for c in original.get('constraint', [])}
            constraints.update({c['key']: c for c in change['constraint']})
            combined['constraint'] = list(constraints.values())
        merged[change['id']] = combined
    return list(merged.values())


def resolve(definitions, url, path):
    entries = elements(definitions, url)
    exact = next((e for e in entries if e['id'] == path), None)
    if exact:
        return exact
    # Datatype descendants are often absent from a resource snapshot.
    parts = path.split('.')
    for size in range(len(parts) - 1, 0, -1):
        prefix = '.'.join(parts[:size])
        parent = next((e for e in entries if e['id'] == prefix), None)
        if not parent:
            continue
        for type_spec in parent.get('type', []):
            datatype = type_spec['code']
            target = 'http://hl7.org/fhir/StructureDefinition/' + datatype
            if target not in definitions:
                continue
            try:
                return resolve(definitions, target, '.'.join([datatype] + parts[size:]))
            except ValueError:
                pass
    raise ValueError(f'Unresolved element {url}: {path}')


def main():
    source = json.loads(SOURCE.read_text())
    labels_path = RESOURCE_DIR / 'workbook-absent-reasons.json'
    labels = json.loads(labels_path.read_text())
    definitions, packages = load_profiles()
    fields, ids, profile_metadata = [], set(), {}
    for rule in source['fields']:
        if rule['id'] in ids:
            raise ValueError('Duplicate field id: ' + rule['id'])
        ids.add(rule['id'])
        profile = definitions[rule['profile']]
        entries = elements(definitions, rule['profile'])
        evidence = []
        for target in rule['targets']:
            element = resolve(definitions, rule['profile'], target)
            if element.get('max') == '0':
                raise ValueError('Forbidden field: ' + target)
            if any(k.startswith(('fixed', 'pattern')) for k in element):
                raise ValueError('Cannot replace fixed/pattern value: ' + target)
            # A parent pattern may prescribe this descendant's literal value.
            for ancestor in entries:
                if not target.startswith(ancestor['id'] + '.'):
                    continue
                suffix = target[len(ancestor['id']) + 1:].split('.')
                for key, value in ancestor.items():
                    if not key.startswith('pattern'):
                        continue
                    for part in suffix:
                        value = value.get(part) if isinstance(value, dict) else None
                    if value is not None:
                        raise ValueError('Ancestor pattern fixes field: ' + target)
            evidence.append({'target': target, 'definitionElement': element['id'],
                             'types': [t['code'] for t in element.get('type', [])],
                             'min': element.get('min', 0), 'max': element.get('max')})
        group = source['semanticGroups'][rule['semanticGroup']]
        codes = group['allowedCodes']
        if not codes or len(codes) != len(set(codes)) or any(c not in labels for c in codes):
            raise ValueError('Invalid DAR code list: ' + rule['id'])
        constraints = {}
        for e in entries:
            for c in e.get('constraint', []):
                if not c['key'].startswith(('dom-', 'ele-', 'ext-', 'ref-')):
                    constraints[c['key']] = {k: c[k] for k in ('key', 'severity', 'expression', 'human') if k in c}
        profile_metadata[rule['profile']] = {'version': profile.get('version'), 'constraints': list(constraints.values())}
        fields.append({**rule, 'profileVersion': profile.get('version'), 'allowedCodes': codes,
                       'help': group['help'], 'codeConditions': group.get('codeConditions', {}),
                       'profileEvidence': evidence})
    result = {'schemaVersion': 1, 'scope': 'Supported converter fields; semantic allowlists are reviewed project rules.',
              'sources': {'rules': {'file': 'web/catalog/dar/source/field-rules.json', 'sha256': digest(SOURCE)},
                          'labels': {'file': 'src/main/resources/workbook-absent-reasons.json', 'sha256': digest(labels_path)},
                          'generatorSha256': digest(Path(__file__)), 'packages': packages},
              'codes': [{'code': c, 'label': label} for c, label in labels.items()],
              'profiles': profile_metadata, 'fields': fields}
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if '--check' in sys.argv:
        if not OUTPUT.exists() or OUTPUT.read_text() != text:
            raise SystemExit('DAR catalogue is stale. Run python3 web/catalog/dar/generator/generate_catalog.py')
        print(f'DAR catalogue is current: {len(fields)} fields')
    else:
        OUTPUT.write_text(text)
        print(f'Generated {len(fields)} DAR fields: {OUTPUT.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
