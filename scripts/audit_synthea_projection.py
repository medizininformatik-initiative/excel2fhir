#!/usr/bin/env python3
"""Independent source/Excel/FHIR audit; deliberately does not call import/mapping functions.
Usage: audit_synthea_projection.py SOURCE.json WORKBOOK.xlsx TARGET.json LOSS.json
Mapping decisions are checked against the versioned data tables; medical equivalence
and terminology membership require the separate terminology and human reviews.
"""
from collections import Counter
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import sys
import uuid
from workbook_xml import read_sheets
from workbook_absent import canonical

MAPS = Path(__file__).parent / 'mappings'
ATC = 'http://fhir.de/CodeSystem/bfarm/atc'
PZN = 'http://fhir.de/CodeSystem/ifa/pzn'
RX = 'http://www.nlm.nih.gov/research/umls/rxnorm'
CVX = 'http://hl7.org/fhir/sid/cvx'
SNOMED = 'http://snomed.info/sct'


def rows(cells):
    headers = {re.sub(r'1$', '', c): v for c, v in cells.items() if re.fullmatch(r'[A-Z]+1', c)}
    help_col = next((c for c, h in headers.items() if h == 'Erklärung/Ausfüllhilfe'), None)
    groups = {}
    for cell, value in cells.items():
        col, number = re.fullmatch(r'([A-Z]+)(\d+)', cell).groups()
        if number == '1' or col == help_col or col not in headers or not value: continue
        groups.setdefault(int(number), {})[headers[col]] = value
    return [groups[n] for n in sorted(groups)]


def codings(cc):
    return tuple((c.get('system'), c.get('code'), c.get('version')) for c in cc.get('coding', []))


def all_codings(obj):
    if isinstance(obj, dict):
        if 'system' in obj and 'code' in obj: yield obj
        for value in obj.values(): yield from all_codings(value)
    elif isinstance(obj, list):
        for value in obj: yield from all_codings(value)


def decimal(value):
    if isinstance(value, str) and value.startswith('!dar:'):
        return value
    return str(Decimal(str(value)).normalize()) if value not in (None, '') else ''


def volume_unit(value):
    # UCUM permits both spellings of litre; the Java converter emits capital L.
    return {'ml': 'mL', 'l': 'L'}.get(value, value)


