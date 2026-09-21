import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_synthea_workflow as workflow
from converter_options import CONFIG_NAME, workflow_defaults


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
        options_patch = patch.object(workflow, 'resolve_config', return_value={'values': workflow_defaults()})
        options_patch.start()
        self.addCleanup(options_patch.stop)

    def conversion(self, source, output, *, config=None, directory=None):
        self.assertTrue(config.is_file())
        (directory / 'fhir/good.json').write_text('{"resourceType":"Bundle"}')
        (directory / 'details/reports/summary.json').write_text(json.dumps({'status': self.status,
            'results': [{'workbook': str(directory / 'excel/Fall-good.xlsx')}],
            'failures': [{'source': 'bad'}] if self.status == 'FAILED' else []}))
        return 1

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=0))
    def test_partial_or_unchecked_outputs_are_never_reported_as_complete(self, generate):
        self.status = 'FAILED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion):
            self.assertEqual(1, workflow.run(self.root / 'output', ['-p', '1']))
        directory = next((self.root / 'output').glob('run-*'))
        self.assertEqual(['good.json'], [p.name for p in (directory/'fhir').iterdir()])
        self.assertEqual('FAILED', json.loads((directory/'details/reports/workflow.json').read_text())['status'])
        self.status = 'NOT_CHECKED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion):
            self.assertEqual(1, workflow.run(self.root / 'output', ['-p', '1']))
        self.assertEqual(2, len(list((self.root/'output').glob('run-*'))))
        self.assertIn('--exporter.fhir.bulk_data=false', generate.call_args.args[0])

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=2))
    @patch.object(workflow, 'convert_cases')
    def test_failed_generation_never_starts_conversion(self, convert, generate):
        with self.assertRaisesRegex(RuntimeError, 'Synthea fehlgeschlagen'):
            workflow.run(self.root / 'output', [])
        convert.assert_not_called()
        directory = next((self.root/'output').glob('run-*'))
        self.assertEqual('FAILED', json.loads((directory/'details/reports/workflow.json').read_text())['status'])

    def test_wrong_source_version_does_not_generate_or_create_output(self):
        (self.root/'target/synthea-revision.txt').write_text('new modules')
        with self.assertRaisesRegex(ValueError, 'Stand passt nicht'):
            workflow.run(self.root/'output', [])
        self.assertFalse((self.root/'output').exists())

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=0))
    def test_explicit_native_history_setting_overrides_default(self, generate):
        self.status = 'NOT_CHECKED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion):
            workflow.run(self.root / 'output', ['--exporter.years_of_history=7'])
        command = generate.call_args.args[0]
        self.assertLess(command.index('--exporter.years_of_history=0'),
                        command.index('--exporter.years_of_history=7'))

    @patch.object(workflow.subprocess, 'run')
    def test_invalid_options_stop_before_synthea_or_run_directory(self, generate):
        out = self.root / 'output'
        out.mkdir()
        (out / CONFIG_NAME).write_text('CHECK_INPUT_CONSISTENCY=treu\n')
        with patch.object(workflow, 'resolve_config', side_effect=ValueError('bad options')):
            with self.assertRaisesRegex(ValueError, 'bad options'):
                workflow.run(out, [])
        generate.assert_not_called()
        self.assertEqual([CONFIG_NAME], [p.name for p in out.iterdir()])

    @patch.object(workflow.subprocess, 'run', return_value=Mock(returncode=0))
    def test_existing_config_is_snapshotted_and_forwarded(self, generate):
        out = self.root / 'output'
        out.mkdir()
        original = '# custom settings\nPID_PREFIX=demo-\n'
        (out / CONFIG_NAME).write_text(original)
        self.status = 'NOT_CHECKED'
        with patch.object(workflow, 'convert_cases', side_effect=self.conversion) as convert:
            workflow.run(out, [])
        snapshot = convert.call_args.kwargs['config']
        self.assertEqual(original, snapshot.read_text())
        self.assertEqual(original, (out / CONFIG_NAME).read_text())
