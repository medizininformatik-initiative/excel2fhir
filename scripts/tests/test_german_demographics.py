import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from german_demographics import identity, localize_document_identity, check_patient
from synthea_to_excel import prepare
from test_synthea_import import bundle


class DemographicsTest(unittest.TestCase):
    def test_independent_of_source_order_names_race_and_address(self):
        patient = bundle()['entry'][0]['resource']
        before = copy.deepcopy(patient)
        expected = identity(patient)
        self.assertEqual(patient, before)
        changed = dict(patient, name=[], address=[{'country':'US'}], extension=[{'valueString':'White'}])
        self.assertEqual(identity(changed), expected)
        self.assertEqual(identity(dict(patient, gender='other'))['name']['given'],
                         identity(dict(patient, gender='unknown'))['name']['given'])
        self.assertEqual(expected['address']['country'], 'DE')
        self.assertRegex(expected['address']['postalCode'], r'^\d{5}$')
        self.assertFalse(any(c.isdigit() for c in str(expected['name'])))

    def test_patient_contract_rejects_changed_address_birth_and_report(self):
        source = bundle()['entry'][0]['resource']
        replacement = identity(source)
        target = dict(source, name=[replacement['name']], address=[replacement['address']])
        report = {'demographics':replacement}
        check_patient(source, target, report)
        for key in ('birthDate','gender'):
            with self.assertRaises(AssertionError):check_patient(source, dict(target, **{key:'wrong'}), report)
        changed = copy.deepcopy(target);changed['address'][0]['country'] = 'US'
        with self.assertRaises(AssertionError):check_patient(source, changed, report)
        with self.assertRaises(AssertionError):check_patient(source, target, {'demographics':{}})

    def test_document_replacement_preserves_clinical_text_and_word_boundaries(self):
        patient = dict(bundle()['entry'][0]['resource'], name=[{'given':['May123'], 'family':'Brown456'}])
        text = 'May123 Brown456. May123 has Brown syndrome in May. XMay123. Brown456.'
        result, count = localize_document_identity(text, patient)
        name = identity(patient)['name']
        self.assertEqual(result, f"{name['given'][0]} {name['family']}. {name['given'][0]} has Brown syndrome in May. XMay123. {name['family']}.")
        self.assertEqual(count, 3)
        ordinary = dict(patient, name=[{'given':['May'], 'family':'Brown'}])
        self.assertEqual(localize_document_identity('Brown syndrome in May.', ordinary), ('Brown syndrome in May.', 0))

    def test_excel_person_uses_new_identity_without_invented_consent(self):
        source = bundle();before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        self.assertEqual(rows['Person'][0][1:3],
                         [report['demographics']['name']['given'][0], report['demographics']['name']['family']])
        self.assertEqual(rows['Person'][0][6:13], [''] * 7)
        self.assertEqual(rows['Person'][0][17], 'DE')
        self.assertTrue(any(l['path']=='address' for l in report['losses']))


if __name__ == '__main__':unittest.main()
