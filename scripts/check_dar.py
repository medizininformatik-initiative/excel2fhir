"""Verify DAR replacements, then restore source-derived values for the fact comparison."""
from copy import deepcopy
from functools import lru_cache
import json
from pathlib import Path
from check_medication_transformations import DAR, absent
from synthea_expected_fields import expected_fields


@lru_cache(maxsize=1)
def catalogue():
    root = Path(__file__).resolve().parents[1] / 'web/catalog'
    fields = json.loads((root / 'dar/generated/catalog.json').read_text())['fields']
    contract = json.loads((root / 'options/contract.json').read_text())
    return fields, contract['propertiesFormat']['darProperties']


def checked_original_dar(source, target, report):
    options = report.get('converterOptions', {})
    if not any(key.startswith('DAR_') and value != 'unchanged' for key, value in options.items()):
        return target
    fields, properties = catalogue()
    baseline = expected_fields(source, report)
    result = deepcopy(target)
    for entry in result['entry']:
        resource = entry['resource']
        assert resource['id'] in baseline, 'No independent DAR expectation: ' + resource['id']
        original = baseline[resource['id']]
        output_period = deepcopy(resource.get('period'))
        if resource['resourceType'] == 'Condition':
            clinical = {c.get('code') for c in resource.get('clinicalStatus', {}).get('coding', []) if c.get('system') == 'http://terminology.hl7.org/CodeSystem/condition-clinical'}
            abated = any(k.lstrip('_').startswith('abatement') and bool(v) for k, v in resource.items())
            assert not abated or clinical.intersection({'inactive', 'resolved', 'remission'}), 'Condition DAR violates con-4'
            entered = any(c.get('code') == 'entered-in-error' and c.get('system') == 'http://terminology.hl7.org/CodeSystem/condition-ver-status' for c in resource.get('verificationStatus', {}).get('coding', []))
            assert not entered or not resource.get('clinicalStatus'), 'Condition DAR violates con-5'
        for field in fields:
            if field['resourceType'] != resource['resourceType']: continue
            identifier = field['id']
            code = options.get(properties[identifier], 'unchanged')
            if resource['resourceType'] == 'Encounter':
                scope = {'AMB': 'ambulatory', 'IMP': 'inpatient'}.get(resource.get('class', {}).get('code'))
                scoped = identifier.replace('Encounter.', 'Encounter.' + str(scope) + '.')
                code = options.get(properties.get(scoped), code)
            if resource['resourceType'] == 'Observation':
                vital = any(c.get('code') == 'vital-signs' and c.get('system') == 'http://terminology.hl7.org/CodeSystem/observation-category'
                            for cc in original.get('category', []) for c in cc.get('coding', []))
                if identifier.startswith('VitalSigns.') != vital: continue
            if code == 'unchanged': continue
            if code == 'as-text':
                from xml.etree import ElementTree
                narrative = resource.get('text', {}).get('div', '<div/>')
                assert ''.join(ElementTree.fromstring(narrative).itertext()).strip(), 'DAR as-text needs narrative'
            if field['representation'] == 'dataAbsentReason':
                values = list(zip(original.get('component', []), resource.get('component', []))) if field['field'].startswith('component.') else [(original, resource)]
                if field['field'].startswith('component.'):
                    assert len(original.get('component', [])) == len(resource.get('component', [])), 'DAR component count differs'
                for before, after in values:
                    keys = [k for k in before if k.startswith('value')]
                    if not keys: continue
                    assert len(keys) == 1
                    key = keys[0]
                    if (key == 'valueQuantity') != (field['semanticGroup'] == 'numeric-measurement'): continue
                    assert not any(k.startswith('value') for k in after), 'DAR measurement still contains a value'
                    assert after.get('dataAbsentReason') == {'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/data-absent-reason', 'code': code}]}, 'Wrong measurement DAR'
                    after.pop('dataAbsentReason')
                    after[key] = deepcopy(before[key])
                    if 'dataAbsentReason' in before: after['dataAbsentReason'] = deepcopy(before['dataAbsentReason'])
            else:
                path = field['field'].split('.')
                if identifier == 'Encounter.admissionReasonFourthDigit':
                    path = ['extension:Aufnahmegrund', 'extension:VierteStelle', 'valueCoding', 'code']
                restore_path(original, resource, path, field, code)
            if identifier == 'DocumentReference.content.attachment.data':
                for content in resource.get('content', []):
                    attachment = content['attachment']
                    assert 'size' not in attachment and 'hash' not in attachment, 'DAR attachment leaks size or hash'
                    assert not attachment.get('url', '').lower().startswith('data:'), 'DAR attachment leaks embedded data URL'
        if resource['resourceType'] == 'Encounter' and output_period != resource.get('period'):
            ended = bool((output_period or {}).get('end'))
            assert resource.get('status') == ('finished' if ended else 'in-progress'), 'DAR contact status differs'
            for place in resource.get('location', []):
                assert place.get('period') == output_period, 'DAR location period differs'
                assert place.get('status') == ('completed' if ended else 'active'), 'DAR location status differs'
                place['period'] = deepcopy(resource['period'])
                place['status'] = 'completed' if resource['period'].get('end') else 'active'
            resource['status'] = 'finished' if resource['period'].get('end') else 'in-progress'
    return result


