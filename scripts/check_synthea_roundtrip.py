#!/usr/bin/env python3
"""Compare the diagnosis contract after Synthea -> Excel -> FHIR.
Usage: INPUT.json OUTPUT.json OUTPUT.loss.json
"""
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import sys
from diagnosis_mapping import map_diagnosis, mapping_metadata
from check_clinical_roundtrip import check_clinical
from check_movements import check_movements
from german_demographics import check_patient


def configured_condition_reference(condition, resources, level):
    """Check the chosen contact level against the emitted, separately checked hierarchy."""
    if level == 'none' or not condition.get('recordedDate'):
        return ''
    time = datetime.fromisoformat(condition['recordedDate'].replace('Z', '+00:00'))
    codes = {'facility': 'einrichtungskontakt', 'department': 'abteilungskontakt',
             'ward-service': 'versorgungsstellenkontakt'}
    candidates = []
    for resource in resources:
        if resource['resourceType'] != 'Encounter':
            continue
        types = {coding.get('code') for value in resource.get('type', []) for coding in value.get('coding', [])}
        if codes[level] not in types or (level == 'ward-service' and types.intersection({'operation', 'ub', 'konsil'})):
            continue
        period = resource.get('period', {})
        if not period.get('start'):
            continue
        start = datetime.fromisoformat(period['start'].replace('Z', '+00:00'))
        end = datetime.fromisoformat(period['end'].replace('Z', '+00:00')) if period.get('end') else None
        if start <= time and (end is None or time <= end):
            candidates.append((start, resource))
    if not candidates:
        return ''
    latest = max(candidates, key=lambda item: item[0])
    if latest[1].get('class', {}).get('code') == 'AMB':
        inpatients = [item for item in candidates if item[1].get('class', {}).get('code') == 'IMP']
        if inpatients:
            latest = max(inpatients, key=lambda item: item[0])
    return 'Encounter/' + latest[1]['id']


