"""General CarePlans retain distinct activities without implicit clinical defaults."""
import copy
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from careplan_import import project_plan
from synthea_to_excel import prepare


class CarePlanImportTest(unittest.TestCase):
    def test_independent_activities_raw_systems_and_losses(self):
        plan = {'resourceType': 'CarePlan', 'id': 'plan', 'status': 'free-status',
                'created': '2026-01-01', 'period': {'start': '2026-02-01'},
                'category': [{'text': 'Other category'}, {'coding': [
                    {'system': 'http://snomed.info/sct', 'code': 'c', 'version': 'v'}]}],
                'activity': [{'detail': {'code': {'text': 'First', 'coding': [
                    {'system': 'urn:own:activity', 'code': 'a', 'version': 'own'}]},
                    'status': 'other-status', 'scheduledPeriod': {'end': '2026-03-01'},
                    'statusReason': {'text': 'Reason'},
                    'location': {'reference': 'Location/elsewhere', 'display': 'Place'}}},
                    {'detail': {'code': {'text': 'Second'}}}], 'careTeam': [{'reference': 'CareTeam/x'}]}
        before = copy.deepcopy(plan); losses = []
        rows = project_plan(plan, 'p', 'f', lambda path, reason: losses.append(path))
        self.assertEqual(before, plan)
        self.assertEqual(2, len(rows))
        first, second = rows
        self.assertEqual('p', first['Patient-ID'])
        self.assertEqual('free-status', first['Planstatus'])
        self.assertEqual('urn:own:activity', first['Aktivitätscodesystem'])
        self.assertEqual('own', first['Aktivitätsversion'])
        self.assertEqual('2026-03-01', first['Aktivitätsende'])
        self.assertEqual('Reason', first['Aktivitätsstatusgrund'])
        self.assertEqual('Place', first['Aktivitätsort'])
        self.assertEqual({'Aktivitätstext': 'Second'}, {k: v for k, v in second.items() if v})
        self.assertEqual({'category[0]', 'activity[0].detail.location.reference', 'careTeam'}, set(losses))

    def test_roundtrip_check_detects_missing_or_modified_followup_activity(self):
        from check_careplans import expected_plans, check_careplans
        rows = project_plan({'activity': [{'detail': {'code': {'text': 'One'}}},
                                         {'detail': {'status': 'completed'}}]}, 'p', '', lambda *args: None)
        expected = expected_plans(rows, 'p')
        check_careplans(rows, expected, 'p')
        changed = copy.deepcopy(expected)
        changed[0]['activity'][1]['detail']['status'] = 'in-progress'
        with self.assertRaises(AssertionError): check_careplans(rows, changed, 'p')
        changed[0]['activity'].pop()
        with self.assertRaises(AssertionError): check_careplans(rows, changed, 'p')

    def test_empty_plan_does_not_invent_activity_category_or_status(self):
        rows = project_plan({'resourceType': 'CarePlan', 'id': 'empty'}, 'p', '', lambda *args: None)
        self.assertEqual([{'Patient-ID': 'p'}], [{k: v for k, v in r.items() if v} for r in rows])

    def test_fixture_translation_and_source_ids_follow_plan_start_rows(self):
        source = json.loads((Path(__file__).parent / 'fixtures/synthea/male.json').read_text())
        plan = next(e['resource'] for e in source['entry'] if e['resource']['resourceType'] == 'CarePlan')
        extra = copy.deepcopy(plan); extra['id'] = 'second-plan'; extra.pop('activity')
        source['entry'].append({'fullUrl': 'urn:uuid:second-plan', 'resource': extra})
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        plans = rows['Behandlungsplan']
        self.assertEqual(3, len(plans))
        self.assertEqual('', plans[1]['Patient-ID'])
        self.assertEqual('', plans[1]['Fall-Nr'])
        self.assertEqual('', plans[1]['Kategoriecode'])
        self.assertEqual('http://snomed.info/sct', plans[0]['Aktivitätscodesystem'])
        self.assertEqual(['183051005', '408580007', ''], [r['Aktivitätscode'] for r in plans])
        self.assertNotEqual(plan['activity'][0]['detail']['code']['text'], plans[0]['Aktivitätstext'])
        self.assertEqual([1, 3], [r['inputRow'] for r in report['clinicalImports'] if r['resourceType'] == 'CarePlan'])
        self.assertEqual(before, source)


if __name__ == '__main__':
    unittest.main()
