"""Existing source regimens outrank candidate product enrichment."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from clinical_import import prepare_clinical
from medication_dose_policy import has_source_dosage
import test_medication_products
from national_medication_mapping import RXNORM


class MedicationDosePolicyTest(unittest.TestCase):
    def project(self, code, dosage, kind='MedicationRequest'):
        source = test_medication_products.ProductTest().medication_bundle()
        resource = source['entry'][-1]['resource']
        resource['resourceType'] = kind
        resource['medicationCodeableConcept'] = {'coding': [{'system': RXNORM, 'code': code}]}
        resource.pop('dosageInstruction')
        resource['dosage' if kind == 'MedicationAdministration' else 'dosageInstruction'] = dosage
        before = copy.deepcopy(source)
        with patch('medication_products.local_catalog_path', return_value=Path('/private/tmp/nonexistent-dose-policy-catalog.json')):
            rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
        self.assertEqual(before, source)
        return rows['Medikation'][0], report['clinicalMappings'][0]

    def test_nitrofurantoin_keeps_one_twice_daily_without_inventing_unit(self):
        dosage = [{'doseAndRate': [{'doseQuantity': {'value': 1.0}}],
                   'timing': {'repeat': {'frequency': 2, 'period': 1, 'periodUnit': 'd'}},
                   'asNeededBoolean': False}]
        row, decision = self.project('1648755', dosage)
        self.assertEqual(['1.0', '', '', '2', ''], [row[k] for k in
                         ('Einzeldosis', 'Dosiereinheit', 'Dosiereinheitcodesystem', 'Dosen pro Tag', 'Dosierungstext')])
        self.assertEqual('07097540', row['Präparatcode'])
        self.assertNotIn('Nifuretten', row['Präparatbezeichnung'])
        self.assertIn('nitrofurantoin', row['Präparatbezeichnung'].lower())
        self.assertNotIn('productReview', decision)
        self.assertEqual('Hartkapsel, retardiert', row['Darreichungsform'])
        self.assertEqual(dosage, decision['sourceDosage'])
        self.assertEqual('J01XE01', row['ATC-Code'])

    def test_text_only_as_needed_and_later_instructions_prevent_replacement(self):
        for dosage in ([{'text': 'Take as needed.', 'asNeededBoolean': True}],
                       [{'asNeededBoolean': True}], [{'route': {'text': 'inhaled'}}],
                       [{}, {'text': 'Second instruction'}]):
            row, decision = self.project('630208', dosage)
            self.assertEqual('', row['Einzeldosis'])
            self.assertEqual('', row['Dosen pro Tag'])
            self.assertTrue(row['Präparatcode'])
            self.assertEqual(dosage[0].get('text', ''), row['Dosierungstext'])
            self.assertEqual(dosage, decision['sourceDosage'])

    def test_explicit_zero_and_custom_unit_in_administration_remain(self):
        dosage = {'dose': {'value': 0, 'code': 'own', 'system': 'urn:own'}, 'text': 'Explicit zero'}
        row, decision = self.project('1648755', dosage, 'MedicationAdministration')
        self.assertEqual(['0', 'own', 'urn:own', 'Explicit zero'], [row[k] for k in
                         ('Einzeldosis', 'Dosiereinheit', 'Dosiereinheitcodesystem', 'Dosierungstext')])
        self.assertEqual(dosage, decision['sourceDosage'])

    def test_synthetic_regimen_is_only_used_without_source_facts(self):
        self.assertFalse(has_source_dosage([{'sequence': 1}]))
        for dosage in ([], [{}], [{'sequence': 1}]):
            row, decision = self.project('1535362', dosage)
            self.assertEqual(('0.25', 'ml', '01285625'),
                             (row['Einzeldosis'], row['Dosiereinheit'], row['Präparatcode']))
            self.assertNotIn('productReview', decision)

    def test_compatible_product_keeps_partial_dosage_without_filling_unit(self):
        row, decision = self.project('206905', [{'doseAndRate': [{'doseQuantity': {'value': 1}}]}])
        self.assertEqual(('00266011', '1', ''), (row['Präparatcode'], row['Einzeldosis'], row['Dosiereinheit']))
        self.assertNotIn('productReview', decision)

    def test_salt_strength_and_representative_labels_do_not_block_products(self):
        dosage = [{'doseAndRate': [{'doseQuantity': {'value': 1}}],
                   'timing': {'repeat': {'frequency': 1, 'period': 1, 'periodUnit': 'd'}}}]
        for code, pzn in [('197541', '19053588'), ('1147334', '11563775')]:
            row, decision = self.project(code, dosage)
            self.assertEqual((pzn, '1', '', '1'), (row['Präparatcode'], row['Einzeldosis'], row['Dosiereinheit'], row['Dosen pro Tag']))
            self.assertNotIn('productReview', decision)
            self.assertTrue(row['ATC-Code'])
            self.assertTrue(row['Wirkstoffcode'])
            self.assertEqual(dosage, decision['sourceDosage'])

    def test_ingredient_change_retains_product_for_every_supplied_dose(self):
        for code in ('312748', '1860491'):
            for quantity in ({'value': 1}, {'value': 1, 'code': '{tbl}'},
                             {'value': 10, 'code': 'mg'},
                             {'value': 1, 'code': '{tbl}', 'system': 'urn:custom'}):
                dosage = [{'doseAndRate': [{'doseQuantity': quantity}]}]
                row, decision = self.project(code, dosage)
                self.assertNotIn('productReview', decision)
                self.assertEqual(True, bool(row['Präparatcode']))
                self.assertEqual(True, bool(row['ATC-Code']))
                self.assertEqual(True, bool(row['Wirkstoffcode']))
                self.assertEqual(dosage, decision['sourceDosage'])
                self.assertEqual(str(quantity['value']), row['Einzeldosis'])
                self.assertEqual(quantity.get('code', ''), row['Dosiereinheit'])

    def test_same_strength_statin_representative_keeps_explicit_milligrams(self):
        dosage = [{'doseAndRate': [{'doseQuantity': {'value': 10, 'code': 'mg'}}]}]
        row, decision = self.project('197903', dosage)
        self.assertIn('Simvastatin', row['Präparatbezeichnung'])
        self.assertEqual(('10', 'mg'), (row['Einzeldosis'], row['Dosiereinheit']))
        self.assertNotIn('productReview', decision)

    def test_release_combination_and_biological_unit_conflicts_remain_visible(self):
        for code in ('856460', '1599803', '860182', '905377'):
            row, decision = self.project(code, [{'text': 'Original instruction'}])
            self.assertTrue(row['Präparatcode'])
            self.assertEqual('Original instruction', row['Dosierungstext'])
            self.assertTrue(decision['sourceDoseReview']['note'])
            self.assertNotIn('productReview', decision)

    def test_physical_dose_in_later_instruction_range_rate_or_text_is_not_missed(self):
        for dosage in ([{}, {'doseAndRate': [{'doseQuantity': {'value': 10, 'code': 'mg'}}]}],
                       [{'doseAndRate': [{'doseRange': {'low': {'value': 5, 'unit': 'mg'}}}]}],
                       [{'doseAndRate': [{'rateQuantity': {'value': 5, 'code': 'mg/h'}}]}],
                       [{'text': '10 mg morgens'}]):
            row, decision = self.project('1860491', dosage)
            self.assertTrue(row['Präparatcode'])
            self.assertEqual(dosage, decision['sourceDosage'])


if __name__ == '__main__': unittest.main()
