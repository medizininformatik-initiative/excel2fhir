import copy
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_time_shift import shift_date, shift_days, checked_original_times
from check_encounter_policies import checked_original_periods
from check_medication_transformations import transformed_medications, absent
from check_medication_selection import check_medication_references
from check_dar import restore_path, catalogue, checked_original_dar
from converter_options import resolve_config


class TimeShiftChecks(unittest.TestCase):
    def test_calendar_dates_precision_offsets_and_iteration(self):
        self.assertEqual('2000-03-01', shift_date('2000-02-29', 1))
        self.assertEqual('2025-12-31', shift_date('2026-01-01', -1))
        self.assertEqual('2026-03-30T12:13:14.120+01:00', shift_date('2026-03-28T12:13:14.120+01:00', 2))
        options = {'TIME_SHIFT_ENABLED': 'true', 'TIME_SHIFT_BASE_DAYS': '10', 'TIME_SHIFT_ITERATION_DAYS': '-5'}
        self.assertEqual([10, 5, 0, -5], [shift_days({'converterOptions': options, 'iteration': i}) for i in range(4)])
        options['TIME_SHIFT_ENABLED'] = 'false'
        self.assertEqual(0, shift_days({'converterOptions': options, 'iteration': 10}))

    def test_wrong_day_offset_or_precision_is_rejected_and_target_preserved(self):
        original = '2026-03-28T12:13:14.120+01:00'
        report = {'converterOptions': {'TIME_SHIFT_ENABLED': 'true', 'TIME_SHIFT_BASE_DAYS': '2'}}
        target = {'entry': [{'resource': {'resourceType': 'Observation', 'id': 'o', 'effectiveDateTime': '2026-03-30T12:13:14.120+01:00', 'meta': {'lastUpdated': original}}}]}
        with patch('check_time_shift.temporal_expectations', return_value={'o': {'effectiveDateTime': original}}):
            result = checked_original_times({}, target, report)
            self.assertEqual(original, result['entry'][0]['resource']['effectiveDateTime'])
            self.assertEqual('2026-03-30T12:13:14.120+01:00', target['entry'][0]['resource']['effectiveDateTime'])
            self.assertEqual({'lastUpdated': original}, result['entry'][0]['resource']['meta'])
            for value in [original, '2026-03-30T12:13:14.120+02:00', '2026-03-30T12:13:14.12+01:00']:
                target['entry'][0]['resource']['effectiveDateTime'] = value
                with self.assertRaises(AssertionError): checked_original_times({}, target, report)

    def test_contact_end_policy_uses_shifted_quarter(self):
        source = {'entry': [{'resource': {'resourceType': 'Encounter', 'id': 'stay', 'period': {'start': '2026-03-31T10:00:00+02:00', 'end': '2026-04-02T10:00:00+02:00'}}}]}
        report = {'sourcePatient': 'p', 'encounterNumbers': {'stay': '1'}, 'converterOptions': {'TIME_SHIFT_ENABLED': 'true', 'TIME_SHIFT_BASE_DAYS': '1', 'ENCOUNTER_INPATIENT_END_POLICY': 'quarter-end'}}
        target = {'entry': [{'resource': {'resourceType': 'Encounter', 'id': 'p-E-1', 'class': {'code': 'IMP'}, 'status': 'finished', 'period': {'start': '2026-04-01T10:00:00+02:00', 'end': '2026-06-30T23:59:59+02:00'}}}]}
        result = checked_original_periods(source, target, report)
        self.assertEqual(source['entry'][0]['resource']['period'], result['entry'][0]['resource']['period'])
        target['entry'][0]['resource']['period']['end'] = '2026-04-01T23:59:59+02:00'
        with self.assertRaises(AssertionError): checked_original_periods(source, target, report)


