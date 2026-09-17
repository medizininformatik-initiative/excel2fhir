import copy
import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from procedure_mapping import DATA, ENTRIES, PATH, select_ops
from synthea_to_excel import prepare
from test_synthea_import import bundle


class ProcedureMappingTest(unittest.TestCase):
    def test_us_extension_procedures_have_explicit_international_or_exclusion_decisions(self):
        for code, entry in ENTRIES.items():
            if code[-3:-1] == '10' and code[-10:-3] in ('1000119', '1000124'):
                self.assertTrue(entry.get('internationalReplacement') or entry['status'] == 'excluded', code)
        self.assertEqual(select_ops({'system': 'http://snomed.info/sct', 'code': '428211000124100'})[
            'internationalReplacement']['code'], '713106006')
        self.assertEqual(select_ops({'system': 'http://snomed.info/sct', 'code': '371361000119107'})[
            'status'], 'excluded')

    def test_laparoscopy_does_not_imply_absence_of_bile_duct_revision(self):
        source = bundle()
        procedure = {'resourceType': 'Procedure', 'id': 'surgery', 'status': 'completed',
                     'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
                     'performedPeriod': {'start': '2020-01-02T08:00:00Z', 'end': '2020-01-02T09:00:00Z'},
                     'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': '45595009',
                                          'display': 'Laparoscopic cholecystectomy (procedure)'}]}}
        source['entry'].append({'resource': procedure})
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        row = rows['Prozedur'][0]
        self.assertEqual((row[3], row[5], row[6]), ('5-511.y', 'OPS 2026', '45595009'))
        self.assertEqual(row[4], procedure['performedPeriod']['start'])
        self.assertEqual(row[8], procedure['performedPeriod']['end'])
        self.assertEqual('Cholezystektomie: N.n.bez.', row[2])
        self.assertIn('Gallengangsrevision', report['clinicalMappings'][0]['reason'])

    def test_operating_steps_and_ambulatory_therapy_do_not_gain_wrong_ops(self):
        for code in ['52765003', '166001', '228557008']:
            decision = select_ops({'system': 'http://snomed.info/sct', 'code': code})
            self.assertIsNone(decision['target'])
            self.assertEqual(decision['status'], 'source-preserved')
            self.assertTrue(decision['reason'])
        self.assertIsNone(select_ops({'system': 'http://snomed.info/sct', 'code': '45595009',
                                     'display': 'Wrong procedure'})['target'])

    def test_transplant_and_cancer_context_survive_synthetic_choices(self):
        for code, expected in [('234336002', '8-805.40'), ('58390007', '5-411.40'),
                               ('58776007', '5-411.00'), ('90470006', '5-604.41')]:
            self.assertEqual(select_ops({'system': 'http://snomed.info/sct', 'code': code})['target']['code'], expected)

    def test_nonprocedural_source_concepts_are_not_exported_as_procedure_codes(self):
        tags = r'\((finding|situation|event|physical object|qualifier value|observable entity|disorder)\)'
        for code, entry in ENTRIES.items():
            if any(re.search(tags, label) for label in entry['sourceDisplays']):
                decision = select_ops({'system': 'http://snomed.info/sct', 'code': code})
                self.assertNotEqual(decision['internationalReplacement']['code'], code)
                self.assertEqual(decision['evidence']['relation'], 'synthetic-contextual-replacement')

    def test_all_registered_procedures_have_decisions_and_survive_import(self):
        registry = json.loads(PATH.with_name('synthea-source-code-registry.json').read_text())
        expected = {e['code'] for e in registry['entries']
                    if e['system'] == 'http://snomed.info/sct' and 'state-code:Procedure' in e['usages']}
        self.assertEqual(set(ENTRIES), expected | {'418023006'})
        self.assertEqual(len(DATA['entries']), len(ENTRIES))
        source = bundle()
        source['entry'] = source['entry'][:3]
        for code, entry in ENTRIES.items():
            self.assertNotEqual(entry['status'], 'not-assessed')
            self.assertTrue(entry['reason'])
            self.assertTrue(entry['sourceFiles'])
            source['entry'].append({'resource': {
                'resourceType': 'Procedure', 'id': 'procedure-' + code, 'status': 'completed',
                'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
                'performedPeriod': {'start': '2026-09-01T08:00:00+02:00', 'end': '2026-09-01T08:01:00+02:00'},
                'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': code,
                                      'display': entry['sourceDisplays'][0]}]}}})
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        outputs = [o for m in report['clinicalMappings'] for o in m.get('outputs', [])]
        self.assertEqual(len(rows['Prozedur']), len(outputs))
        self.assertEqual(len(report['clinicalMappings']), len(ENTRIES))
        self.assertEqual({l['id'] for l in report['losses'] if l['resourceType'] == 'Procedure' and l['path'] == '$'},
                         {'procedure-' + code for code, e in ENTRIES.items() if e['status'] == 'excluded'})
        for row, projected in zip(rows['Prozedur'], outputs):
            self.assertEqual(row[3], projected['codings'][0]['code'])
            self.assertEqual(row[4], '2026-09-01T08:00:00+02:00')
            self.assertEqual(row[8:10], ['2026-09-01T08:01:00+02:00', 'completed'])
            if row[5] == 'OPS 2026':
                self.assertEqual(row[2], projected['codings'][0]['display'])

    def test_us_dental_aftercare_uses_international_parent_without_extra_us_coding(self):
        decision = select_ops({'system': 'http://snomed.info/sct', 'code': '456191000124101',
                               'display': 'Postoperative care for dental procedure (regime/therapy)'})
        self.assertEqual(decision['internationalReplacement']['code'], '133899007')
        self.assertIsNone(decision['target'])
        self.assertEqual(decision['evidence']['relation'], 'broader')
