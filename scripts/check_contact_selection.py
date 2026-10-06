"""Source-derived contact identities and hierarchy, independent of emitted partOf."""
from check_encounter_policies import instant
from check_output_selection import enabled
from synthea_movements import enrich

LEVELS = {'facility': 'einrichtungskontakt', 'department': 'abteilungskontakt',
          'ward-service': 'versorgungsstellenkontakt'}
KINDS = {'Normalstationär': 'normalstationaer', 'Intensivstationär': 'intensivstationaer',
         'Operation': 'operation', 'Untersuchung und Behandlung': 'ub', 'Konsil': 'konsil'}


def contact_expectations(source, report):
    from synthea_to_excel import CLASSES
    pid = report.get('outputPatient', report['sourcePatient'].replace('_', '-'))
    options = report.get('converterOptions', {})
    source_contacts = [e['resource'] for e in source['entry'] if e['resource']['resourceType'] == 'Encounter']
    if 'movements' in report:
        rows = [[report['sourcePatient'], report['encounterNumbers'][r['id']], '', '', CLASSES[r['class']['code']], '', '', '', '', '']
                for r in source_contacts]
        _, movement = enrich(source, rows, report['encounterNumbers'])
        assert movement == report['movements'], 'Movement report or rules changed'
    else:
        movement = {'contacts': []}
    expected = {}
    for r in source_contacts:
        identifier = pid + '-E-' + report['encounterNumbers'][r['id']]
        expected[identifier] = dict(id=identifier, root=identifier, level='facility', kind='',
                                    start=r.get('period', {}).get('start'), end=r.get('period', {}).get('end'),
                                    missing=not r.get('period', {}).get('end'),
                                    cls='AMB' if r['class']['code'] == 'EMER' else r['class']['code'], parent=None)
    department_number = int(options.get('START_ID_ENCOUNTER_LEVEL_2', 1))
    ward_number = int(options.get('START_ID_ENCOUNTER_LEVEL_3', 1))
    phase, previous, primary_end = None, None, None
    for row in movement['contacts']:
        root = pid + '-E-' + row[1]
        secondary = row[10] in ('Operation', 'Untersuchung und Behandlung', 'Konsil')
        if not secondary:
            if previous != (root, row[5]):
                phase = root + '-A-' + str(department_number)
                department_number += 1
                expected[phase] = dict(id=phase, root=root, level='department', kind='', start=row[2],
                                       end=row[3], missing=not row[3], cls=expected[root]['cls'], parent=root)
            else:
                expected[phase]['end'] = row[3]
            previous, primary_end = (root, row[5]), row[3]
        identifier = root + '-V-' + str(ward_number)
        ward_number += 1
        expected[identifier] = dict(id=identifier, root=root, level='ward-service', kind=KINDS.get(row[10], ''),
                                    start=row[2], end=row[3] or primary_end, missing=not row[3],
                                    cls=expected[root]['cls'], parent=phase or root, row=row)
    return expected


def generic_contacts(options):
    return all(not enabled(options, 'CONTACT_' + level + '_ENABLED')
               for level in ('FACILITY', 'DEPARTMENT', 'WARD_SERVICE'))


def contact_selected(contact, options):
    if not enabled(options, 'ENCOUNTER_ENABLED'):
        return False
    scope = {'AMB': 'AMBULATORY', 'IMP': 'INPATIENT'}.get(contact['cls'])
    if scope and not enabled(options, 'ENCOUNTER_' + scope + '_ENABLED'):
        return False
    if generic_contacts(options):
        return contact['level'] == 'facility'
    return enabled(options, 'CONTACT_' + contact['level'].replace('-', '_').upper() + '_ENABLED')


def expected_parent(contact, contacts, options):
    default = {'facility': 'none', 'department': 'facility', 'ward-service': 'department'}
    level = options.get('CONTACT_' + contact['level'].replace('-', '_').upper() + '_PART_OF', default[contact['level']])
    parent = contacts.get(contact['parent'])
    while parent:
        if parent['level'] == level:
            return 'Encounter/' + parent['id'] if contact_selected(parent, options) else None
        parent = contacts.get(parent['parent'])
    return None


def checked_contacts(source, target, report):
    expected = contact_expectations(source, report)
    options = report.get('converterOptions', {})
    actual = [e['resource'] for e in target['entry'] if e['resource']['resourceType'] == 'Encounter']
    wanted = {key: c for key, c in expected.items() if contact_selected(c, options)}
    assert len(actual) == len(wanted) and {r['id'] for r in actual} == wanted.keys(), 'Configured contact identities differ'
    for r in actual:
        contact = wanted[r['id']]
        levels = [c.get('code') for t in r.get('type', []) for c in t.get('coding', [])
                  if c.get('system') == 'http://fhir.de/CodeSystem/Kontaktebene']
        assert levels == ([] if generic_contacts(options) else [LEVELS[contact['level']]]), 'Contact level differs'
        assert r.get('partOf', {}).get('reference') == expected_parent(contact, expected, options), 'Contact parent differs'
        assert r['class']['code'] == contact['cls'], 'Contact class differs'
        period = r.get('period', {})
        assert instant(period.get('start')) == instant(contact['start']), 'Contact start differs'
        assert instant(period.get('end')) == instant(contact['end']), 'Contact end differs'
        if contact['level'] == 'ward-service':
            kinds = [c.get('code') for t in r.get('type', []) for c in t.get('coding', [])
                     if c.get('system') == 'http://fhir.de/CodeSystem/kontaktart-de']
            assert kinds == ([contact['kind']] if contact['kind'] else []), 'Contact kind differs'
    return expected