class MedicationTransformationChecks(unittest.TestCase):
    def row(self, kind='Verordnung', status='completed'):
        return ['p', '1', kind, 'Test product', '', '', '', '', '', '123', 'SNOMED CT (Version nicht angegeben)', status, 'order',
                '2026-01-01T10:00:00Z', '2026-01-02T10:00:00Z', '', '5', 'mg', '2', '']

    def test_prescription_transformation_does_not_invent_an_administered_event(self):
        for action, kind in [('replace-administration', 'MedicationAdministration'), ('replace-statement', 'MedicationStatement')]:
            options = {'MEDICATION_REQUEST_TREATMENT': action}
            resource, = transformed_medications([self.row()], options, 'p')
            self.assertEqual(kind, resource['resourceType'])
            self.assertNotIn('status', resource)
            self.assertNotIn('effectiveDateTime', resource)
            self.assertNotIn('authoredOn', resource)
            self.assertEqual(kind == 'MedicationStatement', 'dosage' in resource)
            check_medication_references([self.row()], [resource], options, 'p')
            for field, value in [('status', 'completed'), ('effectiveDateTime', '2026-01-02T10:00:00Z')]:
                wrong = copy.deepcopy(resource); wrong[field] = value
                with self.assertRaises(AssertionError): check_medication_references([self.row()], [wrong], options, 'p')

    def test_administration_add_and_replace_preserve_dose_and_effective_time(self):
        for action, count in [('add-statement', 2), ('replace-statement', 1)]:
            result = transformed_medications([self.row('Verabreichung')], {'MEDICATION_ADMINISTRATION_TREATMENT': action}, 'p')
            self.assertEqual(count, len(result))
            statement = result[-1]
            self.assertEqual('completed', statement['status'])
            self.assertEqual('2026-01-02T10:00:00Z', statement['effectiveDateTime'])
            self.assertEqual(5, statement['dosage'][0]['doseAndRate'][0]['doseQuantity']['value'])

    def test_statement_regimen_is_not_an_individual_dose_and_pass_is_not_recursive(self):
        row = self.row('Medikationsaussage', 'active')
        options = {'MEDICATION_STATEMENT_TREATMENT': 'replace-administration', 'MEDICATION_ADMINISTRATION_TREATMENT': 'replace-statement'}
        administration, = transformed_medications([row], options, 'p')
        self.assertEqual('MedicationAdministration', administration['resourceType'])
        self.assertNotIn('dosage', administration)
        self.assertNotIn('dateAsserted', administration)
        self.assertNotIn('status', administration)
        self.assertEqual('2026-01-02T10:00:00Z', administration['effectiveDateTime'])

    def test_request_pass_precedes_event_pass_and_ids_are_deterministic(self):
        options = {'MEDICATION_REQUEST_TREATMENT': 'replace-administration', 'MEDICATION_ADMINISTRATION_TREATMENT': 'replace-statement'}
        rows = [self.row()]
        result = transformed_medications(rows, options, 'p')
        self.assertEqual(result, transformed_medications(rows, options, 'p'))
        self.assertEqual('MedicationStatement', result[0]['resourceType'])
        self.assertNotIn('status', result[0])
        self.assertNotIn('dosage', result[0])