def check(source, target, report):
    src = [e['resource'] for e in source['entry']]
    dst = [e['resource'] for e in target['entry']]
    source_ids = {e.get('fullUrl'):e['resource'].get('id')for e in source['entry']}
    source_ids.update({r['resourceType']+'/'+r['id']:r['id']for r in src})
    target_ids = {r['resourceType']+'/'+r['id'] for r in dst}
    target_ids.update(e.get('fullUrl')for e in target['entry']if e.get('fullUrl'))
    pid = report.get('outputPatient', report['sourcePatient'].replace('_','-'))
    demographic_check = check_patient(next(r for r in src if r['resourceType']=='Patient'),
        next(r for r in dst if r['resourceType']=='Patient'), report)
    assert report['diagnosisMapping'] == mapping_metadata(), 'Use the mapping version that produced this workbook'
    from terminology_year import emitted, signature as coding_signature, metadata as terminology_metadata
    terminology = report.get('terminology', {})
    year, mode = terminology.get('mappingYear', '2026'), terminology.get('versionOutput', 'Jahr')
    if terminology:
        assert terminology == terminology_metadata(year, mode), 'Annual catalogue evidence changed'
    decisions = [map_diagnosis(r, year) for r in src if r['resourceType'] == 'Condition']
    assert report['diagnosisMappings'] == decisions, 'Mapping report differs from the versioned decisions'
    excluded = {d['sourceId'] for d in decisions if d['status'] == 'excluded'}
    assert excluded == {l['id'] for l in report['losses']
                        if l['resourceType'] == 'Condition' and l['path'] == '$'}, 'Unreported diagnosis exclusion'
    def signature(r, original):
        expected_codings = list(r['code']['coding'])
        if original:
            decision = map_diagnosis(r, year)
            if decision['target'] is not None:
                expected_codings.append(decision['target'])
        if not original and any(c['system'] == 'http://fhir.de/CodeSystem/bfarm/icd-10-gm' for c in expected_codings):
            assert expected_codings[0]['system'] == 'http://fhir.de/CodeSystem/bfarm/icd-10-gm', 'ICD-10-GM must be first'
        codings = tuple(sorted(coding_signature(emitted(c, mode) if original else c) for c in expected_codings))
        encounter = r.get('encounter',{}).get('reference','')
        options = report.get('converterOptions', {})
        configured_level = options.get('REFERENCE_CONDITION_ENCOUNTER')
        if original and configured_level is not None:
            encounter = configured_condition_reference(r, dst, configured_level)
        elif original and options.get('SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER', 'true') == 'false':
            encounter = ''
        if original and encounter and configured_level is None:
            encounter = 'Encounter/'+pid+'-E-'+report['encounterNumbers'][source_ids[encounter]]
        statuses = tuple(tuple(c['code']for c in r.get(field,{}).get('coding',[]))for field in ['clinicalStatus','verificationStatus'])
        if original and 'verificationStatusChange' in decision:
            statuses = (statuses[0], (decision['verificationStatusChange']['to'],))
        return (codings, r.get('recordedDate',''), r.get('onsetDateTime',''),r.get('abatementDateTime',''),encounter,statuses)
    original=Counter(signature(r,True)for r in src if r['resourceType']=='Condition' and r['id'] not in excluded)
    converted=Counter(signature(r,False)for r in dst if r['resourceType']=='Condition')
    assert original==converted, {'missing':list((original-converted).elements())[:2], 'extra':list((converted-original).elements())[:2]}
    for r in dst:
        if r['resourceType']=='Patient':continue
        for field in ['subject','patient','encounter','context']:
            if r.get(field,{}).get('reference'): assert r[field]['reference'] in target_ids
        for reference in r.get('result',[]): assert reference['reference'] in target_ids
    encounters={r['id']:r for r in dst if r['resourceType']=='Encounter'}
    for r in src:
        if r['resourceType']!='Encounter':continue
        found=encounters[pid+'-E-'+report['encounterNumbers'][r['id']]]
        if r['class']['code'] == 'EMER':
            assert found['class']['code'] == 'AMB'
            mapping = [m for m in report['encounterMappings'] if m['sourceId'] == r['id']]
            assert len(mapping) == 1 and mapping[0]['sourceClass'] == 'EMER' and mapping[0]['targetClass'] == 'AMB'
            assert mapping[0]['admissionReasonFourthComponent'] == '7'
            reasons = [e for e in found.get('extension', []) if e['url'] == 'http://fhir.de/StructureDefinition/Aufnahmegrund']
            assert len(reasons) == 1
            parts = reasons[0]['extension']
            assert len(parts) == 1 and parts[0]['url'] == 'VierteStelle'
            assert parts[0]['valueCoding']['system'] == 'http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle'
            assert parts[0]['valueCoding']['code'] == '7'
        else:
            assert found['class']['code']==r['class']['code']
        for date in ['start','end']:
            if r.get('period',{}).get(date):
                assert datetime.fromisoformat(found['period'][date].replace('Z','+00:00'))==datetime.fromisoformat(r['period'][date].replace('Z','+00:00')), {
                    'encounter': r['id'], 'field': date, 'source': r['period'][date], 'target': found['period'][date]}
    movement_check = check_movements(source, target, report)
    source_encounters = sum(r['resourceType'] == 'Encounter' for r in src)
    assert Counter(r['resourceType']for r in dst if r['resourceType'] in ('Patient','Encounter','Condition'))==Counter(Patient=1,Encounter=source_encounters+movement_check['contacts'],Condition=sum(original.values()))
    clinical = check_clinical(source, target, report)
    return {'demographics':demographic_check,'movements':movement_check,'clinical':clinical,'conditions':sum(original.values()),'excludedConditions':len(excluded),'encounters':len(encounters),
            'sourceDiagnosisValuesAndReferences': ('preserved except reported verification status changes'
                if any('verificationStatusChange' in d for d in decisions) else 'preserved'),
            'verificationStatusChanges':sum('verificationStatusChange' in d for d in decisions),
            'additionalIcd10GmCodings':sum(d['target'] is not None for d in decisions),
            'mappingDecisions':dict(Counter(d['status'] for d in decisions)),
            'emergencyMappings':len(report.get('encounterMappings',[])),
            'terminologyValidation':'not performed'}

def check_configured(source, target, report, options, patient_ids):
    """Check each requested patient copy without assuming the default ID scheme."""
    actual = [e['resource']['id'] for e in target['entry'] if e['resource']['resourceType'] == 'Patient']
    assert Counter(actual) == Counter(patient_ids), 'Configured patient copies differ'
    shared = {'Medication', 'Location'}
    groups = {pid: [] for pid in patient_ids}
    for entry in target['entry']:
        resource = entry['resource']
        if resource['resourceType'] in shared:
            for group in groups.values(): group.append(entry)
            continue
        if resource['resourceType'] == 'Patient':
            pid = resource['id']
        else:
            reference = resource.get('subject', resource.get('patient', {})).get('reference', '')
            assert reference.startswith('Patient/'), 'Resource has no patient attribution: ' + resource['resourceType']
            pid = reference.removeprefix('Patient/')
        assert pid in groups, 'Unexpected patient attribution: ' + pid
        groups[pid].append(entry)
    copies = [check(source, {'entry': groups[pid]}, dict(report, outputPatient=pid, converterOptions=options))
              for pid in patient_ids]
    if len(copies) == 1:
        result = copies[0]
    else:
        result = {'copies': copies, 'conditions': sum(c['conditions'] for c in copies),
                  'encounters': sum(c['encounters'] for c in copies)}
    result['outputPatients'] = patient_ids
    if options.get('REFERENCE_CONDITION_ENCOUNTER') == 'none' or ('REFERENCE_CONDITION_ENCOUNTER' not in options and options.get('SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER') == 'false'):
        result['sourceDiagnosisValuesAndReferences'] = 'values checked; encounter references omitted by explicit converter option'
    return result


if __name__=='__main__':
    if len(sys.argv)!=4:raise SystemExit(__doc__)
    print(json.dumps(check(*(json.loads(Path(f).read_text())for f in sys.argv[1:])),indent=2))
