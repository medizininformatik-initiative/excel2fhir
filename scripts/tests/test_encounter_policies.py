import copy
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_encounter_policies import expected_end, checked_original_periods, instant


class EncounterPolicyTests(unittest.TestCase):
    def test_policy_dates_and_application(self):
        start, end = '2024-02-29T23:59:59+02:00', '2024-03-02T10:00:00+02:00'
        expected = {'preserve': end, 'open': None, 'quarter-end': '2024-03-31T23:59:59+02:00',
                    'year-end': '2024-12-31T23:59:59+02:00', 'start': start,
                    'start-plus-second': '2024-03-01T00:00:00+02:00'}
        for policy, value in expected.items():
            self.assertEqual(instant(value), instant(expected_end(start, end, policy, 'always', False)))
            self.assertEqual(end, expected_end(start, end, policy, 'missing-input-end', False))
            if policy != 'preserve':
                self.assertEqual(instant(value), instant(expected_end(start, end, policy, 'missing-input-end', True)))
        self.assertEqual('2024-03-31', expected_end('2024-02-29', None, 'quarter-end', 'always', True))
        self.assertEqual('2024-12-31T23:59:59.999+02:00', expected_end('2024-02-29T10:00:00.123+02:00', None, 'year-end', 'always', True))

    def test_validates_transformation_before_restoring_comparison_view(self):
        original = {'resourceType': 'Encounter', 'id': 'source', 'class': {'code': 'IMP'},
                    'period': {'start': '1990-09-06T12:14:21+02:00', 'end': '1990-09-22T12:29:21+02:00'}}
        source = {'entry': [{'resource': original}]}
        for kind, scope in [('IMP', 'INPATIENT'), ('AMB', 'AMBULATORY')]:
            for policy in ('open', 'quarter-end', 'year-end', 'start', 'start-plus-second'):
                report = {'sourcePatient': 'p1', 'encounterNumbers': {'source': '1'},
                          'converterOptions': {f'ENCOUNTER_{scope}_END_POLICY': policy}}
                resource = copy.deepcopy(original)
                resource.update(id='p1-E-1', **{'class': {'code': kind}})
                wanted = expected_end(original['period']['start'], original['period']['end'], policy, 'always', False)
                if wanted: resource['period']['end'] = wanted
                else: resource['period'].pop('end')
                resource['status'] = 'finished' if wanted else 'in-progress'
                target = {'entry': [{'resource': resource}]}
                before = copy.deepcopy(target)
                normalized = checked_original_periods(source, target, report)
                self.assertEqual(original['period'], normalized['entry'][0]['resource']['period'])
                self.assertEqual(before, target)
                resource['period']['end'] = '2000-01-01T00:00:00+02:00'
                with self.assertRaises(AssertionError): checked_original_periods(source, target, report)

    def test_child_location_period_and_status_are_checked(self):
        start, end = '2026-01-01T08:00:00+01:00', '2026-01-03T10:00:00+01:00'
        source = {'entry': [{'resource': {'resourceType': 'Encounter', 'id': 'source', 'period': {'start': start, 'end': end}}}]}
        report = {'sourcePatient': 'p', 'encounterNumbers': {'source': '1'},
                  'converterOptions': {'ENCOUNTER_INPATIENT_END_POLICY': 'open'},
                  'movements': {'contacts': [['p', '1', start, end, 'stationaer', 'A', 'Ward', '', '', '', 'Normalstationär']]}}
        resources = []
        for id, level, parent in [('p-E-1', 'einrichtungskontakt', None), ('p-E-1-A-1', 'abteilungskontakt', 'p-E-1'), ('p-E-1-V-1', 'versorgungsstellenkontakt', 'p-E-1-A-1')]:
            resource = {'resourceType': 'Encounter', 'id': id, 'class': {'code': 'IMP'}, 'status': 'in-progress', 'period': {'start': start},
                        'type': [{'coding': [{'system': 'http://fhir.de/CodeSystem/Kontaktebene', 'code': level},
                                             {'system': 'http://fhir.de/CodeSystem/kontaktart-de', 'code': 'normalstationaer'}]}]}
            if parent: resource['partOf'] = {'reference': 'Encounter/' + parent}
            resources.append({'resource': resource})
        ward = resources[-1]['resource']
        ward['location'] = [{'period': {'start': start}, 'status': 'active'}]
        target = {'entry': resources}
        normalized = checked_original_periods(source, target, report)
        self.assertEqual([end] * 3, [r['resource']['period']['end'] for r in normalized['entry']])
        for field, wrong in [('status', 'completed'), ('period', {'start': start, 'end': end})]:
            broken = copy.deepcopy(target)
            broken['entry'][-1]['resource']['location'][0][field] = wrong
            with self.assertRaises(AssertionError): checked_original_periods(source, broken, report)