class DarChecks(unittest.TestCase):
    def field(self, identifier):
        return next(f for f in catalogue()[0] if f['id'] == identifier)

    def test_primitive_replacement_requires_exact_reason_and_no_original_value(self):
        field = self.field('Patient.birthDate')
        before = {'birthDate': '2000-02-29'}
        target = {'_birthDate': absent('masked')}
        restore_path(before, target, ['birthDate'], field, 'masked')
        self.assertEqual(before, target)
        for wrong in [{'birthDate': '2000-02-29', '_birthDate': absent('masked')}, {'_birthDate': absent('unknown')}, {}]:
            with self.assertRaises(AssertionError): restore_path(before, wrong, ['birthDate'], field, 'masked')

    def test_arrays_retain_cardinality_and_missing_scalar_is_restored_to_absence(self):
        field = self.field('Patient.name.given')
        before = {'name': [{'given': ['Ada', 'Jane']}]}
        target = {'name': [{'_given': [absent(), absent()]}]}
        restore_path(before, target, ['name', 'given'], field, 'unknown')
        self.assertEqual(before, target)
        with self.assertRaises(AssertionError):
            restore_path(before, {'name': [{'_given': [absent()]}]}, ['name', 'given'], field, 'unknown')
        target = {'_deceasedDateTime': absent()}
        restore_path({}, target, ['deceased[x]'], self.field('Patient.deceased[x]'), 'unknown')
        self.assertEqual({}, target)

    def test_period_dar_clears_all_values_and_rejects_wrong_choice(self):
        before = {'performedPeriod': {'start': '2026-01-01', 'end': '2026-01-02'}}
        field = self.field('Procedure.performed[x]')
        target = {'performedPeriod': absent()}
        restore_path(before, target, ['performed[x]'], field, 'unknown')
        self.assertEqual(before, target)
        for target in [{'performedPeriod': {**absent(), 'start': '2026-01-01'}}, {'_performedDateTime': absent()}]:
            with self.assertRaises(AssertionError): restore_path(before, target, ['performed[x]'], field, 'unknown')

    def test_coding_version_is_restored_without_inventing_repeated_codings(self):
        before = {'code': {'coding': [{'system': 'urn:code', 'code': 'x'}]}}
        target = {'code': {'coding': [{'system': 'urn:code', 'code': 'x', '_version': absent('unsupported')}]}}
        field = self.field('Condition.code.coding.version')
        restore_path(before, target, ['code', 'coding', 'version'], field, 'unsupported')
        self.assertEqual(before, target)
        target = {}
        restore_path({}, target, ['code', 'coding', 'version'], field, 'unknown')
        self.assertEqual({}, target)

    def test_condition_dar_checks_requested_output_independently_of_fhir_validity(self):
        clinical = {'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/condition-clinical', 'code': 'active'}]}
        cases = [
            ({'clinicalStatus': clinical, 'abatementDateTime': '2026-01-01'},
             {'clinicalStatus': absent(), 'abatementDateTime': '2026-01-01'}, 'DAR_CONDITION_CLINICAL_STATUS'),
            ({'clinicalStatus': clinical}, {'clinicalStatus': clinical, '_abatementDateTime': absent()}, 'DAR_CONDITION_ABATEMENT_X'),
            ({'verificationStatus': {'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/condition-ver-status', 'code': 'entered-in-error'}]}},
             {'clinicalStatus': absent(), 'verificationStatus': {'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/condition-ver-status', 'code': 'entered-in-error'}]}}, 'DAR_CONDITION_CLINICAL_STATUS'),
        ]
        for original, actual, option in cases:
            with self.subTest(option=option, original=original):
                before = {'resourceType': 'Condition', 'id': 'c', **original}
                target = {'entry': [{'resource': {'resourceType': 'Condition', 'id': 'c', **actual}}]}
                report = {'converterOptions': {option: 'unknown'}}
                with patch('check_dar.expected_fields', return_value={'c': before}):
                    result = checked_original_dar({}, target, report)
                    self.assertEqual(before, result['entry'][0]['resource'])

    def test_valid_condition_status_dar_without_abatement_is_checked(self):
        before = {'resourceType': 'Condition', 'id': 'c', 'clinicalStatus': {'coding': [
            {'system': 'http://terminology.hl7.org/CodeSystem/condition-clinical', 'code': 'active'}]}}
        target = {'entry': [{'resource': {'resourceType': 'Condition', 'id': 'c', 'clinicalStatus': absent()}}]}
        report = {'converterOptions': {'DAR_CONDITION_CLINICAL_STATUS': 'unknown'}}
        with patch('check_dar.expected_fields', return_value={'c': before}):
            result = checked_original_dar({}, target, report)
            self.assertEqual(before, result['entry'][0]['resource'])
            self.assertEqual(absent(), target['entry'][0]['resource']['clinicalStatus'])

    @unittest.skipUnless((Path(__file__).resolve().parents[2] / 'target/excel2fhir.jar').exists(), 'Build converter JAR')
    def test_bridge_exports_shift_medication_and_dar_and_respects_dependencies(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'test.config'
            path.write_text('CONFIGURATION_VERSION=1\nTIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=10\nTIME_SHIFT_ITERATION_DAYS=-3\nMEDICATION_REQUEST_TREATMENT=replace-statement\nDAR_PATIENT_BIRTH_DATE=masked\n')
            values = resolve_config(path)['values']
            self.assertEqual(('10', '-3', 'replace-statement', 'masked'), tuple(values[k] for k in ('TIME_SHIFT_BASE_DAYS', 'TIME_SHIFT_ITERATION_DAYS', 'MEDICATION_REQUEST_TREATMENT', 'DAR_PATIENT_BIRTH_DATE')))
            path.write_text('CONFIGURATION_VERSION=1\nTIME_SHIFT_BASE_DAYS=10\nMEDICATION_REQUEST_TREATMENT=replace-statement\nMEDICATION_STATEMENT_ENABLED=false\n')
            values = resolve_config(path)['values']
            self.assertEqual('0', values['TIME_SHIFT_BASE_DAYS'])
            self.assertEqual('retain', values['MEDICATION_REQUEST_TREATMENT'])
