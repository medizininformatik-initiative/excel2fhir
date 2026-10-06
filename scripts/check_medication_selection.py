"""Verify product reference identity and descriptive output from input rows."""
from check_output_selection import descriptive_reference, named_id, enabled


def product_id(row):
    # Java length prefixes count UTF-16 code units, including supplementary characters.
    parts = [str(part or '') for part in row[3:11]]
    return named_id('Medication', ''.join(str(len(p.encode('utf-16-le')) // 2) + ':' + p for p in parts))


def check_medication_references(rows, resources, options, pid):
    types = {'Verordnung': ('MedicationRequest', 'MR', 'MEDICATION_REQUEST'),
             'Verabreichung': ('MedicationAdministration', 'MA', 'MEDICATION_ADMINISTRATION'),
             'Medikationsaussage': ('MedicationStatement', 'MS', 'MEDICATION_STATEMENT')}
    counts, expected = {}, {}
    for row in rows:
        kind, suffix, key = types[row[2]]
        number = counts.get(kind, int(options.get('START_ID_' + key, 1)))
        counts[kind] = number + 1
        identifier = pid + ('-E-' + row[1] if row[1] else '') + '-' + suffix + '-' + str(number)
        if enabled(options, key + '_ENABLED'):
            expected[identifier] = descriptive_reference('Medication', product_id(row), row[3], options)
    actual = {r['id']: r for r in resources if r['resourceType'] in {t[0] for t in types.values()}}
    assert actual.keys() == expected.keys(), 'Medication event identities differ'
    for identifier, resource in actual.items():
        assert resource.get('medicationReference', {}) == expected[identifier], 'Medication reference differs: ' + identifier
        assert 'medicationCodeableConcept' not in resource, 'Unexpected medication representation'
