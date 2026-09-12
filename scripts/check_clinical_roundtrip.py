"""Verify the supported clinical projection independently of the saved report."""
from collections import Counter
from decimal import Decimal
from clinical_import import prepare_clinical, SYSTEMS
from clinical_events import prepare_events, prepare_documents


def check_clinical(source, target, report):
    entries = source['entry']; pid = report['sourcePatient']
    rows, expected = prepare_clinical(entries, pid, report['encounterNumbers'])
    events, event_report = prepare_events(entries, pid, report['encounterNumbers'],
        {r['sourceId'] for r in expected['clinicalImports'] if r['resourceType']=='Observation'})
    expected['clinicalImports'].extend(event_report['clinicalImports'])
    document_rows, document_report = prepare_documents(entries, pid, report['encounterNumbers'])
    expected['clinicalImports'].extend(document_report['clinicalImports'])
    assert report.get('documentIdentityChanges') == document_report['documentIdentityChanges'], 'Document identity report changed'
    assert report.get('clinicalMapping') == expected['clinicalMapping'], 'Clinical mapping version changed'
    assert report.get('clinicalImports', []) == expected['clinicalImports'], 'Clinical import report changed'
    assert report.get('clinicalMappings', []) == expected['clinicalMappings'], 'Clinical mapping report changed'
    src = {r['resource']['id']:r['resource'] for r in entries if 'id' in r.get('resource',{})}
    dst = [e['resource'] for e in target['entry']]
    wanted = Counter(r['resourceType'] for r in expected['clinicalImports'])
    excluded = {'Patient','Encounter','Condition','Medication'}
    if 'movements' in report: excluded.add('Location')
    actual = Counter(r['resourceType'] for r in dst if r['resourceType'] not in excluded)
    assert wanted == actual, {'expectedClinicalCounts':wanted,'actualClinicalCounts':actual}
    def code(cc):
        c=(cc.get('coding')or[{}])[0]
        return c.get('system'), c.get('code')
    def value(r):
        if 'valueQuantity'in r:
            q=r['valueQuantity'];return ('number',str(Decimal(str(q['value'])).normalize()),q.get('code'))
        if 'valueCodeableConcept'in r:return ('code',code(r['valueCodeableConcept']))
        if 'valueString'in r:return ('text',r['valueString'])
        if 'valueBoolean'in r:return ('boolean',r['valueBoolean'])
        return ('absent',code(r.get('dataAbsentReason',{})))
    def obs(r):
        category=next((c['code'] for cc in r.get('category',[]) for c in cc.get('coding',[])
                       if c.get('system')=='http://terminology.hl7.org/CodeSystem/observation-category'),None)
        return (code(r['code']),r.get('status'),category,r.get('effectiveDateTime'),r.get('issued'),
                value(r),tuple((code(c['code']),value(c))for c in r.get('component',[])))
    wanted_obs=Counter(obs(src[i['sourceId']])for i in expected['clinicalImports'] if i['resourceType']=='Observation')
    actual_obs=Counter(obs(r)for r in dst if r['resourceType']=='Observation')
    assert wanted_obs==actual_obs, {'missingObservations':list((wanted_obs-actual_obs).items())[:2],
                                  'unexpectedObservations':list((actual_obs-wanted_obs).items())[:2]}
    def procedure(r):return (code(r['code']),r['status'],r.get('performedDateTime'),
                            r.get('performedPeriod',{}).get('start'),r.get('performedPeriod',{}).get('end'))
    assert Counter(procedure(src[i['sourceId']])for i in expected['clinicalImports']if i['resourceType']=='Procedure')==Counter(procedure(r)for r in dst if r['resourceType']=='Procedure'), 'Procedure values changed'
    medications={r['id']:r for r in dst if r['resourceType']=='Medication'}
    expected_codes=Counter((SYSTEMS.get(r['code']['coding'][0]['system']),r['code']['coding'][0]['code'])for r in medications.values())
    products={(row[17],row[16])for row in rows['Medikation']}
    assert expected_codes==Counter(products), 'Medication definitions lost or merged'
    for r in dst:
        if r['resourceType'] in ('MedicationRequest','MedicationAdministration','MedicationStatement'):
            assert r.get('medicationReference',{}).get('reference','').removeprefix('Medication/') in medications
    import base64
    assert Counter(row[4]for row in document_rows)==Counter(
        base64.b64decode(r['content'][0]['attachment']['data']).decode('utf-8')for r in dst if r['resourceType']=='DocumentReference'), 'Document text changed'
    return {'counts':dict(wanted),'medicationDefinitions':len(medications),'clinicalProjection':'passed'}
