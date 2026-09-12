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


def check(source, target, report):
    src = [e['resource'] for e in source['entry']]
    dst = [e['resource'] for e in target['entry']]
    source_ids = {e.get('fullUrl'):e['resource'].get('id')for e in source['entry']}
    source_ids.update({r['resourceType']+'/'+r['id']:r['id']for r in src})
    target_ids = {r['resourceType']+'/'+r['id'] for r in dst}
    target_ids.update(e.get('fullUrl')for e in target['entry']if e.get('fullUrl'))
    pid = report['sourcePatient'].replace('_','-')
    demographic_check = check_patient(next(r for r in src if r['resourceType']=='Patient'),
        next(r for r in dst if r['resourceType']=='Patient'), report)
    assert report['diagnosisMapping'] == mapping_metadata(), 'Use the mapping version that produced this workbook'
    decisions = [map_diagnosis(r) for r in src if r['resourceType'] == 'Condition']
    assert report['diagnosisMappings'] == decisions, 'Mapping report differs from the versioned decisions'
    def signature(r, original):
        expected_codings = list(r['code']['coding'])
        if original:
            decision = map_diagnosis(r)
            if decision['target'] is not None:
                expected_codings.append(decision['target'])
        codings = tuple(sorted((c['system'],c.get('version',''),c['code'])for c in expected_codings))
        encounter = r.get('encounter',{}).get('reference','')
        if original and encounter:
            encounter = 'Encounter/'+pid+'-E-'+report['encounterNumbers'][source_ids[encounter]]
        statuses = tuple(tuple(c['code']for c in r.get(field,{}).get('coding',[]))for field in ['clinicalStatus','verificationStatus'])
        if original and 'verificationStatusChange' in decision:
            statuses = (statuses[0], (decision['verificationStatusChange']['to'],))
        return (codings, r.get('recordedDate',''), r.get('onsetDateTime',''),r.get('abatementDateTime',''),encounter,statuses)
    original=Counter(signature(r,True)for r in src if r['resourceType']=='Condition')
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
                assert datetime.fromisoformat(found['period'][date].replace('Z','+00:00'))==datetime.fromisoformat(r['period'][date].replace('Z','+00:00'))
    source_encounters = sum(r['resourceType'] == 'Encounter' for r in src)
    assert Counter(r['resourceType']for r in dst if r['resourceType'] in ('Patient','Encounter','Condition'))==Counter(Patient=1,Encounter=source_encounters+len(report.get('movements',{}).get('contacts',[])),Condition=sum(original.values()))
    clinical = check_clinical(source, target, report)
    return {'demographics':demographic_check,'movements':check_movements(source,target,report),'clinical':clinical,'conditions':sum(original.values()),'encounters':len(encounters),
            'sourceDiagnosisValuesAndReferences': ('preserved except reported verification status changes'
                if any('verificationStatusChange' in d for d in decisions) else 'preserved'),
            'verificationStatusChanges':sum('verificationStatusChange' in d for d in decisions),
            'additionalIcd10GmCodings':sum(d['target'] is not None for d in decisions),
            'mappingDecisions':dict(Counter(d['status'] for d in decisions)),
            'emergencyMappings':len(report.get('encounterMappings',[])),
            'terminologyValidation':'not performed'}

if __name__=='__main__':
    if len(sys.argv)!=4:raise SystemExit(__doc__)
    print(json.dumps(check(*(json.loads(Path(f).read_text())for f in sys.argv[1:])),indent=2))
