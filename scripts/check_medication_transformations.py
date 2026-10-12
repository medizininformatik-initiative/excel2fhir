"""Expected medication events derived from input rows, before output selections."""
from copy import deepcopy
from decimal import Decimal
from check_output_selection import named_id, selected, resource_key
from check_medication_selection import product_id

DAR = 'http://hl7.org/fhir/StructureDefinition/data-absent-reason'
TYPES = {'Verordnung': ('MedicationRequest', 'MR', 'MEDICATION_REQUEST'),
         'Verabreichung': ('MedicationAdministration', 'MA', 'MEDICATION_ADMINISTRATION'),
         'Medikationsaussage': ('MedicationStatement', 'MS', 'MEDICATION_STATEMENT')}


def absent(code='unknown'):
    return {'extension': [{'url': DAR, 'valueCode': code}]}


def primitive(parent, key, value):
    if value and str(value).startswith('!dar:'):
        parent['_' + key] = absent(value[5:])
    elif value not in ('', None):
        parent[key] = value


def quantity(value, unit, system=None):
    result = {}
    if system: result['system'] = system
    if value: primitive(result, 'value', value if str(value).startswith('!dar:') else float(Decimal(str(value))))
    if unit: result.update(code=unit, unit=unit)
    return result


def dose_text(row):
    parts = [row['Dosierungstext']] if row['Dosierungstext'] else []
    from workbook_absent import display
    dose = display('Medikation', 'Einzeldosis', row['Einzeldosis'], {})
    if dose: parts.append('Einzeldosis: ' + dose + (' ' + row['Dosiereinheit'] if row['Dosiereinheit'] else ' (Einheit unbekannt)'))
    if row['Dosen pro Tag']: parts.append('Dosen pro Tag: ' + row['Dosen pro Tag'])
    return '; '.join(parts)


def baseline_medications(rows, options, pid):
    result, counts = [], {}
    for row in rows:
        kind, suffix, key = TYPES[row['Medikationstyp']]
        number = counts.get(kind, int(options.get('START_ID_' + key, 1))); counts[kind] = number + 1
        identifier = pid + ('-E-' + row['Fall-Nr'] if row['Fall-Nr'] else '') + '-' + suffix + '-' + str(number)
        resource = {'resourceType': kind, 'id': identifier, 'medicationReference': {'reference': 'Medication/' + product_id(row)}}
        resource['status'] = row['Status'] or ('completed' if suffix == 'MA' else 'active')
        if suffix == 'MR':
            resource['intent'] = row['Absicht'] or 'order'
            primitive(resource, 'authoredOn', row['Dokumentationszeitpunkt'])
        else:
            if row['Ende']:
                resource['effectivePeriod'] = {}
                primitive(resource['effectivePeriod'], 'start', row['Beginn']); primitive(resource['effectivePeriod'], 'end', row['Ende'])
            else: primitive(resource, 'effectiveDateTime', row['Beginn'])
            if suffix == 'MS': primitive(resource, 'dateAsserted', row['Dokumentationszeitpunkt'])
        dose = {}
        if suffix == 'MA':
            if any([row['Einzeldosis'], row['Dosiereinheit'], row['Dosiereinheitcodesystem']]): dose['dose'] = quantity(row['Einzeldosis'], row['Dosiereinheit'], row['Dosiereinheitcodesystem'])
            if row['Dosierungstext'] or row['Dosen pro Tag'] or (row['Einzeldosis'] and not row['Dosiereinheit']): dose['text'] = dose_text(row)
            if dose: resource['dosage'] = dose
        else:
            frequency = Decimal(row['Dosen pro Tag']) if row['Dosen pro Tag'] else None
            integral = frequency is not None and frequency == int(frequency) and -2147483648 <= frequency <= 2147483647
            if any([row['Einzeldosis'], row['Dosiereinheit'], row['Dosiereinheitcodesystem']]): dose['doseAndRate'] = [{'doseQuantity': quantity(row['Einzeldosis'], row['Dosiereinheit'], row['Dosiereinheitcodesystem'])}]
            if integral:
                dose['timing'] = {'repeat': {'frequency': int(frequency), 'period': 1, 'periodUnit': 'd'}}
            if row['Dosierungstext'] or (row['Dosen pro Tag'] and not integral) or (row['Einzeldosis'] and not row['Dosiereinheit']): dose['text'] = dose_text(row)
            if dose: resource['dosageInstruction' if suffix == 'MR' else 'dosage'] = [dose]
        result.append(resource)
    return result


def transformed_medications(rows, options, pid):
    def derive(resource, action, request):
        kind = 'MedicationAdministration' if action.endswith('administration') else 'MedicationStatement'
        key = resource['resourceType'] + '/' + resource['id'] + '/' + kind + ('/request' if request else '/event')
        result = {'resourceType': kind, 'id': named_id('derived', key), 'medicationReference': deepcopy(resource['medicationReference'])}
        if request:
            if kind == 'MedicationStatement' and 'dosageInstruction' in resource:
                result['dosage'] = deepcopy(resource['dosageInstruction'])
        else:
            for field in ('effectiveDateTime', '_effectiveDateTime', 'effectivePeriod'):
                if field in resource: result[field] = deepcopy(resource[field])
            if resource.get('status') in ('completed', 'entered-in-error', 'stopped', 'on-hold', 'unknown'):
                result['status'] = resource['status']
            if kind == 'MedicationStatement' and resource.get('dosage'):
                old = resource['dosage']; dose = {}
                if 'text' in old: dose['text'] = old['text']
                if 'dose' in old: dose['doseAndRate'] = [{'doseQuantity': deepcopy(old['dose'])}]
                result['dosage'] = [dose]
        return result
    first = []
    for resource in baseline_medications(rows, options, pid):
        action = options.get('MEDICATION_REQUEST_TREATMENT', 'retain') if resource['resourceType'] == 'MedicationRequest' else 'retain'
        first.append(derive(resource, action, True) if action.startswith('replace-') else resource)
    result = []
    for resource in first:
        action = options.get(resource_key(resource) + '_TREATMENT', 'retain') if resource['resourceType'] != 'MedicationRequest' else 'retain'
        if not action.startswith('replace-'): result.append(resource)
        if action.startswith(('add-', 'replace-')): result.append(derive(resource, action, False))
    return [r for r in result if selected(r, options)]
