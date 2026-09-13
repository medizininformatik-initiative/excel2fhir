"""Entirely fabricated target products; these fixtures contain no MMI records."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from medication_products import ProductCatalog, PZN, ROOT, require_external_output
from synthea_to_excel import prepare, write_workbook
from test_synthea_import import bundle

RXNORM = 'http://www.nlm.nih.gov/research/umls/rxnorm'
REGISTRY = json.loads((ROOT / 'scripts/mappings/synthea-source-code-registry.json').read_text())
SOURCE = next({'system': e['system'], 'code': e['code'], 'display': e['displays'][0]}
              for e in REGISTRY['entries'] if e['system'] == RXNORM and 'state-code:MedicationOrder' in e['usages'])


def fixture():
    return {'schemaVersion': 1, 'provider': 'mmi-local', 'id': 'synthetic-adapter-test',
            'sourceVersion': 'test-only', 'entries': [
                {'source': {k: SOURCE[k] for k in ('system', 'code')},
                 'target': {'system': PZN, 'code': '00000000',
                            'display': 'Erfundenes Adapter-Testpräparat', 'doseForm': 'Tablette'},
                 'doseCompatibility': 'unchanged',
                 'provenance': {'source': 'invented-test-fixture', 'method': 'test',
                                'evidence': 'Fabricated data; not a real product or verified PZN.'}}]}


class ProductTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'products.json'
        self.patch = patch('medication_products.local_catalog_path', return_value=self.path)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def save(self, data=None):
        self.path.write_text(json.dumps(fixture() if data is None else data))

    def medication_bundle(self):
        data = bundle()
        data['entry'].append({'resource': {'resourceType': 'MedicationRequest', 'id': 'rx',
            'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
            'medicationCodeableConcept': {'coding': [copy.deepcopy(SOURCE)]},
            'status': 'active', 'intent': 'order', 'authoredOn': '2026-01-02',
            'dosageInstruction': [{'doseAndRate': [{'doseQuantity': {'value': 2, 'code': 'mg'}}],
                                  'timing': {'repeat': {'frequency': 3, 'period': 1, 'periodUnit': 'd'}}}]}})
        return data

    def test_absent_catalog_preserves_codes_and_uses_existing_german_texts(self):
        rows, report = prepare(self.medication_bundle())
        self.assertEqual(rows['Medikation'][0][16:18], [SOURCE['code'], 'RxNorm'])
        self.assertTrue(rows['Medikation'][0][5])
        self.assertFalse(report['productDataUsage']['containsLocalProductData'])
        self.assertEqual(report['productCatalog']['provider'], 'public-source')

    def test_local_hit_changes_only_product_columns_and_keeps_provenance(self):
        source = self.medication_bundle()
        before = copy.deepcopy(source)
        public_rows, _ = prepare(source)
        self.save()
        local_rows, report = prepare(source)
        self.assertEqual(source, before)
        public, local = public_rows['Medikation'][0], local_rows['Medikation'][0]
        self.assertEqual([v for i,v in enumerate(public) if i not in (5,10,16,17)],
                         [v for i,v in enumerate(local) if i not in (5,10,16,17)])
        self.assertEqual(local[16:18], ['00000000', 'PZN'])
        self.assertEqual(local[5], 'Erfundenes Adapter-Testpräparat')
        decision = next(m for m in report['clinicalMappings'] if m['sourceId'] == 'rx')
        self.assertEqual(decision['source'], SOURCE)
        self.assertEqual(decision['provenance']['source'], 'invented-test-fixture')
        self.assertTrue(report['productDataUsage']['containsLocalProductData'])
        self.assertEqual(report['productDataUsage']['redistribution'], 'not-cleared')
        self.assertEqual(report['germanTexts']['missing'], prepare(source)[1]['germanTexts']['missing'])
        with self.assertRaises(ValueError): require_external_output(report, ROOT / 'target/local.xlsx')
        with self.assertRaises(ValueError): write_workbook(local_rows, ROOT / 'target/local.xlsx')
        require_external_output(report, self.path.parent / 'case.xlsx')

    def test_unknown_product_and_unresolved_dose_fall_back_without_target_data(self):
        self.save()
        self.assertIsNone(ProductCatalog().select({'system': RXNORM, 'code': 'not-in-inventory'})['target'])
        data = fixture(); data['entries'][0]['doseCompatibility'] = 'unresolved'; self.save(data)
        rows, report = prepare(self.medication_bundle())
        self.assertEqual(rows['Medikation'][0][16], SOURCE['code'])
        self.assertNotIn('Erfundenes Adapter-Testpräparat', json.dumps(report))
        self.assertFalse(report['productDataUsage']['containsLocalProductData'])

    def test_corrupt_ambiguous_or_untraceable_catalogue_is_not_silently_ignored(self):
        self.path.write_text('{')
        with self.assertRaises(ValueError): ProductCatalog()
        for mutate in [lambda d: d['entries'].append(copy.deepcopy(d['entries'][0])),
                       lambda d: d['entries'][0].pop('provenance'),
                       lambda d: d['entries'][0]['source'].update(code='not-in-synthea'),
                       lambda d: d['entries'][0]['target'].update(code=123),
                       lambda d: d['entries'][0]['target'].update(strength='unsupported')]:
            data = fixture(); mutate(data); self.save(data)
            with self.assertRaises(ValueError): ProductCatalog()

    def test_resolved_symlinks_cannot_move_catalog_or_output_into_repo(self):
        self.path.symlink_to(ROOT / 'pom.xml')
        with self.assertRaises(ValueError): ProductCatalog()
        output_link = self.path.parent / 'repo'; output_link.symlink_to(ROOT, target_is_directory=True)
        with self.assertRaises(ValueError):
            require_external_output({'productDataUsage': {'containsLocalProductData': True}}, output_link / 'case.xlsx')

    def test_catalog_is_frozen_per_import_and_reload_changes_fingerprint(self):
        self.save(); first = ProductCatalog()
        data = fixture(); data['entries'][0]['target']['display'] = 'Anderes Testpräparat'; self.save(data)
        second = ProductCatalog()
        self.assertNotEqual(first.metadata['sha256'], second.metadata['sha256'])
        self.assertEqual(first.select(SOURCE)['target']['display'], 'Erfundenes Adapter-Testpräparat')
        result = first.select(SOURCE); result['target']['display'] = 'mutation'
        self.assertEqual(first.select(SOURCE)['target']['display'], 'Erfundenes Adapter-Testpräparat')


if __name__ == '__main__': unittest.main()
