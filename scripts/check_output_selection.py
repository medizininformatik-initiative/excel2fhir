"""Independent expectations for configured resource and reference selection."""
import hashlib
import re
import uuid


def enabled(options, key, default=True):
    return str(options.get(key, default)).lower() == 'true'


def resource_key(resource):
    kind = resource['resourceType']
    if kind == 'Observation':
        vital = any(c.get('system') == 'http://terminology.hl7.org/CodeSystem/observation-category'
                    and c.get('code') == 'vital-signs'
                    for category in resource.get('category', []) for c in category.get('coding', []))
        return 'OBSERVATION_VITAL_SIGNS' if vital else 'OBSERVATION_LABORATORY'
    return re.sub(r'(?<!^)(?=[A-Z])', '_', kind).upper()


def selected(resource, options):
    kind = resource['resourceType']
    if kind in ('Patient', 'Medication', 'Location'):
        return options.get(kind.upper() + '_MODE', 'generate-reference') == 'generate-reference'
    return enabled(options, resource_key(resource) + '_ENABLED')


def named_id(kind, key):
    return kind + '-' + str(uuid.UUID(bytes=hashlib.md5(key.encode('utf-8')).digest(), version=3))


def event_id(pid, kind, source_id):
    return named_id(kind, pid + '|' + kind + '|' + source_id)


def descriptive_reference(kind, identifier, display, options):
    mode = options.get(kind.upper() + '_MODE', 'generate-reference')
    if mode == 'neither':
        return {}
    if mode == 'generate-reference':
        return {'reference': kind + '/' + identifier}
    result = {}
    if enabled(options, kind.upper() + '_EXISTING_REFERENCE'):
        result['reference'] = kind + '/' + identifier
    if enabled(options, kind.upper() + '_DESCRIPTION', False):
        result['identifier'] = {'value': identifier}
        if display:
            result['display'] = display
    return result


def patient_owner(resource, patient_ids, source):
    """Patient omission keeps IDs; hashed event IDs also encode input identity."""
    kind, identifier = resource['resourceType'], resource['id']
    if kind in ('Immunization', 'DiagnosticReport', 'CarePlan', 'Observation'):
        candidates = [pid for pid in patient_ids for entry in source['entry']
                      if entry['resource']['resourceType'] == kind
                      and event_id(pid, kind, entry['resource']['id']) == identifier]
    else:
        candidates = [pid for pid in patient_ids if identifier.startswith(pid + '-')]
        # Prefixes may overlap; the longest exact patient prefix owns the ID.
        if candidates:
            candidates = [max(candidates, key=len)]
    assert len(candidates) == 1, 'Cannot attribute output resource: ' + identifier
    return candidates[0]
