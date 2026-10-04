import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('generate_schema', ROOT / 'generate_schema.py')
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class ContractConsistencyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = json.loads((ROOT / 'contract.json').read_text())
        cls.options = {o['id']: o for o in cls.contract['options']}

    def test_ids_defaults_and_dependencies_resolve(self):
        self.assertEqual(len(self.options), len(self.contract['options']))
        sections = {s['id'] for s in self.contract['sections']}
        graph = {}
        for key, option in self.options.items():
            self.assertIn(option['section'], sections, key)
            value = option['default']
            kind = option['type']
            if kind == 'enum':
                self.assertIn(value, option['choices'], key)
            elif kind == 'set':
                self.assertEqual(len(value), len(set(value)), key)
                self.assertTrue(set(value) <= set(option['choices']), key)
            else:
                self.assertIs(type(value), {'integer': int, 'string': str,
                                           'boolean': bool}[kind], key)
            if 'minimum' in option:
                self.assertGreaterEqual(value, option['minimum'], key)
            conditions = list(option.get('enabledWhen', []))
            for choice, dependencies in option.get('choiceDependencies', {}).items():
                self.assertIn(choice, option['choices'], key)
                conditions.extend(dependencies)
            graph[key] = [c['option'] for c in conditions]
            for condition in conditions:
                target = self.options[condition['option']]
                if target['type'] == 'enum':
                    self.assertIn(condition['equals'], target['choices'], key)
                else:
                    self.assertIs(type(condition['equals']), type(target['default']), key)
        def visit(node, path):
            self.assertNotIn(node, path, 'Cyclic control dependency')
            for parent in graph[node]:
                visit(parent, path + [node])
        for node in graph:
            visit(node, [])

    def test_dar_schema_uses_exact_catalogue_choices(self):
        catalogue = json.loads((ROOT / self.contract['dar']['catalogue']).read_text())
        schema = generator.generate()['properties']['dar']['properties']
        self.assertEqual(set(schema), {field['id'] for field in catalogue['fields']})
        for field in catalogue['fields']:
            self.assertEqual(schema[field['id']]['oneOf'][1]['properties']['code']['enum'],
                             field['allowedCodes'])
            self.assertTrue(set(field['codeConditions']) <= set(field['allowedCodes']))
            self.assertIn(field['resourceType'],
                          {r['resourceType'] for r in self.contract['resources']})

    def test_structural_schema_is_fresh(self):
        self.assertEqual(json.loads((ROOT / 'configuration.schema.json').read_text()),
                         generator.generate())

    def test_every_clinical_reference_has_a_timestamp_policy(self):
        assignment = self.contract['contactAssignment']
        for key in self.options:
            if key.startswith('reference.') and key.endswith('.encounter'):
                resource = key[len('reference.'):-len('.encounter')]
                self.assertIn(resource, assignment['timestamps'])
                self.assertTrue(assignment['timestamps'][resource])
        self.assertNotIn('pendingTimestamps', assignment)
        self.assertEqual(self.contract['openDecisions'], [])


if __name__ == '__main__':
    unittest.main()
