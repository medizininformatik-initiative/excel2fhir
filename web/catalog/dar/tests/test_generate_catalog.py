"""Tests for profile inheritance and datatype resolution used by the catalogue."""
import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "generator"))
from generate_catalog import elements, resolve


class ProfileResolutionTest(unittest.TestCase):
    def test_differential_preserves_inherited_cardinality_and_type(self):
        definitions = {
            'base': {'snapshot': {'element': [{'id': 'Patient.gender', 'min': 0, 'max': '1', 'type': [{'code': 'code'}]}]}},
            'child': {'baseDefinition': 'base', 'differential': {'element': [{'id': 'Patient.gender', 'min': 1}]}}
        }
        field = resolve(definitions, 'child', 'Patient.gender')
        self.assertEqual((1, '1', [{'code': 'code'}]), (field['min'], field['max'], field['type']))

    def test_resolves_descendant_through_a_datatype_definition(self):
        definitions = {
            'resource': {'snapshot': {'element': [{'id': 'Patient.name', 'type': [{'code': 'HumanName'}]}]}},
            'http://hl7.org/fhir/StructureDefinition/HumanName': {
                'snapshot': {'element': [{'id': 'HumanName.family', 'type': [{'code': 'string'}]}]}}
        }
        self.assertEqual('HumanName.family', resolve(definitions, 'resource', 'Patient.name.family')['id'])
        with self.assertRaises(ValueError):
            resolve(definitions, 'resource', 'Patient.name.invented')

    def test_rejects_inheritance_cycle(self):
        with self.assertRaisesRegex(ValueError, 'Cyclic'):
            elements({'a': {'baseDefinition': 'b'}, 'b': {'baseDefinition': 'a'}}, 'a')


if __name__ == '__main__':
    unittest.main()
