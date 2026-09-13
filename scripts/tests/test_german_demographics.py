import copy
import json
import hashlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from german_demographics import identity, localize_document_identity, check_patient
from synthea_to_excel import prepare
from test_synthea_import import bundle
from build_german_name_pool import unique_names, read_pools


class DemographicsTest(unittest.TestCase):
    def test_large_normalized_pools_have_provenance_and_license(self):
        root = Path(__file__).resolve().parents[2]
        data = json.loads((root/'scripts/mappings/german-demographics.json').read_text())
        self.assertGreaterEqual(len(data['familyNames']), 500)
        for values in data['givenNames'].values():self.assertGreaterEqual(len(values), 300)
        for values in [*data['givenNames'].values(), data['familyNames']]:
            self.assertEqual(values, unique_names(values))
            self.assertFalse(any('.' in n or any(c.isdigit() for c in n) for n in values))
        self.assertIn('Öztürk', data['familyNames'])
        self.assertIn('Nguyen', data['familyNames'])
        self.assertEqual(data['nameSources']['license'], 'MIT')
        self.assertIn('Copyright (c) 2012 Daniele Faraglia', (root/data['nameSources']['licenseFile']).read_text())
        self.assertEqual(len(data['nameSources']['files']), 3)

    def test_normalization_deduplicates_unicode_but_keeps_real_spelling_variants(self):
        self.assertEqual(unique_names([' Müller ', 'Mu\u0308ller', 'MÜLLER', 'Muller', 'H.-Dieter']), ['Muller', 'Müller'])
        source = "raise RuntimeError('must not execute')\nclass Provider:\n    last_names = ('Müller', 'Kaya')\n"
        self.assertEqual(read_pools(source)['last_names'], ('Müller','Kaya'))

    def test_name_pool_update_keeps_v1_address_assignment(self):
        patient = bundle()['entry'][0]['resource']
        root = Path(__file__).resolve().parents[2]
        data = json.loads((root/'scripts/mappings/german-demographics.json').read_text())
        def old_pick(label, values):
            digest = hashlib.sha256(('german-demographics-v1|'+patient['id']+'|'+label).encode()).digest()
            return values[int.from_bytes(digest,'big') % len(values)]
        place = old_pick('place',data['places'])
        expected = {k:place[k] for k in ('city','state','postalCode')}
        expected.update(country='DE', line=[old_pick('street',data['streets'])+' '+str(old_pick('number',list(range(1,100))))])
        self.assertEqual(identity(patient)['address'], expected)

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
        target = dict(source, name=[replacement['name']], address=[dict(replacement['address'], state='DE-NW')])
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
        self.assertEqual(rows['Person'][0][5:11], [''] * 6)
        self.assertEqual(rows['Person'][0][15], 'DE')
        self.assertTrue(any(l['path']=='address' for l in report['losses']))


if __name__ == '__main__':unittest.main()