def audit(source, workbook, target, report):
    src = [e['resource'] for e in source['entry']]
    dst = [e['resource'] for e in target['entry']]
    sheets = {name: [canonical(name, row) for row in rows(cells)] for name, cells in read_sheets(workbook).items()
              if name not in ('Codes', 'Konvertierungsoptionen')}
    source_counts, target_counts = Counter(r['resourceType'] for r in src), Counter(r['resourceType'] for r in dst)
    assert 'Allergie' not in sheets and not target_counts['AllergyIntolerance']
    excluded_allergies = {l['id'] for l in report['losses'] if l['resourceType'] == 'AllergyIntolerance'
                         and l['path'] == '$' and 'Bewusst ausgeschlossen' in l['reason']}
    assert excluded_allergies == {r['id'] for r in src if r['resourceType'] == 'AllergyIntolerance'}
    for typ, sheet in [('Condition', 'Diagnose'), ('Procedure', 'Prozedur'), ('Immunization', 'Impfung'),
                       ('DiagnosticReport', 'Befundbericht'), ('CarePlan', 'Behandlungsplan'), ('Device', 'Hilfsmittel')]:
        assert source_counts[typ] == len(sheets[sheet]) == target_counts[typ], (typ, 'unintended event loss')
    assert source_counts['Patient'] == target_counts['Patient'] == len(sheets['Person']) == 1
    all_codes = list(all_codings(dst))
    assert not [c for c in all_codes if c['system'] in (RX, CVX) or '/us/core/' in c['system'] or (c['system'] == SNOMED and c['code'] == '456191000124101')], 'US-specific target coding'
    assert not any(v in (RX, CVX, 'RxNorm', 'CVX') for rr in sheets.values() for row in rr for v in row.values()), 'US coding in Excel data'
    for resource in dst:
        cc = resource.get('code', {})
        codes = codings(cc)
        if resource['resourceType'] in ('Condition', 'Procedure'):
            national = 'http://fhir.de/CodeSystem/bfarm/' + ('icd-10-gm' if resource['resourceType'] == 'Condition' else 'ops')
            if any(c[0] == national for c in codes): assert codes[0][0] == national
        if resource['resourceType'] == 'Medication':
            ranks = [{PZN: 0, ATC: 1}.get(c[0], 2) for c in codes]
            assert ranks == sorted(ranks)
    for c in all_codes:
        if c['system'] in (ATC, 'http://fhir.de/CodeSystem/bfarm/ops', 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'):
            assert c.get('version') == '2026', c
    patient = next(r for r in src if r['resourceType'] == 'Patient')
    pid = patient['id'].replace('_', '-')
    index = {r['resourceType']+'/'+r['id']: r for r in src}
    index.update({e['fullUrl']: e['resource'] for e in source['entry'] if e.get('fullUrl')})
    meds = {r['id']: r for r in dst if r['resourceType'] == 'Medication'}
    mapping = {e['source']['code']: e for e in json.loads((MAPS/'synthea-medications-de-2026.json').read_text())['entries']}
    source_meds = [r for r in src if r['resourceType'] in ('MedicationRequest', 'MedicationAdministration')]
    assert len(source_meds) == len(sheets['Medikation']) == target_counts['MedicationRequest'] + target_counts['MedicationAdministration']
    expected_events = Counter()
    for resource, row in zip(source_meds, sheets['Medikation']):
        typ = resource['resourceType']
        cc = resource.get('medicationCodeableConcept') or index[resource['medicationReference']['reference']]['code']
        first = cc['coding'][0]
        assert first['system'] == RX, 'Extend independent audit for a new source medication system'
        entry = mapping.get(str(first['code']), {})
        assert row.get('ATC-Code', '') == (entry.get('atc') or {}).get('code', '')
        product = entry.get('product') or {}
        assert row.get('Präparatcode', '') == product.get('code', '')
        if not product: assert 'PZN-Zuordnung offen' in row['Präparatbezeichnung']
        if not entry.get('atc'): assert 'ATC-Zuordnung offen' in row['Präparatbezeichnung']
        assert row['Medikationstyp'] == ('Verordnung' if typ == 'MedicationRequest' else 'Verabreichung')
        dose = resource.get('dosage', {}) if typ == 'MedicationAdministration' else (resource.get('dosageInstruction') or [{}])[0]
        quantity = dose.get('dose', {}) if typ == 'MedicationAdministration' else next((v['doseQuantity'] for v in dose.get('doseAndRate', []) if 'doseQuantity' in v), {})
        repeat = dose.get('timing', {}).get('repeat', {})
        frequency = str(repeat['frequency']) if repeat.get('period') == 1 and repeat.get('periodUnit') == 'd' and 'frequency' in repeat else ''
        enrichment = entry.get('enrichment') or {}
        if enrichment.get('resetDose'):
            replacement = enrichment['dose']
            quantity = {'value': replacement['value'], 'unit': replacement['unit']}
            frequency = replacement['frequency']
            assert row.get('Dosierungstext', '') == replacement['text']
        if quantity.get('value') not in (None, '') and not quantity.get('code', quantity.get('unit', '')) and product.get('sourceCountUnit'):
            quantity = {**quantity, 'unit': product['sourceCountUnit']}
        assert decimal(row.get('Einzeldosis')) == decimal(quantity.get('value'))
        assert row.get('Dosiereinheit', '') == quantity.get('code', quantity.get('unit', ''))
        assert row.get('Dosen pro Tag', '') == frequency
        moment = resource.get('authoredOn', '') if typ == 'MedicationRequest' else resource.get('effectiveDateTime', resource.get('effectivePeriod', {}).get('start', ''))
        assert row.get('Dokumentationszeitpunkt' if typ == 'MedicationRequest' else 'Beginn', '') == moment
        end = resource.get('effectivePeriod', {}).get('end', '')
        assert row.get('Ende', '') == end
        codes = []
        if row.get('Präparatcode'): codes.append((PZN, row['Präparatcode'], None))
        if row.get('ATC-Code'): codes.append((ATC, row['ATC-Code'], '2026'))
        expected_events[(typ, tuple(codes), row['Präparatbezeichnung'], row.get('Darreichungsform', ''),
                         resource.get('status', ''), moment, end, decimal(quantity.get('value')),
                         volume_unit(quantity.get('code', quantity.get('unit', ''))), frequency)] += 1
    actual_events = Counter(); text_doses = 0
    for resource in dst:
        typ = resource['resourceType']
        if typ not in ('MedicationRequest', 'MedicationAdministration'): continue
        med = meds[resource['medicationReference']['reference'].removeprefix('Medication/')]
        dose = resource.get('dosage', {}) if typ == 'MedicationAdministration' else (resource.get('dosageInstruction') or [{}])[0]
        quantity = dose.get('dose', {}) if typ == 'MedicationAdministration' else next((v['doseQuantity'] for v in dose.get('doseAndRate', []) if 'doseQuantity' in v), {})
        amount, unit = decimal(quantity.get('value')), quantity.get('code', quantity.get('unit', ''))
        for extension in quantity.get('_value', {}).get('extension', []):
            if extension.get('url') == 'http://hl7.org/fhir/StructureDefinition/data-absent-reason':
                assert not amount, 'Dose has both a numeric value and an absence reason'
                amount = '!dar:' + extension['valueCode']
        frequency = str(dose.get('timing', {}).get('repeat', {}).get('frequency', ''))
        text = dose.get('text', '')
        if not amount:
            match = re.search(r'(?:^|; )Einzeldosis: ([^ ;]+)(?: ([^;]+))?', text)
            if match:
                amount, unit = decimal(match[1]), match[2] or ''
                if unit == '(Einheit unbekannt)': unit = ''
                text_doses += 1
        if not frequency:
            match = re.search(r'(?:^|; )Dosen pro Tag: ([^;]+)', text)
            if match: frequency = match[1]
        moment = resource.get('authoredOn', '') if typ == 'MedicationRequest' else resource.get('effectiveDateTime', resource.get('effectivePeriod', {}).get('start', ''))
        actual_events[(typ, codings(med['code']), med['code'].get('text', ''), med.get('form', {}).get('text', ''),
                       resource.get('status', ''), moment, resource.get('effectivePeriod', {}).get('end', ''), amount, volume_unit(unit), frequency)] += 1
    assert expected_events == actual_events, {'missingMedicationEvents': list((expected_events-actual_events).items())[:2],
                                             'unexpectedMedicationEvents': list((actual_events-expected_events).items())[:2]}
    # Direct source quantities/answers/components, without reconstructing import rows.
    observations = {r['id']: r for r in dst if r['resourceType'] == 'Observation'}
    omitted_observations = {l['id'] for l in report['losses'] if l['resourceType']=='Observation' and l['path']=='$'}
    checked = 0
    def observation_value(before, after):
        if 'valueQuantity' in before:
            q, t = before['valueQuantity'], after['valueQuantity']
            assert decimal(q['value']) == decimal(t['value']) and q.get('code') == t.get('code')
        if 'valueCodeableConcept' in before: assert codings(before['valueCodeableConcept'])[:1] == codings(after['valueCodeableConcept'])
        if 'valueBoolean' in before: assert before['valueBoolean'] == after['valueBoolean']
        if 'dataAbsentReason' in before: assert codings(before['dataAbsentReason']) == codings(after['dataAbsentReason'])
    for resource in src:
        if resource['resourceType'] != 'Observation': continue
        if resource['id'] in omitted_observations: continue
        key = 'Observation-' + str(uuid.UUID(bytes=hashlib.md5((pid+'|Observation|'+resource['id']).encode()).digest(), version=3))
        found = observations[key];checked += 1
        expected_codes = codings(resource['code'])[:2]
        if expected_codes and expected_codes[0][0] == SNOMED and expected_codes[0][1] in ('413077008', '413078003'):
            assert resource['valueQuantity']['code'] == '{logmar}'
            correct = {'413077008': '6617-5', '413078003': '6616-7'}[expected_codes[0][1]]
            expected_codes = (('http://loinc.org', correct, None), expected_codes[0])
        assert expected_codes == codings(found['code'])
        assert resource.get('effectiveDateTime') == found.get('effectiveDateTime')
        assert resource.get('issued') == found.get('issued')
        assert resource.get('status') == found.get('status')
        observation_value(resource, found)
        assert len(resource.get('component', [])) == len(found.get('component', []))
        for before, after in zip(resource.get('component', []), found.get('component', [])):
            assert codings(before['code'])[:2] == codings(after['code']); observation_value(before, after)
    assert checked == len(observations)
    return {'status': 'PASSED', 'sourceCounts': dict(source_counts), 'targetCounts': dict(target_counts),
            'medicationEventsCompared': len(source_meds), 'medicationDosesPreservedAsText': text_doses,
            'observationsCompared': checked, 'explicitlyOmittedObservations': len(omitted_observations),
            'explicitlyExcludedAllergies': len(excluded_allergies), 'rxnormCvxOrUsCoreTargetCodings': 0,
            'limitations': ['Mapping equivalence requires human review; this is an independent event/value/code projection audit.',
                            'SNOMED edition membership is assessed separately, not inferred from namespace.']}


if __name__ == '__main__':
    if len(sys.argv) != 5: raise SystemExit(__doc__)
    source, book, target, loss = sys.argv[1:]
    print(json.dumps(audit(json.loads(Path(source).read_text()), book, json.loads(Path(target).read_text()),
                           json.loads(Path(loss).read_text())), ensure_ascii=False, indent=2))