def restore_path(before, after, parts, field, code):
    key, *rest = parts
    if ':' in key:
        name, suffix = key.split(':')
        originals = [e for e in before.get(name, []) if e.get('url', '').rsplit('/', 1)[-1] == suffix]
        actuals = [e for e in after.get(name, []) if e.get('url', '').rsplit('/', 1)[-1] == suffix]
        assert len(originals) == len(actuals), 'DAR extension count differs'
        for a, b in zip(originals, actuals): restore_path(a, b, rest, field, code)
        return
    if key.endswith('[x]'):
        prefix = key[:-3]
        choices = [k for k in before if k.startswith(prefix) and k[len(prefix):len(prefix)+1].isupper()]
        if not choices:
            if field.get('supportedChoices', [])[:1] != ['dateTime']: return
            key = prefix + 'DateTime'
        else:
            assert len(choices) == 1
            key = choices[0]
        assert not any(k.startswith(prefix) and k[len(prefix):len(prefix)+1].isupper() and k != key for k in after), 'Unexpected DAR choice type'
    value = before.get(key)
    if rest:
        if isinstance(value, list):
            actual = after.get(key, [])
            assert len(actual) == len(value), 'DAR repeated element count differs: ' + key
            for a, b in zip(value, actual): restore_path(a, b, rest, field, code)
        elif isinstance(value, dict) or field['applyTo'] == 'existing-or-missing-scalar':
            actual = after.get(key, {})
            restore_path(value or {}, actual, rest, field, code)
            if not actual: after.pop(key, None)
        return
    repeated = key in ('given', 'line')
    if repeated and value is None: return
    complex_value = isinstance(value, dict) or field['representation'] == 'complex-extension'
    if isinstance(value, list):
        expected = [absent(code) for _ in value]
        assert after.get('_' + key) == expected, 'Wrong repeated primitive DAR: ' + key
        assert key not in after or after[key] == [None] * len(value), 'DAR primitive array still contains values'
    elif complex_value:
        expected = {'extension': [e for e in (value or {}).get('extension', []) if e.get('url') != DAR] + absent(code)['extension']}
        if (value or {}).get('id'): expected['id'] = value['id']
        assert after.get(key) == expected, 'Wrong complex DAR: ' + key
    else:
        expected = deepcopy(before.get('_' + key, {}))
        expected['extension'] = [e for e in expected.get('extension', []) if e.get('url') != DAR] + absent(code)['extension']
        assert key not in after, 'DAR primitive still contains value: ' + key
        assert after.get('_' + key) == expected, 'Wrong primitive DAR: ' + key
    for name in (key, '_' + key):
        if name in before: after[name] = deepcopy(before[name])
        else: after.pop(name, None)
