import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_diagnosis_mapping import audit, condition_sources


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        path = self.root / 'src/main/resources/modules/example.json'
        path.parent.mkdir(parents=True)
        code = {'system': 'SNOMED-CT', 'code': 'source', 'display': 'Source label'}
        self.module = path
        self.write(path, {'states': {
            'Onset': {'type': 'ConditionOnset', 'codes': [code]},
            'End': {'type': 'ConditionEnd', 'codes': [{**code, 'code': 'end-only'}]},
            'Other': {'type': 'Procedure', 'codes': [{**code, 'code': 'procedure-only'}]}}})
        template = self.root / 'src/main/resources/templates/modules/template.json'
        template.parent.mkdir(parents=True)
        self.write(template, {'states': {'Onset': {'type': 'ConditionOnset', 'codes': [{**code, 'code': '1234'}]}}})
        sources = condition_sources(self.root)
        self.catalogue = self.root / 'catalogue.json'
        self.terminal = self.root / 'terminal.json'
        self.table = self.root / 'mapping.json'
        self.write(self.catalogue, {'concept': [{'code': 'target', 'display': 'Target label'}]})
        self.write(self.terminal, {'expansion': {'contains': [{'code': 'target'}]}})
        self.mapping = {'targetCatalogue': {
            'sha256': hashlib.sha256(self.catalogue.read_bytes()).hexdigest(),
            'terminalValueSetSha256': hashlib.sha256(self.terminal.read_bytes()).hexdigest()},
            'entries': [{'sourceCode': 'source', 'sourceDisplay': 'Source label',
                         'sourceDisplays': ['Source label'], 'sources': sources['source']['sources'],
                         'relation': 'approximate', 'reason': 'Reviewed approximation.',
                         'target': {'system': 'http://fhir.de/CodeSystem/bfarm/icd-10-gm',
                                    'version': '2026', 'code': 'target', 'display': 'Target label'}}]}

    def write(self, path, data):
        path.write_text(json.dumps(data))

    def run_audit(self):
        self.write(self.table, self.mapping)
        return audit(self.root, self.catalogue, self.terminal, self.table)

    def test_excludes_templates_non_conditions_and_condition_end(self):
        self.assertEqual(set(condition_sources(self.root)), {'source'})
        self.assertEqual(self.run_audit()['sourceConcepts'], 1)

    def test_rejects_non_source_mapping_and_modified_module(self):
        self.mapping['entries'][0]['sourceCode'] = 'outside-synthea'
        with self.assertRaises(ValueError):
            self.run_audit()
        self.mapping['entries'][0]['sourceCode'] = 'source'
        self.module.write_text(self.module.read_text() + '\n')
        with self.assertRaises(ValueError):
            self.run_audit()

    def test_rejects_invalid_target_and_catalogue_drift(self):
        self.mapping['entries'][0]['target']['code'] = 'unlisted'
        with self.assertRaises(ValueError):
            self.run_audit()
        self.mapping['entries'][0]['target']['code'] = 'target'
        self.terminal.write_text('{}')
        with self.assertRaises(ValueError):
            self.run_audit()


if __name__ == '__main__':
    unittest.main()
