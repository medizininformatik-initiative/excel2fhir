import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from observation_mapping import decision
from synthea_to_excel import prepare
from test_synthea_import import bundle


class ObservationMappingTest(unittest.TestCase):
    def test_logmar_zero_preserved_and_length_ratio_code_replaced(self):
        for source_code, old_loinc, target in [('413077008', '98498-9', '6617-5'),
                                              ('413078003', '98499-7', '6616-7')]:
            resource = {'resourceType': 'Observation', 'id': 'vision', 'status': 'final',
                'subject': {'reference': 'urn:uuid:p'}, 'encounter': {'reference': 'urn:uuid:e'},
                'category': [{'coding': [{'system': 'http://terminology.hl7.org/CodeSystem/observation-category', 'code': 'exam'}]}],
                'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': source_code},
                                    {'system': 'http://loinc.org', 'code': old_loinc}]},
                'valueQuantity': {'value': 0, 'code': '{logmar}', 'unit': '{logmar}', 'system': 'http://unitsofmeasure.org'}}
            source = bundle(); source['entry'].append({'resource': resource}); before = copy.deepcopy(source)
            rows, report = prepare(source)
            self.assertEqual(source, before)
            row = rows['Klinische Dokumentation'][0]
            self.assertEqual(row[3:7], [target, 'LOINC', source_code, 'SNOMED CT (Version nicht angegeben)'])
            self.assertEqual(row[7], '0')
            self.assertFalse(report['observationMappings'][0]['valueChange'])
            resource['valueQuantity']['code'] = '1'
            self.assertIsNone(decision(resource))
