"""Verify the supported clinical projection independently of the saved report."""
from collections import Counter
from decimal import Decimal
from clinical_import import prepare_clinical, SYSTEMS
from medication_products import PZN
from clinical_events import prepare_events, prepare_documents
from german_texts import GermanTexts, localize_rows
from german_demographics import identity


def check_clinical(source, target, report):
    entries = source['entry']; pid = report['sourcePatient']
    rows, expected = prepare_clinical(entries, pid, report['encounterNumbers'])
    events, event_report = prepare_events(entries, pid, report['encounterNumbers'],
        {r['sourceId'] for r in expected['clinicalImports'] if r['resourceType']=='Observation'})
    expected['clinicalImports'].extend(event_report['clinicalImports'])
    document_rows, document_report = prepare_documents(entries, pid, report['encounterNumbers'])
    localize_rows({'DocumentReference':document_rows})
    expected['clinicalImports'].extend(document_report['clinicalImports'])
    assert report.get('documentIdentityChanges') == document_report['documentIdentityChanges'], 'Document identity report changed'
    assert report.get('productCatalog') == expected['productCatalog'], 'Product catalogue changed; use the generation catalogue'
    assert report.get('productDataUsage') == expected['productDataUsage'], 'Product data provenance changed'
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
    translations = GermanTexts(identity(next(r for r in src.values() if r['resourceType']=='Patient'))['address'])
    def value(r, source=False):
        if 'valueQuantity'in r:
            q=r['valueQuantity'];return ('number',str(Decimal(str(q['value'])).normalize()),q.get('code'))
        if 'valueCodeableConcept'in r:
            cc = r['valueCodeableConcept']
            if code(cc) == (None, None) and 'text' in cc:
                coding = cc['coding'][0]
                for primitive in ('_system', '_code'):
                    assert coding[primitive]['extension'] == [{'url':
                        'http://hl7.org/fhir/StructureDefinition/data-absent-reason', 'valueCode':'unknown'}]
                return ('text', cc['text'])
            return ('code',code(cc))
        if 'valueString'in r:return ('text',translations.observation_text(r['valueString'], *code(r['code'])) if source else r['valueString'])
        if 'valueBoolean'in r:return ('boolean',r['valueBoolean'])
        return ('absent',code(r.get('dataAbsentReason',{})))
    def obs(r, source=False):
        category=next((c['code'] for cc in r.get('category',[]) for c in cc.get('coding',[])
                       if c.get('system')=='http://terminology.hl7.org/CodeSystem/observation-category'),None)
        return (code(r['code']),r.get('status'),category,r.get('effectiveDateTime'),r.get('issued'),
                value(r, source),tuple((code(c['code']),value(c, source))for c in r.get('component',[])))
    wanted_obs=Counter(obs(src[i['sourceId']], True)for i in expected['clinicalImports'] if i['resourceType']=='Observation')
    actual_obs=Counter(obs(r)for r in dst if r['resourceType']=='Observation')
    assert wanted_obs==actual_obs, {'missingObservations':list((wanted_obs-actual_obs).items())[:2],
                                  'unexpectedObservations':list((actual_obs-wanted_obs).items())[:2]}
    def procedure(r, original=False):
        codings = list(r['code'].get('coding', []))
        if original:
            from procedure_mapping import select_ops
            mapped = select_ops(codings[0])['target']
            if mapped: codings.insert(0, mapped)
        return (tuple((c['system'], c['code'], c.get('version')) for c in codings), r['status'],
                r.get('performedDateTime'), r.get('performedPeriod', {}).get('start'), r.get('performedPeriod', {}).get('end'))
    assert Counter(procedure(src[i['sourceId']], True) for i in expected['clinicalImports'] if i['resourceType']=='Procedure') == Counter(procedure(r) for r in dst if r['resourceType']=='Procedure'), 'Procedure codes, order or event changed'
    assert report.get('vaccineMapping') == event_report['vaccineMapping'], 'Vaccine mapping changed'
    assert report.get('vaccineMappings') == event_report['vaccineMappings'], 'Vaccine decisions changed'
    vaccine_rows = events['Impfung']
    def vaccine_row(row):
        systems = {'ATC 2026': ('http://fhir.de/CodeSystem/bfarm/atc', '2026')}
        system, version = systems.get(row[5], (next((k for k,v in SYSTEMS.items() if v == row[5]), None), None))
        return (row[3], ((system, row[4], version),) if row[4] else (), row[6], row[7], row[8])
    assert Counter(vaccine_row(row) for row in vaccine_rows) == Counter((
        r['vaccineCode'].get('text', ''), tuple((c.get('system'), c.get('code'), c.get('version'))
        for c in r['vaccineCode'].get('coding', [])), r.get('occurrenceDateTime', ''),
        r.get('status', ''), str(r['primarySource']).lower() if 'primarySource' in r else '')
        for r in dst if r['resourceType'] == 'Immunization'), 'Vaccine coding, description or event changed'
    medications={r['id']:r for r in dst if r['resourceType']=='Medication'}
    product_systems = {**SYSTEMS, PZN: 'PZN'}
    localize_rows({'Medikation': rows['Medikation']})
    def product_row(row):
        codings = []
        if row[4]: codings.append((next(k for k,v in product_systems.items() if v == row[5]), row[4], None))
        if row[6]: codings.append(('http://fhir.de/CodeSystem/bfarm/atc', row[6], row[7]))
        return row[3], tuple(codings), row[8]
    expected_products = Counter(set(product_row(row) for row in rows['Medikation']))
    actual_products = Counter((r['code'].get('text', ''),
        tuple((c.get('system'),c.get('code'),c.get('version')) for c in r['code'].get('coding', [])),
        r.get('form', {}).get('text', '')) for r in medications.values())
    assert actual_products == expected_products, 'Medication definitions, ATC versions or forms lost or merged'
    for r in dst:
        if r['resourceType'] in ('MedicationRequest','MedicationAdministration','MedicationStatement'):
            assert r.get('medicationReference',{}).get('reference','').removeprefix('Medication/') in medications
    import base64
    assert Counter(row[4]for row in document_rows)==Counter(
        base64.b64decode(r['content'][0]['attachment']['data']).decode('utf-8')for r in dst if r['resourceType']=='DocumentReference'), 'Document text changed'
    return {'counts':dict(wanted),'medicationDefinitions':len(medications),'clinicalProjection':'passed'}
