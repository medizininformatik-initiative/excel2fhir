#!/usr/bin/env python3
"""Reuse and verify the complete archived source inventory. No runtime terminology service.
Usage: build_synthea_code_registry.py INVENTORY_DIRECTORY SYNTHEA_CHECKOUT OUTPUT.json
The broader registry includes exporter/auxiliary codes and predicates; it is not a
count of primary diagnoses or a claim that every listed code is emitted in R4.
"""
import hashlib
import json
from collections import Counter
from pathlib import Path
import sys


def build(archive, checkout):
    archive, checkout = Path(archive), Path(checkout)
    manifest = json.loads((archive / 'source-manifest.json').read_text())
    expected = {item['file'] for item in manifest}
    # Gradle generates this version label; it contains no clinical concepts.
    generated = {'src/main/resources/version.txt'}
    actual = {p.relative_to(checkout).as_posix() for p in (checkout / 'src/main').rglob('*')
              if p.is_file()} - generated
    if actual != expected:
        raise ValueError('Synthea source file set changed; added: ' + str(sorted(actual - expected))
                         + '; missing: ' + str(sorted(expected - actual)))
    for item in manifest:
        path = checkout / item['file']
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Synthea source changed: ' + str(path))
    records = json.loads((archive / 'inventory-combined.json').read_text())
    groups = {}
    for row in records:
        if '/templates/' in row['file']:
            continue
        key = (row['system'], str(row['code']))
        entry = groups.setdefault(key, {'system': key[0], 'code': key[1], 'displays': set(),
                                        'usages': set(), 'sourceFiles': set()})
        entry['displays'].add(row['display'])
        entry['usages'].add(row['usage'])
        entry['sourceFiles'].add(row['file'])
    entries = []
    for key, entry in sorted(groups.items()):
        for field in ('displays', 'usages', 'sourceFiles'):
            entry[field] = sorted(entry[field])
        entries.append(entry)
    # Keep source facts independent of mapping decisions. Each mapping references
    # this inventory; copying decisions back here creates stale, circular state.
    return {'id': 'synthea-source-code-registry-v2', 'scope': 'verified archived source inventory excluding module templates; mapping decisions live in the separate mapping tables',
            'inventorySha256': hashlib.sha256((archive / 'inventory-combined.json').read_bytes()).hexdigest(),
            'sourceManifestSha256': hashlib.sha256((archive / 'source-manifest.json').read_bytes()).hexdigest(),
            'verifiedSourceFiles': len(manifest), 'conceptCount': len(entries),
            'systemCounts': dict(Counter(e['system'] for e in entries)), 'entries': entries}


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    result = build(*sys.argv[1:3])
    Path(sys.argv[3]).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(result['conceptCount'], 'concepts;', result['verifiedSourceFiles'], 'verified source files')
