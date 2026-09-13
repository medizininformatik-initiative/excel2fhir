import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_synthea_cases import inspect_conversion, run


class WorkflowTest(unittest.TestCase):
    def test_not_checked_is_preserved_but_unexpected_exit_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            (directory / 'Fall.import.json').write_text('{"status":"COMPLETE"}')
            (directory / 'Fall.validation.json').write_text('{"status":"NOT_CHECKED"}')
            bundle, statuses = inspect_conversion(directory, 1)
            self.assertEqual('Fall.json', bundle.name)
            self.assertEqual('NOT_CHECKED', statuses['validationStatus'])
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 0)
            (directory / 'Fall.import.json').write_text('{"status":"INCOMPLETE"}')
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 1)

    def test_missing_references_fail_even_with_an_acceptable_validator_status(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            (directory / 'Fall.import.json').write_text('{"status":"COMPLETE"}')
            (directory / 'Fall.validation.json').write_text(json.dumps({
                'status': 'VALID', 'referencesWithoutTargetInBundle': {'Patient/missing': 1}}))
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 0)

    @patch('run_synthea_cases.environment', return_value={})
    @patch('run_synthea_cases.read_sheets', return_value={'Codes': []})
    def test_invalid_files_do_not_hide_later_results_and_empty_inputs_fail(self, *mocks):
        with tempfile.TemporaryDirectory() as d:
            source = Path(d) / 'source'
            source.mkdir()
            output = Path(d) / 'empty'
            self.assertEqual(1, run(source, output))
            self.assertEqual('FAILED', json.loads((output / 'summary.json').read_text())['status'])
            (source / 'a.json').write_text('invalid')
            (source / 'b.json').write_text('{"resourceType":"Bundle","entry":[]}')
            output = Path(d) / 'invalid'
            self.assertEqual(1, run(source, output))
            summary = json.loads((output / 'summary.json').read_text())
            self.assertEqual(1, len(summary['failures']))
            self.assertEqual(1, len(summary['skipped']))
