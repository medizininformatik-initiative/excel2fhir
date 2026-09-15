import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_synthea_workflow as workflow


class CompleteWorkflowTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'target').mkdir()
        (self.root / 'scripts').mkdir()
        (self.root / 'target/synthea.jar').write_bytes(b'jar')
        (self.root / 'target/synthea-revision.txt').write_text('pinned')
        (self.root / 'scripts/synthea-version.txt').write_text('pinned')
        root_patch = patch.object(workflow, 'ROOT', self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)

    def conversion(self, source, output):
        good = output / 'good'
        bad = output / 'bad'
        for case in (good, bad):
            (case / 'fhir').mkdir(parents=True)
            (case / 'fhir/Fall.json').write_text('{"resourceType":"Bundle"}')
            (case / 'fhir/Fall.import.json').write_text('{}')
            (case / 'fhir/Fall.validation.json').write_text('{}')
        (output / 'summary.json').write_text(json.dumps({'status': self.status,
            'results': [{'workbook': str(good / 'Fall.xlsx')}], 'failures': [{'source': 'bad'}] if self.status=='FAILED' else []}))
        return 1

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=0))
    def test_partial_or_unchecked_outputs_are_never_reported_as_complete(self, generate):
        self.status = 'FAILED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion):
            self.assertEqual(1, workflow.run(self.root / 'output', ['-p', '1']))
        directory = next((self.root / 'output').iterdir())
        self.assertEqual(['good.json'], [p.name for p in (directory/'fhir').iterdir()])
        self.assertEqual('FAILED', json.loads((directory/'workflow.json').read_text())['status'])
        self.status = 'NOT_CHECKED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion):
            self.assertEqual(1, workflow.run(self.root / 'output', ['-p', '1']))
        self.assertEqual(2, len(list((self.root/'output').iterdir())))
        self.assertIn('--exporter.fhir.bulk_data=false', generate.call_args.args[0])

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=2))
    @patch.object(workflow, 'convert_cases')
    def test_failed_generation_never_starts_conversion(self, convert, generate):
        with self.assertRaisesRegex(RuntimeError, 'Synthea fehlgeschlagen'):
            workflow.run(self.root / 'output', [])
        convert.assert_not_called()
        directory = next((self.root/'output').iterdir())
        self.assertEqual('FAILED', json.loads((directory/'workflow.json').read_text())['status'])

    def test_wrong_source_version_does_not_generate_or_create_output(self):
        (self.root/'target/synthea-revision.txt').write_text('new modules')
        with self.assertRaisesRegex(ValueError, 'Stand passt nicht'):
            workflow.run(self.root/'output', [])
        self.assertFalse((self.root/'output').exists())
