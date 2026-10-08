"""Source-derived field values for checking configured output transformations.

The projection is built from the input mapping contract, never from the output
being checked. It contains the fields eligible for DAR, not a full FHIR resource.
"""
from copy import deepcopy
import base64
import re
from check_output_selection import event_id, named_id, enabled, resource_key
from check_medication_selection import product_id
from check_medication_transformations import primitive, transformed_medications
from check_time_shift import temporal_expectations, shift_days, shift_date
from check_contact_selection import contact_expectations
from check_encounter_policies import expected_end
from clinical_import import SYSTEMS
from medication_products import INGREDIENT_SYSTEMS


def expected_fields(source, report):
    from synthea_to_excel import prepare
    terminology = report.get('terminology', {})
    rows, evidence = prepare(source, {'SYNTHEA_MAPPING_YEAR': terminology.get('mappingYear', '2026'),
                                     'SYNTHEA_VERSION_OUTPUT': terminology.get('versionOutput', 'Jahr')})
    options = report.get('converterOptions', {})
    pid = report.get('outputPatient', report['sourcePatient'].replace('_', '-'))
    resources = [e['resource'] for e in source['entry']]
    result = {}
    def add(kind, identifier, **fields):
        r = dict(resourceType=kind, id=identifier, **fields); result[identifier] = r; return r
    def row_id(row, suffix, key, number):
        return pid + ('-E-' + row[1] if row[1] else '') + '-' + suffix + '-' + str(int(options.get('START_ID_' + key, 1)) + number)
    systems = {value: key for key, value in {**SYSTEMS, **INGREDIENT_SYSTEMS}.items()}
    systems.update({'ICD-10-GM': 'http://fhir.de/CodeSystem/bfarm/icd-10-gm', 'OPS': 'http://fhir.de/CodeSystem/bfarm/ops',
                    'ATC': 'http://fhir.de/CodeSystem/bfarm/atc', 'PZN': 'http://fhir.de/CodeSystem/ifa/pzn'})
    def coding(code, system, version=''):
        c = {'system': systems[system]}
        primitive(c, 'code', code); primitive(c, 'version', version)
        return c
    person = next(r for r in resources if r['resourceType'] == 'Patient')
    from german_demographics import identity
    identity_fields = identity(person)
    address = deepcopy(identity_fields['address'])
    # ISO state codes are the converter's address output contract.
    states = dict(zip(('Baden-Württemberg','Bayern','Berlin','Brandenburg','Bremen','Hamburg','Hessen','Mecklenburg-Vorpommern',
                      'Niedersachsen','Nordrhein-Westfalen','Rheinland-Pfalz','Saarland','Sachsen','Sachsen-Anhalt','Schleswig-Holstein','Thüringen'),
                     ('DE-BW','DE-BY','DE-BE','DE-BB','DE-HB','DE-HH','DE-HE','DE-MV','DE-NI','DE-NW','DE-RP','DE-SL','DE-SN','DE-ST','DE-SH','DE-TH')))
    address['state'] = states.get(address['state'], address['state'])
    add('Patient', pid, name=[identity_fields['name']], address=[address], gender=person['gender'])
    conditions = [r for r in resources if r['resourceType'] == 'Condition' and r['id'] in evidence['conditionRows']]
    for number, (row, original) in enumerate(zip(rows['Diagnose'], conditions)):
        r = add('Condition', row_id(row, 'C', 'CONDITION', number), code={'coding': [coding(row[3], row[4], row[13])]})
        if row[5]: r['code']['coding'].append(coding(row[5], row[6], row[14]))
        for field in ('clinicalStatus', 'verificationStatus'):
            if field in original: r[field] = deepcopy(original[field])
        decision = next(d for d in evidence['diagnosisMappings'] if d['sourceId'] == original['id'])
        if 'verificationStatusChange' in decision:
            r['verificationStatus'] = {'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/condition-ver-status',
                                                 'code': decision['verificationStatusChange']['to']}]}
    for number, row in enumerate(rows['Prozedur']):
        codes = [coding(row[3], row[5], row[11])]
        if row[6]: codes.append(coding(row[6], row[7], row[12]))
        add('Procedure', row_id(row, 'P', 'PROCEDURE', number), code={'coding': codes})
    for sheet in ('Laborbefund', 'Klinische Dokumentation'):
        for row in rows[sheet]:
            code, system, extra, extra_system = row[2:6] if sheet == 'Laborbefund' else row[3:7]
            codes = [coding(code, system)]
            if extra: codes.append(coding(extra, extra_system))
            value = {'code': {'coding': codes}}
            original = next(r for r in resources if r['resourceType'] == 'Observation' and r['id'] == (row[16] or row[15]))
            if row[16]:
                parent = result[event_id(pid, 'Observation', row[16])]
                index = len(parent.setdefault('component', []))
                component = original['component'][index]
                parent['component'].append(value)
            else:
                value = add('Observation', event_id(pid, 'Observation', row[15]), **value, category=deepcopy(original.get('category', [])))
                component = original
            for key, v in component.items():
                if key.startswith('value') or key == 'dataAbsentReason': value[key] = deepcopy(v)
            if 'valueString' in value: value['valueString'] = row[7]
            if 'valueQuantity' in value and enabled(options, resource_key(original) + '_UCUM_CODE_IN_UNIT', False):
                quantity = value['valueQuantity']; quantity['unit'] = quantity.pop('code')
    for row in rows['Medikation']:
        codes = []
        if row[4]: codes.append(coding(row[4], row[5]))
        if row[6] or row[7]: codes.append(coding(row[6], 'ATC', row[7]))
        r = add('Medication', product_id(row), code={'coding': codes})
        if row[8]: r['form'] = {'text': row[8]}
        r['ingredient'] = [{'itemCodeableConcept': {'coding': [coding(code.strip(), row[10])]}} for code in row[9].split(';')]
    for medication in transformed_medications(rows['Medikation'], options, pid): result[medication['id']] = medication
    for row in rows['Impfung']:
        add('Immunization', event_id(pid, 'Immunization', row[2]), vaccineCode={'coding': [coding(row[4], row[5], row[9])] if row[4] else []})
    for row in rows['Befundbericht']:
        r = add('DiagnosticReport', event_id(pid, 'DiagnosticReport', row[2]), code={'coding': [coding(row[4], row[5])] if row[4] else []})
        if row[10]: r['conclusion'] = row[10]
    for row in rows['Behandlungsplan']:
        r = add('CarePlan', event_id(pid, 'CarePlan', row[2]))
        if row[10]: r['description'] = row[10]
    for number, row in enumerate(rows['DocumentReference']):
        add('DocumentReference', row_id(row, 'DR', 'DOCUMENT_REFERENCE', number),
            content=[{'attachment': {'data': base64.b64encode(row[4].encode()).decode()}}])
    days = shift_days(report)
    for identifier, dates in temporal_expectations(source, report).items():
        if identifier not in result: continue
        for path, date in dates.items():
            shifted = shift_date(date, days)
            if not shifted: continue
            parent = result[identifier]
            *parts, key = path.split('.')
            for part in parts: parent = parent.setdefault(part, {})
            parent[key] = shifted
    contacts = contact_expectations(source, report)
    diz = re.sub('[^A-Z]', '', pid.upper())
    for identifier, contact in contacts.items():
        start, end = shift_date(contact['start'], days), shift_date(contact['end'], days)
        scope = {'AMB': 'AMBULATORY', 'IMP': 'INPATIENT'}.get(contact['cls'])
        end = expected_end(start, end, options.get(f'ENCOUNTER_{scope}_END_POLICY', 'preserve'),
                           options.get(f'ENCOUNTER_{scope}_END_APPLICATION', 'always'), contact['missing'])
        period = {'start': start}
        if end: period['end'] = end
        r = add('Encounter', identifier, period=period)
        if contact['level'] == 'facility' and any(d['sourceClass'] == 'EMER' and pid + '-E-' + report['encounterNumbers'][d['sourceId']] == identifier for d in report['encounterMappings']):
            r['extension'] = [{'url': 'http://fhir.de/StructureDefinition/Aufnahmegrund', 'extension': [
                {'url': 'VierteStelle', 'valueCoding': {'system': 'http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle', 'code': '7'}}]}]
        row = contact.get('row')
        if row:
            path = diz + '|' + row[5]
            for kind, name in zip(('wa', 'ro', 'bd'), row[6:9]):
                if name:
                    path += '|' + kind + '|' + name
                    add('Location', named_id('Location', diz + '|Location|' + path), name=name)
    return result
