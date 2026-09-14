#!/usr/bin/env python3
"""Check curated mappings against the original German ATC workbook.

Usage: audit_medication_mapping.py 'Amtliche Fassung des ATC-Index 2026.xlsx'
The original catalogue stays outside the repository. This verifies membership,
not clinical equivalence; product evidence is recorded per selected pack.
"""
import hashlib
import json
from pathlib import Path
import sys
from national_medication_mapping import PATH, NationalMedicationMapping, RXNORM
from workbook_xml import read_sheets


def audit(atc_workbook):
    data = json.loads(PATH.read_text())
    registry_path = PATH.with_name('synthea-source-code-registry.json')
    assert hashlib.sha256(registry_path.read_bytes()).hexdigest() == data['sourceRegistrySha256']
    registry = json.loads(registry_path.read_text())
    sources = {(e['system'], e['code']): e for e in registry['entries'] if e['system'] == RXNORM}
    mapping = NationalMedicationMapping()
    assert sources.keys() == mapping.entries.keys(), 'Quellinventar unvollständig'
    sheet = read_sheets(atc_workbook)['Amtl. Index 2026 ATC-sortiert']
    codes = {value.strip() for cell, value in sheet.items() if cell.startswith('A') and cell[1:].isdigit()}
    for key, entry in mapping.entries.items():
        assert entry['source']['displays'] == sources[key]['displays'], key
        if entry['atc']:
            assert entry['atc']['code'] in codes, (key, entry['atc'])
    return {'sourceConcepts': len(sources),
            'atcClassified': sum(bool(e['atc']) for e in mapping.entries.values()),
            'selectedPacks': sum(bool(e['product']) for e in mapping.entries.values()),
            'sourceAndGerman2026Membership': 'passed', 'clinicalReview': 'pending'}


if __name__ == '__main__':
    print(json.dumps(audit(sys.argv[1]), ensure_ascii=False, indent=2))
