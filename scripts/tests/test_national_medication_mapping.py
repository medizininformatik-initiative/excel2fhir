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
                               ('1049625', 'N02AJ17'), ('483438', 'N02BF02'),
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
            self.assertEqual((row[4], row[6]), (pzn, atc))
            self.assertNotIn('RxNorm', row)
            self.assertEqual(row[16:19], ['2', 'mg', '3'])
            self.assertIn({'sourceId': 'rx', 'resourceType': 'MedicationRequest'}, report['clinicalImports'])
            if not pzn:
                self.assertIn('ATC-Zuordnung offen', row[3])

    def test_every_registered_rxnorm_concept_has_an_explicit_outcome(self):
        registry = json.loads(PATH.with_name('synthea-source-code-registry.json').read_text())
        mapping = NationalMedicationMapping()
        self.assertEqual(set(mapping.entries), {(e['system'], e['code']) for e in registry['entries']
                                                if e['system'] == RXNORM})


if __name__ == '__main__': unittest.main()
