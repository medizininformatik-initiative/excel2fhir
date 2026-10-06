"""Verify the supported clinical projection independently of the saved report."""
from collections import Counter
from decimal import Decimal
from clinical_import import prepare_clinical, SYSTEMS
from medication_products import PZN
from clinical_events import prepare_events, prepare_documents
from german_texts import GermanTexts, localize_rows
from german_demographics import identity
from check_output_selection import selected, enabled
from check_medication_selection import product_id


def check_clinical(source, target, report):
    from terminology_year import emitted, signature
    terminology = report.get('terminology', {})
    year, mode = terminology.get('mappingYear', '2026'), terminology.get('versionOutput', 'Jahr')
    entries = source['entry']; pid = report['sourcePatient']
    rows, expected = prepare_clinical(entries, pid, report['encounterNumbers'], year)
    events, event_report = prepare_events(entries, pid, report['encounterNumbers'],
        {r['sourceId'] for r in expected['clinicalImports'] if r['resourceType']=='Observation'}, year)
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
    assert {l['id'] for l in report['losses'] if l['resourceType'] == 'Procedure' and l['path'] == '$'} == {
        l['id'] for l in expected['losses'] if l['resourceType'] == 'Procedure' and l['path'] == '$'}, 'Procedure exclusion report changed'
    src = {r['resource']['id']:r['resource'] for r in entries if 'id' in r.get('resource',{})}
    dst = [e['resource'] for e in target['entry']]
    options = report.get('converterOptions', {})
    imports = [r for r in expected['clinicalImports'] if selected(src[r['sourceId']], options)]
    wanted = Counter(r['resourceType'] for r in imports)
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
    from observation_mapping import decision as observation_decision, metadata as observation_metadata
    expected_observation_mappings = [m for r in src.values() if r['resourceType'] == 'Observation'
                                     for m in [observation_decision(r)] if m]
    assert report.get('observationMapping') == observation_metadata()
    assert report.get('observationMappings') == expected_observation_mappings
    def obs(r, source=False):
        category=next((c['code'] for cc in r.get('category',[]) for c in cc.get('coding',[])
                       if c.get('system')=='http://terminology.hl7.org/CodeSystem/observation-category'),None)
        mapped = observation_decision(r) if source else None
        codes = mapped['targetCodings'] if mapped else r['code'].get('coding', [])[:2]
        return (tuple((c.get('system'), c.get('code'), c.get('version')) for c in codes),r.get('status'),category,r.get('effectiveDateTime'),r.get('issued'),
                value(r, source),tuple((code(c['code']),value(c, source))for c in r.get('component',[])))
    wanted_obs=Counter(obs(src[i['sourceId']], True)for i in imports if i['resourceType']=='Observation')
    from check_observation_units import checked_observation_units
    checked_dst = checked_observation_units(dst, options)
    actual_obs=Counter(obs(r)for r in checked_dst if r['resourceType']=='Observation')
    assert wanted_obs==actual_obs, {'missingObservations':list((wanted_obs-actual_obs).items())[:2],
                                  'unexpectedObservations':list((actual_obs-wanted_obs).items())[:2]}
    def procedure(r):
        return (tuple(signature(c) for c in r['code'].get('coding', [])),
                r['code'].get('text', ''), r['status'],
                r.get('performedDateTime', r.get('performedPeriod', {}).get('start', '')),
                r.get('performedPeriod', {}).get('end', ''))
    wanted_procedures = Counter()
    for mapping in expected['clinicalMappings'] if enabled(options, 'PROCEDURE_ENABLED') else []:
        for projected in mapping.get('outputs', []):
            codes = projected['codings']
            label = projected['label']
            if not codes[0]['system'].endswith('/ops'):
                original = src[mapping['sourceId']]['code']['coding'][0]
                label = translations.text(label, 'Prozedur', SYSTEMS[original['system']], original['code'])
            wanted_procedures[(tuple(signature(emitted(c, mode)) for c in codes),
                               label, projected['status'], projected['start'], projected['end'])] += 1
    assert wanted_procedures == Counter(procedure(r) for r in dst if r['resourceType'] == 'Procedure'), 'Procedure codes, descriptions, count or event changed'
    assert report.get('vaccineMapping') == event_report['vaccineMapping'], 'Vaccine mapping changed'
    assert report.get('vaccineMappings') == event_report['vaccineMappings'], 'Vaccine decisions changed'
    vaccine_rows = events['Impfung'] if enabled(options, 'IMMUNIZATION_ENABLED') else []
    def vaccine_row(row):
        systems = {'ATC ' + year: ('http://fhir.de/CodeSystem/bfarm/atc', year)}
        system, version = systems.get(row[5], (next((k for k,v in SYSTEMS.items() if v == row[5]), None), None))
        return (row[3], (signature(emitted({'system': system, 'code': row[4], 'version': version}, mode)),) if row[4] else (), row[6], row[7], row[8])
    assert Counter(vaccine_row(row) for row in vaccine_rows) == Counter((
        r['vaccineCode'].get('text', ''), tuple(signature(c)
        for c in r['vaccineCode'].get('coding', [])), r.get('occurrenceDateTime', ''),
        r.get('status', ''), str(r['primarySource']).lower() if 'primarySource' in r else '')
        for r in dst if r['resourceType'] == 'Immunization'), 'Vaccine coding, description or event changed'
    medications={r['id']:r for r in dst if r['resourceType']=='Medication'}
    product_systems = {**SYSTEMS, PZN: 'PZN'}
    localize_rows({'Medikation': rows['Medikation']})
    def product_row(row):
        codings = []
        if row[4]: codings.append(signature({'system': next(k for k,v in product_systems.items() if v == row[5]), 'code': row[4]}))
        if row[6]: codings.append(signature(emitted({'system': 'http://fhir.de/CodeSystem/bfarm/atc', 'code': row[6], 'version': row[7]}, mode)))
        return row[3], tuple(codings), row[8]
    expected_products = {product_id(row): product_row(row) for row in rows['Medikation']} if selected({'resourceType': 'Medication'}, options) else {}
    actual_products = {r['id']: (r['code'].get('text', ''),
        tuple(signature(c) for c in r['code'].get('coding', [])),
        r.get('form', {}).get('text', '')) for r in medications.values()}
    assert actual_products == expected_products, 'Medication definitions, ATC versions or forms lost or merged'
    from check_medication_selection import check_medication_references
    check_medication_references(rows['Medikation'], dst, options, report.get('outputPatient', pid.replace('_', '-')))
    import base64
    assert Counter(row[4]for row in document_rows if enabled(options, 'DOCUMENT_REFERENCE_ENABLED'))==Counter(
        base64.b64decode(r['content'][0]['attachment']['data']).decode('utf-8')for r in dst if r['resourceType']=='DocumentReference'), 'Document text changed'
    from check_clinical_references import check_clinical_references
    check_clinical_references(source, dst, report, rows['Medikation'], document_rows)
    return {'counts':dict(wanted),'medicationDefinitions':len(medications),'clinicalProjection':'passed'}
