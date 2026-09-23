import copy
import json
from pathlib import Path
import random
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from synthea_departments import select_department, ICD10GM, DEPARTMENTS
from synthea_movements import enrich


class DepartmentTest(unittest.TestCase):
    def setUp(self):
        self.patient = {'resourceType': 'Patient', 'id': 'p', 'birthDate': '1965-09-15', 'gender': 'female'}
        self.encounter = {'resourceType': 'Encounter', 'id': 'e', 'class': {'code': 'IMP'},
                          'period': {'start': '1980-04-27T03:09:18+02:00', 'end': '1980-06-12T03:24:18+02:00'}}
        self.resources = [self.patient, self.encounter]
        self.refs = {'urn:uuid:e': 'e'}

    def condition(self, code, onset='1980-04-27T03:09:18+02:00', linked=True):
        r = {'resourceType': 'Condition', 'id': str(len(self.resources)),
             'code': {'coding': [{'system': ICD10GM, 'code': code}]}, 'onsetDateTime': onset}
        if linked:
            r['encounter'] = {'reference': 'urn:uuid:e'}
        self.resources.append(r)
        return r

    def choose(self, seed=1, operations=()):
        return select_department(self.resources, self.encounter, self.refs, operations, random.Random(seed))

    def specialty(self, code):
        self.encounter['serviceType'] = {'coding': [{'system': 'http://nucc.org/provider-taxonomy', 'code': code}]}

    def test_child_injury_uses_age_at_admission_and_stable_department(self):
        self.condition('S13.4')
        department, report = self.choose()
        self.assertEqual('Kinderchirurgie', department)
        self.assertEqual(14, report['ageAtAdmission'])
        self.assertFalse(report['fallback'])
        source = {'entry': [{'fullUrl': 'urn:uuid:' + r['id'], 'resource': r} for r in self.resources]}
        before = copy.deepcopy(source)
        rows = [['p', '1', '', '', 'stationaer', '', '', '', '', '']]
        output, movement = enrich(source, rows, {'e': '1'})
        stays = output[1:]
        self.assertTrue(stays)
        self.assertEqual({'Kinderchirurgie'}, {r[5] for r in stays})
        self.assertEqual({'Station KC1'}, {r[6] for r in stays})
        self.assertEqual({'Normalstationär'}, {r[10] for r in stays})
        self.assertEqual((output, movement), enrich(source, rows, {'e': '1'}))
        self.assertEqual(before, source)

    def test_unknown_codes_fallback_for_child_adult_and_missing_age(self):
        self.condition('UNKNOWN')
        self.assertEqual('Pädiatrie', self.choose()[0])
        self.patient['birthDate'] = '1940-01-01'
        department, report = self.choose()
        self.assertEqual('Innere Medizin', department)
        self.assertTrue(report['fallback'])
        self.assertEqual('UNKNOWN', report['unmappedCodes'][0]['code'])
        self.patient.pop('birthDate')
        self.assertEqual('Innere Medizin', self.choose()[0])

    def test_temporally_incompatible_linked_diagnosis_is_reported(self):
        self.condition('N30.0', '1980-09-17T03:09:18+02:00')
        self.assertTrue(self.choose()[1]['fallback'])
        self.assertEqual(1, len(self.choose()[1]['ignoredEvidence']))
        self.resources[-1]['onsetDateTime'] = '1979-01-01T00:00:00Z'
        self.resources[-1]['abatementDateTime'] = '1980-01-01T00:00:00Z'
        self.assertTrue(self.choose()[1]['fallback'])

    def test_acute_diagnosis_outweighs_chronic_incidental_disease(self):
        self.patient['birthDate'] = '1940-01-01'
        self.condition('H65.9', '1970-01-01T00:00:00Z', linked=False)
        self.condition('I63.9')
        for seed in range(20):
            self.assertEqual('Neurologie', self.choose(seed)[0])

    def test_plausible_candidates_are_reproducibly_sampled(self):
        self.patient['birthDate'] = '1940-01-01'
        self.condition('S13.4')
        self.assertEqual(self.choose(3), self.choose(3))
        self.assertEqual({'Unfallchirurgie', 'Orthopädie'}, {self.choose(s)[0] for s in range(40)})

    def test_operation_beats_incidental_diagnosis_and_adapts_child_department(self):
        self.condition('J20.9')
        operations = [('1980-04-28', 'op', 'Herzchirurgie')]
        department, report = self.choose(operations=operations)
        self.assertEqual('Kinderchirurgie', department)
        self.assertEqual({'op': 'Kinderchirurgie'}, report['operationDepartments'])

    def test_geriatric_source_requires_age_and_is_not_inferred_from_age_alone(self):
        self.specialty('207RG0300X')
        department, report = self.choose()
        self.assertEqual('Pädiatrie', department)
        self.assertTrue(report['rejected'])
        self.patient['birthDate'] = '1900-01-01'
        self.assertEqual('Geriatrie', self.choose()[0])
        self.encounter.pop('serviceType')
        self.assertEqual('Innere Medizin', self.choose()[0])

    def test_gynecology_requires_sex_or_relevant_clinical_evidence(self):
        self.patient.update(birthDate='1940-01-01', gender='male')
        self.specialty('207V00000X')
        self.assertEqual('Innere Medizin', self.choose()[0])
        self.condition('O80')
        self.assertEqual('Frauenheilkunde und Geburtshilfe', self.choose()[0])

    def test_referenced_role_and_generic_practice(self):
        self.patient['birthDate'] = '1940-01-01'
        self.resources.append({'resourceType': 'PractitionerRole', 'id': 'role', 'specialty': [
            {'coding': [{'system': 'http://nucc.org/provider-taxonomy', 'code': '207RC0000X'}]}]})
        self.refs['urn:uuid:role'] = 'role'
        self.encounter['participant'] = [{'individual': {'reference': 'urn:uuid:role'}}]
        self.assertEqual('Kardiologie', self.choose()[0])
        self.encounter.pop('participant')
        self.specialty('208D00000X')
        self.condition('I63.9')
        self.assertEqual('Neurologie', self.choose()[0])

    def test_existing_snomed_mapping_and_unknown_system(self):
        self.patient['birthDate'] = '1940-01-01'
        c = self.condition('ignored')
        c['code'] = {'coding': [{'system': 'http://snomed.info/sct', 'code': '10509002',
                                'display': 'Acute bronchitis (disorder)'}]}
        self.assertEqual('Pneumologie', self.choose()[0])
        c['code']['coding'][0]['system'] = 'http://example.org/unknown'
        self.assertTrue(self.choose()[1]['fallback'])

    def test_refuted_condition_does_not_determine_department(self):
        c = self.condition('S13.4')
        c['verificationStatus'] = {'coding': [{'code': 'refuted'}]}
        self.assertTrue(self.choose()[1]['fallback'])

    def test_all_departments_have_converter_codes(self):
        path = Path(__file__).resolve().parents[2] / 'src/main/resources/EncounterLevel2_Department.map'
        names = {line.split()[0].replace('\\u0020', ' ') for line in path.read_text().splitlines()
                 if line.strip() and not line.lstrip().startswith('#')}
        self.assertLessEqual(DEPARTMENTS.keys(), names)

    def test_specific_specialty_precedes_operation(self):
        self.patient['birthDate'] = '1940-01-01'
        self.specialty('207RC0000X')
        for seed in range(20):
            self.assertEqual('Kardiologie', self.choose(seed, [('1980-04-28', 'op', 'Herzchirurgie')])[0])

    def test_malformed_icd_and_common_digestive_diagnoses(self):
        self.patient['birthDate'] = '1940-01-01'
        c = self.condition('ABC')
        self.assertTrue(self.choose()[1]['fallback'])
        c['code']['coding'][0]['code'] = 'K29.7'
        self.assertEqual('Gastroenterologie', self.choose()[0])
        c['code']['coding'][0]['code'] = 'K35.8'
        self.assertEqual('Allgemeine Chirurgie', self.choose()[0])

    def test_intensive_care_requires_specific_source_evidence(self):
        self.patient['birthDate'] = '1940-01-01'
        self.specialty('207RC0200X')
        self.assertEqual('Intensivmedizin', self.choose()[0])
        self.encounter.pop('serviceType')
        self.assertEqual('Innere Medizin', self.choose()[0])


if __name__ == '__main__':
    unittest.main()
