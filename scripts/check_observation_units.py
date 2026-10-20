"""Verify deliberate UCUM code relocation before comparing clinical values."""
from copy import deepcopy
from check_output_selection import enabled, resource_key


def checked_observation_units(resources, options, originals):
    result = deepcopy(resources)
    for resource in result:
        if resource['resourceType'] != 'Observation':
            continue
        if not enabled(options, resource_key(resource) + '_UCUM_CODE_IN_UNIT', False):
            continue
        original = originals.get(resource.get('id'), {})
        original_values = [original, *original.get('component', [])]
        for index, value in enumerate([resource, *resource.get('component', [])]):
            quantity = value.get('valueQuantity')
            if quantity is None:
                continue
            before = original_values[index].get('valueQuantity', {}) if index < len(original_values) else {}
            if before.get('system') != 'http://unitsofmeasure.org' or not before.get('code'):
                continue
            assert quantity.get('system') == 'http://unitsofmeasure.org', 'UCUM quantity system changed'
            assert 'code' not in quantity and quantity.get('unit') == before['code'], 'UCUM code was not moved into unit'
            quantity['code'] = before['code']
            if 'unit' in before: quantity['unit'] = before['unit']
            else: quantity.pop('unit', None)
    return result
