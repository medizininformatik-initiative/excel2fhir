#!/usr/bin/env python3
"""Audit mapping source membership and official GM target validity.

Usage: audit_diagnosis_mapping.py SYNTHEA_CHECKOUT ICD_CODESYSTEM.json TERMINAL_VALUESET.json
Read-only; official catalogues and the Synthea checkout stay outside this repository.
"""
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys


def condition_sources(checkout):
    """Only production ConditionOnset primary codes can become R4 Condition.code.

    Templates, ConditionEnd, logic checks and other resource types are not sources.
    Runtime extensions/remote value sets are outside this closed source inventory.
    """
    checkout = Path(checkout)
    result = {}
    modules = checkout / 'src/main/resources/modules'
    if not modules.is_dir():
        raise ValueError('Missing production Synthea modules')
    for path in sorted(modules.rglob('*.json')):
        raw = path.read_bytes()
        data = json.loads(raw)
        for name, state in data.get('states', {}).items():
            if state.get('type') != 'ConditionOnset':
                continue
            codes = state.get('codes', [])
            if len(codes) != 1 or codes[0].get('system') not in ('SNOMED-CT', 'http://snomed.info/sct'):
                raise ValueError(f'Unreviewed ConditionOnset coding structure: {path}:{name}')
            coding = codes[0]
            if not coding.get('code') or not coding.get('display') or coding.get('value_set'):
                raise ValueError(f'Unresolved ConditionOnset coding: {path}:{name}')
            entry = result.setdefault(coding['code'], {'displays': set(), 'sources': []})
            entry['displays'].add(coding['display'])
            entry['sources'].append({'file': str(path.relative_to(checkout)), 'state': name,
                                     'sha256': hashlib.sha256(raw).hexdigest()})
    return result


def audit(checkout, catalogue_path, terminal_path, mapping_path=None):
    mapping_path = mapping_path or Path(__file__).parent / 'mappings/synthea-diagnoses-icd10gm-2026.json'
    table = json.loads(Path(mapping_path).read_text())
    sources = condition_sources(checkout)
    entries = table['entries']
    by_code = {e['sourceCode']: e for e in entries}
    if len(by_code) != len(entries) or set(by_code) != set(sources):
        raise ValueError('Mapping must assess exactly the production ConditionOnset source codes')
    catalogue_raw = Path(catalogue_path).read_bytes()
    terminal_raw = Path(terminal_path).read_bytes()
    metadata = table['targetCatalogue']
    if (hashlib.sha256(catalogue_raw).hexdigest() != metadata['sha256']
            or hashlib.sha256(terminal_raw).hexdigest() != metadata['terminalValueSetSha256']):
        raise ValueError('Official target catalogue differs from the reviewed snapshot')
    catalogue = {c['code']: c for c in json.loads(catalogue_raw)['concept']}
    terminals = {c['code'] for c in json.loads(terminal_raw)['expansion']['contains']}
    for code, source in sources.items():
        entry = by_code[code]
        if (set(entry['sourceDisplays']) != source['displays'] or entry['sources'] != source['sources']
                or entry['sourceDisplay'] not in source['displays']):
            raise ValueError('Source evidence changed: ' + code)
        if not entry['reason'] or entry['relation'] not in ('approximate', 'unmapped'):
            raise ValueError('Missing assessment: ' + code)
        target = entry['target']
        if (target is None) != (entry['relation'] == 'unmapped'):
            raise ValueError('Inconsistent mapping decision: ' + code)
        if target:
            if (target['code'] not in terminals or target['code'] not in catalogue
                    or catalogue[target['code']]['display'] != target['display']
                    or target['system'] != 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
                    or target['version'] != '2026'):
                raise ValueError('Invalid target: ' + code)
    return {'sourceConcepts': len(sources), 'sourceOccurrences': sum(len(s['sources']) for s in sources.values()),
            'decisions': dict(Counter(e['relation'] for e in entries)),
            'unassessed': 0, 'sourceAndTerminalTargetAudit': 'passed',
            'independentSemanticValidation': 'not performed'}


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    print(json.dumps(audit(*sys.argv[1:]), indent=2))
