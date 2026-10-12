import json
from pathlib import Path
import tempfile
import unittest
from run_outcomes import collect, save


class OutcomeTests(unittest.TestCase):
    def test_findings_are_independent_and_import_notes_are_counted_by_occurrence(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root)
            reports = folder / 'output/run-test/details/reports'
            reports.mkdir(parents=True)
            (reports / 'one.validation.json').write_text('{"status":"ERROR"}')
            (reports / 'two.validation.json').write_text('{"status":"NOT_CHECKED"}')
            note = {'resourceType': 'Observation', 'path': 'component', 'reason': 'not transferred'}
            (reports / 'summary.json').write_text(json.dumps({'results': [{'source': '/input/p.json', 'variants': [
                {'name': 'A', 'comparisonStatus': 'differences', 'comparisonDetails': 'missing value',
                 'importNotes': [note, note], 'configuredOmissions': [note]},
                {'name': 'B', 'comparisonStatus': 'unavailable', 'comparisonDetails': 'cannot compare'}]}]}))
            result = save(folder, {'inputKind': 'synthea', 'validation': True}, 'succeeded')
            self.assertEqual('succeeded', result['generation'])
            self.assertEqual('findings', result['validation']['status'])
            self.assertEqual(1, result['validation']['incomplete'])
            transfer = result['transfer']
            self.assertEqual((1, 1, 1, 2, 1), tuple(transfer[key] for key in
                ('checked', 'differences', 'unavailable', 'importNotes', 'configuredOmissions')))
            self.assertEqual(2, transfer['notes'][0]['count'])
            self.assertEqual('p.json', transfer['details'][0]['source'])
            self.assertEqual(result, json.loads((folder / 'outcomes.json').read_text()))

    def test_absent_reports_are_not_reported_as_passed(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root)
            self.assertEqual('unavailable', collect(folder, {'validation': True}, 'failed')['validation']['status'])
            plain = collect(folder, {}, 'succeeded')
            self.assertEqual('not-requested', plain['validation']['status'])
            self.assertEqual('not-applicable', plain['transfer']['status'])
            native = collect(folder, {'inputKind': 'synthea-generation', 'generation': {'outputMode': 'synthea'}}, 'succeeded')
            self.assertEqual('not-applicable', native['transfer']['status'])
