"""Expected medication events derived from input rows, before output selections."""
from copy import deepcopy
from decimal import Decimal
from functools import lru_cache
from pathlib import Path
import re
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


@lru_cache(maxsize=1)
def ucum_tables():
    root = Path(__file__).resolve().parents[1] / 'src/main/resources/ucum'
    tables = []
    for name in ('UCUM_Codes.map', 'UCUM_Synonyms_automatic.map', 'UCUM_Synonyms_manual.map'):
        values = {}
        for line in (root / name).read_text().splitlines():
            if not line.strip() or line.lstrip().startswith('#'): continue
            parts = line.split(None, 1)
            if len(parts) == 2:
                values[re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m[1], 16)), parts[0])] = parts[1].strip()
        tables.append(values)
    return tables[0], {**tables[1], **tables[2]}


def quantity(value, unit):
    labels, synonyms = ucum_tables()
    code = unit if unit in labels else synonyms.get(unit, unit)
    result = {'system': 'http://unitsofmeasure.org'}
    if value: primitive(result, 'value', value if str(value).startswith('!dar:') else float(Decimal(str(value))))
    primitive(result, 'code', code or '!dar:unknown')
    primitive(result, 'unit', labels.get(code) or '!dar:unknown')
    return result


def dose_text(row):
    parts = [row[19]] if row[19] else []
    if row[16]: parts.append('Einzeldosis: ' + row[16] + (' ' + row[17] if row[17] else ' (Einheit unbekannt)'))
    if row[18]: parts.append('Dosen pro Tag: ' + row[18])
    return '; '.join(parts)


def baseline_medications(rows, options, pid):
    result, counts = [], {}
    for row in rows:
        kind, suffix, key = TYPES[row[2]]
        number = counts.get(kind, int(options.get('START_ID_' + key, 1))); counts[kind] = number + 1
        identifier = pid + ('-E-' + row[1] if row[1] else '') + '-' + suffix + '-' + str(number)
        resource = {'resourceType': kind, 'id': identifier, 'medicationReference': {'reference': 'Medication/' + product_id(row)}}
        resource['status'] = row[11] or ('completed' if suffix == 'MA' else 'active')
        if suffix == 'MR':
            resource['intent'] = row[12] or 'order'
            primitive(resource, 'authoredOn', row[13])
        else:
            if row[15]:
                resource['effectivePeriod'] = {}
                primitive(resource['effectivePeriod'], 'start', row[14]); primitive(resource['effectivePeriod'], 'end', row[15])
            else: primitive(resource, 'effectiveDateTime', row[14])
            if suffix == 'MS': primitive(resource, 'dateAsserted', row[13])
        dose = {}
        if suffix == 'MA':
            if row[16] or row[17]: dose['dose'] = quantity(row[16], row[17])
            if row[19] or row[18] or (row[16] and not row[17]): dose['text'] = dose_text(row)
            if dose: resource['dosage'] = dose
        else:
            frequency = Decimal(row[18]) if row[18] else None
            integral = frequency is not None and frequency == int(frequency) and -2147483648 <= frequency <= 2147483647
            if not row[19] and row[16] and row[17] and integral:
                dose = {'doseAndRate': [{'doseQuantity': quantity(row[16], row[17])}],
                        'timing': {'repeat': {'frequency': int(frequency), 'period': 1, 'periodUnit': 'd'}}}
            elif dose_text(row): dose = {'text': dose_text(row)}
            if not row[16] and row[17]: dose['doseAndRate'] = [{'doseQuantity': quantity(row[16], row[17])}]
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
