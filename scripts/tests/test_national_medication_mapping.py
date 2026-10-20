import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from national_medication_mapping import NationalMedicationMapping, RXNORM, PATH
import test_medication_products
from clinical_import import prepare_clinical


class NationalMedicationTest(unittest.TestCase):
    def test_classification_is_route_and_combination_specific(self):
        mapping = NationalMedicationMapping()
        for source, target in [('106892', 'A10AD01'), ('311034', 'A10AB01'),
                               ('1535362', 'A01AA01'), ('106258', 'D07AA02'),
                               ('1049625', 'N02AJ13'), ('483438', 'N02BF02'),
                               ('309076', 'J01DD13'), ('25033', 'J01DD08')]:
            result = mapping.select({'system': RXNORM, 'code': source})
            self.assertEqual(result['atc'], {'code': target, 'version': '2026'})

    def test_unknown_changed_display_or_version_is_never_guessed(self):
        mapping = NationalMedicationMapping()
        for source in [{'system': RXNORM, 'code': 'not-a-code'},
                       {'system': RXNORM, 'code': '206905', 'display': 'Different strength'},
                       {'system': RXNORM, 'code': '206905', 'version': 'future'}]:
            before = copy.deepcopy(source)
            result = mapping.select(source)
            self.assertEqual(source, before)
            self.assertIsNone(result['atc'])
            self.assertIsNone(result['target'])
            self.assertEqual(result['status'], 'unmapped')

    def test_real_pack_and_open_mapping_both_retain_medication_event(self):
        source = test_medication_products.ProductTest().medication_bundle()
        rx = source['entry'][-1]['resource']
        for code, pzn, atc in [('206905', '00266011', 'M01AE01'), ('not-a-code', '', '')]:
            rx['medicationCodeableConcept'] = {'coding': [{'system': RXNORM, 'code': code}]}
            rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
            row = rows['Medikation'][0]
            self.assertEqual((row['Präparatcode'], row['ATC-Code']), (pzn, atc))
            self.assertNotIn('RxNorm', row)
            self.assertEqual([row['Einzeldosis'], row['Dosiereinheit'], row['Dosen pro Tag']], ['2', 'mg', '3'])
            self.assertIn({'sourceId': 'rx', 'resourceType': 'MedicationRequest'}, report['clinicalImports'])
            if not pzn:
                self.assertIn('ATC-Zuordnung offen', row['Präparatbezeichnung'])

    def test_every_registered_rxnorm_concept_has_an_explicit_outcome(self):
        registry = json.loads(PATH.with_name('synthea-source-code-registry.json').read_text())
        mapping = NationalMedicationMapping()
        self.assertEqual(set(mapping.entries), {(e['system'], e['code']) for e in registry['entries']
                                                if e['system'] == RXNORM})

    def test_source_dose_system_is_preserved_without_ucum_assumption(self):
        source = test_medication_products.ProductTest().medication_bundle()
        request = source['entry'][-1]['resource']
        request['medicationCodeableConcept'] = {'coding': [{'system': RXNORM, 'code': 'not-a-code'}]}
        dose = request['dosageInstruction'][0]['doseAndRate'][0]['doseQuantity']
        dose.update(system='my-units', code='µg')
        request['dosageInstruction'][0]['text'] = 'Supplied instruction'
        rows, _ = prepare_clinical(source['entry'], 'p', {'e': '1'})
        self.assertEqual(['2', 'µg', 'my-units', '3', 'Supplied instruction'], [rows['Medikation'][0]['Einzeldosis'], rows['Medikation'][0]['Dosiereinheit'], rows['Medikation'][0]['Dosiereinheitcodesystem'], rows['Medikation'][0]['Dosen pro Tag'], rows['Medikation'][0]['Dosierungstext']])

    def test_every_registered_medication_imports_with_atc_and_real_ingredient_keys(self):
        mapping = NationalMedicationMapping()
        source = test_medication_products.ProductTest().medication_bundle()
        for entry in mapping.entries.values():
            coding = {k: entry['source'][k] for k in ('system', 'code')}
            source['entry'][-1]['resource']['medicationCodeableConcept'] = {'coding': [coding]}
            rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
            self.assertEqual(len(rows['Medikation']), 1, coding)
            row = rows['Medikation'][0]
            decision = report['clinicalMappings'][0]
            self.assertRegex(row['Präparatcode'], r'^[0-9]{8}$')
            self.assertEqual('PZN', row['Präparatcodesystem'])
            self.assertTrue(row['ATC-Code'], coding)
            self.assertEqual(row['ATC-Version'], '2026')
            self.assertEqual(row['Wirkstoffcodesystem'], 'UNII')
            self.assertNotIn('productReview', decision)
            self.assertEqual(['2', 'mg', '', '3', ''], [row[k] for k in
                             ('Einzeldosis', 'Dosiereinheit', 'Dosiereinheitcodesystem', 'Dosen pro Tag', 'Dosierungstext')], coding)
            for ingredient in filter(None, row['Wirkstoffcode'].split('; ')):
                self.assertRegex(ingredient, r'^[A-Z0-9]{10}$')
            self.assertNotIn('RxNorm', row)
            self.assertEqual(report['medicationMappingSummary']['withIngredients'], int(bool(row['Wirkstoffcode'])))

    def test_different_ingredient_candidate_does_not_replace_existing_dose(self):
        source = test_medication_products.ProductTest().medication_bundle()
        source['entry'][-1]['resource']['medicationCodeableConcept'] = {
            'coding': [{'system': RXNORM, 'code': '1860491'}]}
        rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
        row = rows['Medikation'][0]
        self.assertIn('Hydromorphon', row['Präparatbezeichnung'])
        self.assertEqual('N02AA03', row['ATC-Code'])
        self.assertTrue(row['Wirkstoffcode'])
        self.assertEqual('09229081', row['Präparatcode'])
        self.assertEqual([row['Einzeldosis'], row['Dosiereinheit'], row['Dosen pro Tag']], ['2', 'mg', '3'])
        self.assertNotIn('productReview', report['clinicalMappings'][0])
        self.assertTrue(report['clinicalMappings'][0]['sourceDosage'])

    def test_unitless_oral_count_is_not_interpreted_as_milligrams(self):
        source = test_medication_products.ProductTest().medication_bundle()
        request = source['entry'][-1]['resource']
        request['medicationCodeableConcept'] = {'coding': [{'system': RXNORM, 'code': '197541'}]}
        quantity = request['dosageInstruction'][0]['doseAndRate'][0]['doseQuantity']
        quantity.pop('code')
        before = copy.deepcopy(source)
        rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
        self.assertEqual([rows['Medikation'][0][i] for i in ('Einzeldosis', 'Dosiereinheit', 'Dosen pro Tag')], ['2', '', '3'])
        self.assertEqual(source, before)
        self.assertNotIn('doseUnitEnrichment', report['clinicalMappings'][0])
        self.assertEqual('19053588', rows['Medikation'][0]['Präparatcode'])
        self.assertNotIn('productReview', report['clinicalMappings'][0])
        quantity['code'] = 'mg'
        rows, _ = prepare_clinical(source['entry'], 'p', {'e': '1'})
        self.assertEqual([rows['Medikation'][0][i] for i in ('Einzeldosis', 'Dosiereinheit', 'Dosen pro Tag')], ['2', 'mg', '3'])
        quantity.pop('code')
        quantity['system'] = 'custom-units'
        rows, report = prepare_clinical(source['entry'], 'p', {'e': '1'})
        self.assertEqual(['2', '', 'custom-units'], [rows['Medikation'][0]['Einzeldosis'], rows['Medikation'][0]['Dosiereinheit'], rows['Medikation'][0]['Dosiereinheitcodesystem']])
        self.assertNotIn('doseUnitEnrichment', report['clinicalMappings'][0])

    def test_german_herceptin_excludes_hyaluronidase_excipient(self):
        result = NationalMedicationMapping().select({'system': RXNORM, 'code': '2119714'})
        self.assertEqual([i['code'] for i in result['ingredients']], ['P188ANX8CK'])
        self.assertEqual(result['target']['code'], '10414263')

    def test_enriched_administrations_never_have_text_only_dosage(self):
        mapping = NationalMedicationMapping()
        for entry in mapping.entries.values():
            resource = {'resourceType': 'MedicationAdministration', 'id': 'admin',
                        'subject': {'reference': 'urn:uuid:p'}, 'status': 'completed',
                        'effectiveDateTime': '2026-01-02T08:00:00Z',
                        'medicationCodeableConcept': {'coding': [{
                            'system': RXNORM, 'code': entry['source']['code'],
                            'display': entry['source']['displays'][0]}]}}
            entries = test_medication_products.bundle()['entry'][:1] + [{'resource': resource}]
            rows, _ = prepare_clinical(entries, 'p', {})
            dose, unit, system, frequency, text = [rows['Medikation'][0]['Einzeldosis'], rows['Medikation'][0]['Dosiereinheit'], rows['Medikation'][0]['Dosiereinheitcodesystem'], rows['Medikation'][0]['Dosen pro Tag'], rows['Medikation'][0]['Dosierungstext']]
            if text or frequency:
                self.assertTrue(dose, entry['source']['code'])

    def test_same_pack_has_consistent_product_and_ingredient_facts(self):
        packs = {}
        for entry in NationalMedicationMapping().entries.values():
            product = entry['product']
            facts = (product['display'], product['doseForm'], entry['atc']['code'],
                     tuple((i['system'], i['code']) for i in entry['ingredients']))
            self.assertEqual(packs.setdefault(product['code'], facts), facts, product['code'])


if __name__ == '__main__': unittest.main()
