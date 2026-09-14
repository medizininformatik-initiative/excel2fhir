import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from procedure_mapping import select_ops
from synthea_to_excel import prepare
from test_synthea_import import bundle


class ProcedureMappingTest(unittest.TestCase):
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
        self.assertIn('Laparoskop', row[2])
        self.assertIn('Gallengangsrevision', report['clinicalMappings'][0]['reason'])

    def test_heart_lung_machine_and_unspecified_prostatectomy_do_not_gain_wrong_surgery(self):
        for code in ['63697000', '90470006']:
            decision = select_ops({'system': 'http://snomed.info/sct', 'code': code})
            self.assertIsNone(decision['target'])
            self.assertEqual(decision['status'], 'source-preserved')
            self.assertTrue(decision['reason'])
        self.assertIsNone(select_ops({'system': 'http://snomed.info/sct', 'code': '45595009',
                                     'display': 'Wrong procedure'})['target'])

    def test_us_dental_aftercare_uses_international_parent_without_extra_us_coding(self):
        decision = select_ops({'system': 'http://snomed.info/sct', 'code': '456191000124101',
                               'display': 'Postoperative care for dental procedure (regime/therapy)'})
        self.assertEqual(decision['internationalReplacement']['code'], '133899007')
        self.assertIsNone(decision['target'])
        self.assertEqual(decision['evidence']['relation'], 'broader')
