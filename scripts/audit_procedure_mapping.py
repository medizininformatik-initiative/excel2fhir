#!/usr/bin/env python3
"""Verify OPS decisions against pinned source modules and the official catalogue.

Usage: audit_procedure_mapping.py SYNTHEA_CHECKOUT OPS_TERMINAL_CATALOGUE.json
No projection code is invoked. The BfArM rendering catalogue contains a rows array.
"""
import hashlib
import json
from pathlib import Path
import sys

PATH = Path(__file__).with_name('mappings') / 'synthea-procedures-ops-2026.json'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def validate_structure(table):
    entries = table['entries']
    if len({e['sourceCode'] for e in entries}) != len(entries):
        raise ValueError('Duplicate source concept')
    for e in entries:
        p = e.get('provenance', {})
        if not p.get('rationale') or not p.get('sourceEvidence') or not p.get('reviewStatus'):
            raise ValueError('Missing mapping provenance: ' + e['sourceCode'])
        if 'contextualRule' in e and e['contextualRule'] not in table['contextualRules']:
            raise ValueError('Unknown contextual rule')
    for key, rule in table['contextualRules'].items():
        if key != rule['id'] or not rule['assumption']:
            raise ValueError('Invalid rule identity/assumption')
        if set(rule['hardGuards']) - table['guardDefinitions'].keys():
            raise ValueError('Unknown guard reference')
        ids = [c['id'] for c in rule['candidates']]
        if not ids or len(ids) != len(set(ids)):
            raise ValueError('Empty or duplicate candidate IDs')
        if any(type(c['weight']) is not int or c['weight'] <= 0 for c in rule['candidates']):
            raise ValueError('Invalid candidate weight')
    return len(entries)


def audit(checkout, catalogue, mapping=PATH):
    table = json.loads(Path(mapping).read_text())
    count = validate_structure(table)
    pinned = PATH.parent.parent.joinpath('synthea-version.txt').read_text().strip()
    if table['provenancePolicy']['sourceSnapshot']['commit'] != pinned:
        raise ValueError('Mapping evidence and generator pin differ')
    raw = Path(catalogue).read_bytes()
    if digest(raw) != table['targetSource']['sha256']:
        raise ValueError('Target catalogue fingerprint differs; review the release before updating evidence')
    targets = {r['Code']: r['Display'] for r in json.loads(raw)['rows']}
    seen_targets = set()
    def check_targets(o):
        if isinstance(o, dict):
            if o.get('system') == 'http://fhir.de/CodeSystem/bfarm/ops':
                if targets.get(o.get('code')) != o.get('display') or o.get('version') != '2026':
                    raise ValueError('Invalid target code/title/version: ' + str(o.get('code')))
                seen_targets.add(o['code'])
            for v in o.values():
                check_targets(v)
        elif isinstance(o, list):
            for v in o:
                check_targets(v)
    check_targets(table)
    files, locations = {}, 0
    observed = set()
    for e in table['entries']:
        for evidence in e['provenance']['sourceEvidence']:
            path = evidence['file']
            if path not in files:
                raw = (Path(checkout) / path).read_bytes()
                files[path] = (digest(raw), json.loads(raw))
            file_hash, module = files[path]
            if file_hash != evidence['fileSha256']:
                raise ValueError('Changed source module: ' + path)
            item = module
            for part in evidence['jsonPointer'].split('/')[1:]:
                part = part.replace('~1', '/').replace('~0', '~')
                item = item[int(part)] if isinstance(item, list) else item[part]
            if str(item['code']) != e['sourceCode']:
                raise ValueError('Source pointer/code mismatch')
            if evidence.get('state'):
                state = module['states'][evidence['state']]
                canonical = json.dumps(state, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
                if digest(canonical) != evidence['stateSha256']:
                    raise ValueError('Changed source state')
            locations += 1
    # Detect new/removed productive Procedure concepts, including new module files.
    for path in (Path(checkout) / 'src/main/resources/modules').rglob('*.json'):
        for state in json.loads(path.read_text()).get('states', {}).values():
            if state.get('type') == 'Procedure':
                observed.update(str(c['code']) for c in state.get('codes', []))
    if observed | {'418023006'} != {e['sourceCode'] for e in table['entries']}:
        raise ValueError('Source procedure inventory changed')
    return {'sourceConcepts': count, 'sourceLocations': locations, 'sourceFiles': len(files),
            'terminalTargets': len(seen_targets), 'status': 'PASSED',
            'scope': 'Source fingerprints, inventory and terminal codes; not clinical equivalence.'}


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    print(json.dumps(audit(*sys.argv[1:]), indent=2))
