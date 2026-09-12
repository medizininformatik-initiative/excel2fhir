import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from diagnosis_mapping import ICD10GM, SNOMED, map_diagnosis
from synthea_to_excel import prepare
from check_synthea_roundtrip import check
from test_synthea_import import bundle


class MappingTest(unittest.TestCase):
    def source(self):
        source = bundle()
        source['entry'][2]['resource']['code']['coding'] = [{
            'system': SNOMED, 'code': '10509002', 'display': 'Acute bronchitis (disorder)'}]
        return source

    def test_import_adds_one_coding_without_changing_source_or_times(self):
        source = self.source()
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        self.assertEqual(rows['Diagnose'][0][3:7], [
            '10509002', 'SNOMED CT (Version nicht angegeben)', 'J20.9', 'ICD-10-GM 2026'])
        self.assertEqual(rows['Diagnose'][0][8], '2020-01')
        self.assertEqual(report['diagnosisMappings'][0]['status'], 'approximate')
        self.assertEqual(len(rows['Diagnose']), 1)

    def test_existing_target_wins_even_when_different_from_approximation(self):
        source = self.source()
        source['entry'][2]['resource']['code']['coding'].append({
            'system': ICD10GM, 'version': '2025', 'code': 'J20.8'})
        rows, report = prepare(source)
        self.assertEqual(rows['Diagnose'][0][5:7], ['J20.8', 'ICD-10-GM 2025'])
        self.assertEqual(report['diagnosisMappings'][0]['status'], 'source-preserved')

    def test_no_guess_from_conflicting_display_unknown_code_or_explicit_version(self):
        condition = self.source()['entry'][2]['resource']
        for field, value in [('display', 'Chronic bronchitis'), ('code', 'unknown'), ('version', 'different')]:
            changed = copy.deepcopy(condition)
            changed['code']['coding'][0][field] = value
            self.assertIsNone(map_diagnosis(changed)['target'])
        condition['code']['text'] = condition['code']['coding'][0].pop('display')
        self.assertEqual(map_diagnosis(condition)['target']['code'], 'J20.9')

    def test_social_facts_and_unspecified_course_are_not_fabricated_diseases(self):
        for code, display in [('160903007', 'Full-time employment (finding)'),
                              ('444814009', 'Viral sinusitis (disorder)')]:
            condition = {'code': {'coding': [{'system': SNOMED, 'code': code, 'display': display}]}}
            self.assertIsNone(map_diagnosis(condition)['target'])

    def test_roundtrip_checks_addition_and_does_not_trust_modified_report(self):
        source = self.source()
        _, report = prepare(source)
        target = copy.deepcopy(source)
        target['entry'] = target['entry'][:3]
        encounter = target['entry'][1]['resource']
        encounter['id'] = 'p-E-1'
        encounter['class']['code'] = 'AMB'
        encounter['extension'] = [{'url': 'http://fhir.de/StructureDefinition/Aufnahmegrund',
            'extension': [{'url': 'VierteStelle', 'valueCoding': {
                'system': 'http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle', 'code': '7'}}]}]
        condition = target['entry'][2]['resource']
        condition['encounter']['reference'] = 'Encounter/p-E-1'
        with self.assertRaises(AssertionError):
            check(source, target, report)
        condition['code']['coding'].append(copy.deepcopy(report['diagnosisMappings'][0]['target']))
        self.assertEqual(check(source, target, report)['additionalIcd10GmCodings'], 1)
        condition['code']['coding'][1]['code'] = 'J20.8'
        report['diagnosisMappings'][0]['target']['code'] = 'J20.8'
        with self.assertRaises(AssertionError):
            check(source, target, report)


if __name__ == '__main__':
    unittest.main()
