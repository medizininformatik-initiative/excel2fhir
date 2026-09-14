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
            'J20.9', 'ICD-10-GM 2026', '10509002', 'SNOMED CT (Version nicht angegeben)'])
        self.assertEqual(rows['Diagnose'][0][8], '2020-01')
        self.assertEqual(report['diagnosisMappings'][0]['status'], 'approximate')
        self.assertEqual(len(rows['Diagnose']), 1)

    def test_existing_target_wins_even_when_different_from_approximation(self):
        source = self.source()
        source['entry'][2]['resource']['code']['coding'].append({
            'system': ICD10GM, 'version': '2025', 'code': 'J20.8'})
        rows, report = prepare(source)
        self.assertEqual(rows['Diagnose'][0][3:5], ['J20.8', 'ICD-10-GM 2025'])
        self.assertEqual(report['diagnosisMappings'][0]['status'], 'source-preserved')

    def test_no_guess_from_conflicting_display_unknown_code_or_explicit_version(self):
        condition = self.source()['entry'][2]['resource']
        for field, value in [('display', 'Chronic bronchitis'), ('code', 'unknown'), ('version', 'different')]:
            changed = copy.deepcopy(condition)
            changed['code']['coding'][0][field] = value
            self.assertIsNone(map_diagnosis(changed)['target'])
        condition['code']['text'] = condition['code']['coding'][0].pop('display')
        self.assertEqual(map_diagnosis(condition)['target']['code'], 'J20.9')

    def test_neutral_facts_and_tasks_are_not_fabricated_diseases(self):
        for code, display in [('160903007', 'Full-time employment (finding)'),
                              ('314529007', 'Medication review due (situation)')]:
            condition = {'code': {'coding': [{'system': SNOMED, 'code': code, 'display': display}]}}
            self.assertIsNone(map_diagnosis(condition)['target'])

    def test_missing_details_use_fixed_synthetic_defaults(self):
        cases = [('19169002', 'Miscarriage in first trimester (disorder)', 'O03.9'),
                 ('85116003', 'Miscarriage in second trimester (disorder)', 'O03.9'),
                 ('444814009', 'Viral sinusitis (disorder)', 'J01.9'),
                 ('206523001', 'Meconium ileus (disorder)', 'E84.1'),
                 ('79619009', 'Mitral valve stenosis (disorder)', 'I05.0')]
        for code, display, expected in cases:
            source = self.source()
            source['entry'][2]['resource']['code']['coding'][0].update(code=code, display=display)
            before = copy.deepcopy(source)
            rows, report = prepare(source)
            self.assertEqual(rows['Diagnose'][0][3], expected)
            self.assertEqual(source, before)
            self.assertEqual(prepare(source), (rows, report))
            self.assertTrue(report['diagnosisMappings'][0]['reason'])

    def test_suspicion_uses_table_status_without_overwriting_refutation_or_existing_gm(self):
        for code, display, target in [
                ('162573006', 'Suspected lung cancer (situation)', 'C34.9'),
                ('315268008', 'Suspected prostate cancer (situation)', 'C61'),
                ('840544004', 'Suspected disease caused by Severe acute respiratory coronavirus 2 (situation)', 'B34.2')]:
            source = self.source()
            condition = source['entry'][2]['resource']
            condition['code']['coding'][0].update(code=code, display=display)
            for status in [None, 'confirmed', 'provisional', 'refuted', 'entered-in-error']:
                condition.pop('verificationStatus', None)
                if status:
                    condition['verificationStatus'] = {'coding': [{
                        'system': 'http://terminology.hl7.org/CodeSystem/condition-ver-status', 'code': status}]}
                before = copy.deepcopy(source)
                rows, report = prepare(source)
                self.assertEqual(source, before)
                self.assertEqual(rows['Diagnose'][0][3], target)
                changed = report['diagnosisMappings'][0].get('verificationStatusChange')
                self.assertEqual(bool(changed), status in [None, 'confirmed'])
                if changed:
                    self.assertEqual(rows['Diagnose'][0][11], 'Vorläufig')
                    self.assertEqual(changed['from'], condition.get('verificationStatus'))
            condition['code']['coding'].append({'system': ICD10GM, 'version': '2026', 'code': target})
            self.assertNotIn('verificationStatusChange', map_diagnosis(condition))

    def test_only_production_display_variants_are_accepted(self):
        for display in ['Sprain of ankle (disorder)', '  SPRAIN  of ankle (disorder)  ']:
            condition = {'code': {'coding': [{'system': SNOMED, 'code': '44465007', 'display': display}]}}
            self.assertEqual(map_diagnosis(condition)['target']['code'], 'S93.40')
        # This misleading label occurred elsewhere in the broad inventory, not
        # in the ConditionOnset state that actually emits this diabetes code.
        wrong = {'code': {'coding': [{'system': SNOMED, 'code': '427089005', 'display': 'Male Infertility'}]}}
        self.assertEqual(map_diagnosis(wrong)['status'], 'not-assessed')
        placeholder = {'code': {'coding': [{'system': SNOMED, 'code': '1234', 'display': 'SNOMED Code'}]}}
        self.assertEqual(map_diagnosis(placeholder)['status'], 'not-assessed')

    def test_primary_secondary_and_historical_disease_remain_distinct(self):
        cases = [('93761005', 'Primary malignant neoplasm of colon (disorder)', 'C18.9'),
                 ('94260004', 'Metastatic malignant neoplasm to colon (disorder)', 'C78.5'),
                 ('428251008', 'History of appendectomy (situation)', 'Z90.4'),
                 ('74400008', 'Appendicitis (disorder)', 'K37')]
        for code, display, expected in cases:
            condition = {'code': {'coding': [{'system': SNOMED, 'code': code, 'display': display}]}}
            self.assertEqual(map_diagnosis(condition)['target']['code'], expected)

    def test_roundtrip_checks_addition_and_does_not_trust_modified_report(self):
        source = self.source()
        _, report = prepare(source)
        target = copy.deepcopy(source)
        target['entry'] = target['entry'][:3]
        target['entry'][0]['resource']['name'] = [report['demographics']['name']]
        target['entry'][0]['resource']['address'] = [dict(report['demographics']['address'], state='DE-NW')]
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
        condition['code']['coding'].insert(0, copy.deepcopy(report['diagnosisMappings'][0]['target']))
        self.assertEqual(check(source, target, report)['additionalIcd10GmCodings'], 1)
        condition['code']['coding'][1]['code'] = 'J20.8'
        report['diagnosisMappings'][0]['target']['code'] = 'J20.8'
        with self.assertRaises(AssertionError):
            check(source, target, report)

    def test_roundtrip_requires_reported_suspicion_status(self):
        source = self.source()
        condition = source['entry'][2]['resource']
        condition['code']['coding'][0].update(code='162573006', display='Suspected lung cancer (situation)')
        condition['verificationStatus'] = {'coding': [{
            'system': 'http://terminology.hl7.org/CodeSystem/condition-ver-status', 'code': 'confirmed'}]}
        _, report = prepare(source)
        target = copy.deepcopy(source)
        target['entry'] = target['entry'][:3]
        target['entry'][0]['resource']['name'] = [report['demographics']['name']]
        target['entry'][0]['resource']['address'] = [dict(report['demographics']['address'], state='DE-NW')]
        encounter = target['entry'][1]['resource']
        encounter['id'] = 'p-E-1'
        encounter['class']['code'] = 'AMB'
        encounter['extension'] = [{'url': 'http://fhir.de/StructureDefinition/Aufnahmegrund',
            'extension': [{'url': 'VierteStelle', 'valueCoding': {
                'system': 'http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle', 'code': '7'}}]}]
        converted = target['entry'][2]['resource']
        converted['encounter']['reference'] = 'Encounter/p-E-1'
        converted['code']['coding'].insert(0, copy.deepcopy(report['diagnosisMappings'][0]['target']))
        with self.assertRaises(AssertionError):
            check(source, target, report)
        converted['verificationStatus']['coding'][0]['code'] = 'provisional'
        self.assertEqual(check(source, target, report)['verificationStatusChanges'], 1)
        report['diagnosisMappings'][0]['verificationStatusChange']['to'] = 'confirmed'
        with self.assertRaises(AssertionError):
            check(source, target, report)


if __name__ == '__main__':
    unittest.main()
