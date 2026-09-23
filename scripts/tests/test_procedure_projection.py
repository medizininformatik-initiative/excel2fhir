import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_procedures import audit_procedures
from procedure_projection import project
from synthea_to_excel import prepare
from test_synthea_import import bundle

SNOMED = 'http://snomed.info/sct'
RX = 'http://www.nlm.nih.gov/research/umls/rxnorm'


class ProcedureProjectionTest(unittest.TestCase):
    def source(self):
        source = bundle()
        source['entry'] = source['entry'][:3]
        return source

    def procedure(self, source, identifier, code, day=1):
        r = {'resourceType': 'Procedure', 'id': identifier, 'status': 'completed',
             'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
             'code': {'coding': [{'system': SNOMED, 'code': code}]},
             'performedPeriod': {'start': f'2026-09-{day:02}T08:00:00+02:00',
                                 'end': f'2026-09-{day:02}T10:00:00+02:00'}}
        source['entry'].append({'resource': r})
        return r

    def medication(self, source, identifier, day, code, display):
        source['entry'].append({'resource': {'resourceType': 'MedicationAdministration', 'id': identifier,
            'status': 'completed', 'subject': {'reference': 'urn:uuid:p'}, 'context': {'reference': 'urn:uuid:e'},
            'effectiveDateTime': f'2026-09-{day:02}T08:00:00+02:00',
            'medicationCodeableConcept': {'coding': [{'system': RX, 'code': code, 'display': display}]}}})

    def test_ct_regions_have_individual_labels_without_combined_snomed(self):
        source = self.source()
        self.procedure(source, 'ct', '418023006')
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        self.assertEqual([r[3] for r in rows['Prozedur']], ['3-202', '3-207', '3-206'])
        self.assertEqual([r[2] for r in rows['Prozedur']], ['Native Computertomographie des Thorax',
            'Native Computertomographie des Abdomens', 'Native Computertomographie des Beckens'])
        self.assertTrue(all(r[6:8] == ['', ''] for r in rows['Prozedur']))
        self.assertEqual([o['row'] for o in report['clinicalMappings'][0]['outputs']], [2, 3, 4])

    def test_chemo_counts_substances_once_and_groups_days_but_not_long_breaks(self):
        source = self.source()
        for day in (2, 1, 5):
            self.procedure(source, f'p{day}', '703423002', day)
            for suffix in ('a', 'b'):
                self.medication(source, f'cis-{day}-{suffix}', day, '1736854', 'Cisplatin 50 MG Injection')
            self.medication(source, f'eto-{day}', day, '226719', 'etoposide 100 MG Injection [Etopophos]')
        decisions = project(source['entry'])
        outputs = [o for d in decisions.values() for o in d['outputs']]
        self.assertEqual(len(outputs), 5)  # Three fractions and two chemotherapy blocks.
        self.assertEqual([o['codings'][0]['code'] for o in decisions['p1']['outputs']], ['8-522.91', '8-543.22'])
        self.assertEqual(decisions['p1']['outputs'][1]['end'], '2026-09-02T10:00:00+02:00')
        self.assertEqual(decisions['p1']['chemotherapyBlock']['sourceIds'], ['p1', 'p2'])
        self.assertEqual(decisions['p5']['outputs'][1]['codings'][0]['code'], '8-542.12')
        self.assertTrue(all(len(o['codings']) == 1 for o in outputs))

    def test_geriatrics_requires_age_and_preserves_unknown_or_younger_source(self):
        source = self.source()
        self.procedure(source, 'assessment', '710824005')
        self.assertIsNone(project(source['entry'])['assessment']['target'])
        source['entry'][0]['resource']['birthDate'] = '1961-09-02'
        self.assertIsNone(project(source['entry'])['assessment']['target'])
        source['entry'][0]['resource']['birthDate'] = '1961-09-01'
        self.assertEqual(project(source['entry'])['assessment']['target']['code'], '1-770')

    def test_prolonged_fluorouracil_regimen_is_not_guessed_as_high_complexity(self):
        source = self.source()
        for day in range(1, 6):
            self.procedure(source, f'p{day}', '703423002', day)
            self.medication(source, f'fu{day}', day, '1791701', '10 ML Fluorouracil 50 MG/ML Injection')
        decisions = project(source['entry'])
        self.assertTrue(all(d['status'] == 'source-preserved' for d in decisions.values()))
        self.assertTrue(all(d['outputs'][0]['codings'][0]['code'] == '703423002' for d in decisions.values()))

    def test_missing_medication_is_an_explicit_assumption_not_a_source_fact(self):
        source = self.source()
        self.procedure(source, 'chemo', '367336001')
        d = project(source['entry'])['chemo']
        self.assertEqual(d['outputs'][0]['codings'][0]['code'], '8-542.11')
        self.assertEqual(d['chemotherapyBlock']['administrationIds'], [])
        self.assertIn('synthetisch angenommen', d['chemotherapyBlock']['assumptions'][-1])

    def test_unknown_version_or_display_never_triggers_split(self):
        source = self.source()
        r = self.procedure(source, 'ct', '418023006')
        r['code']['coding'][0]['display'] = 'Unrelated procedure'
        self.assertEqual(len(project(source['entry'])['ct']['outputs']), 1)
        r['code']['coding'][0].pop('display')
        r['code']['coding'][0]['version'] = 'unexpected'
        self.assertEqual(len(project(source['entry'])['ct']['outputs']), 1)

    def test_independent_audit_rejects_missing_region_wrong_label_and_extra_code(self):
        source = self.source()
        self.procedure(source, 'ct', '418023006')
        rows, report = prepare(source)
        headers = ['Patient-ID', 'Fall-Nr', 'Prozedurentext', 'Prozedurencode', 'Durchführungsbeginn',
                   'Codesystem', 'Zusatzcode', 'Zusatzcodesystem', 'Ende', 'Status', 'Kategorie']
        excel = [dict(zip(headers, r)) for r in rows['Prozedur']]
        target = {'entry': [{'resource': {'resourceType': 'Procedure', 'id': f'p{i}',
                    'code': {'coding': o['codings'], 'text': o['label']}, 'status': o['status'],
                    'performedPeriod': {'start': o['start'], 'end': o['end']}}}
                  for i, o in enumerate(report['clinicalMappings'][0]['outputs'])]}
        self.assertEqual(audit_procedures(source, target, excel, report)['opsProcedures'], 3)
        for mutation in ('missing', 'label', 'extra'):
            changed = copy.deepcopy(target)
            if mutation == 'missing': changed['entry'].pop()
            elif mutation == 'label': changed['entry'][0]['resource']['code']['text'] = 'Wrong region'
            else: changed['entry'][0]['resource']['code']['coding'].append({'system': SNOMED, 'code': '418023006'})
            with self.assertRaises(AssertionError): audit_procedures(source, changed, excel, report)
