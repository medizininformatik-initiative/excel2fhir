"""Verify deliberate UCUM code relocation before comparing clinical values."""
from copy import deepcopy
from check_output_selection import enabled, resource_key


def checked_observation_units(resources, options):
    result = deepcopy(resources)
    for resource in result:
        if resource['resourceType'] != 'Observation':
            continue
        if not enabled(options, resource_key(resource) + '_UCUM_CODE_IN_UNIT', False):
            continue
        for value in [resource, *resource.get('component', [])]:
            quantity = value.get('valueQuantity')
            if quantity is None:
                continue
            assert quantity.get('system') == 'http://unitsofmeasure.org', 'UCUM quantity system changed'
            assert 'code' not in quantity and quantity.get('unit'), 'UCUM code was not moved into unit'
            # The following source comparison checks this unit against the original code.
            quantity['code'] = quantity['unit']
    return result
