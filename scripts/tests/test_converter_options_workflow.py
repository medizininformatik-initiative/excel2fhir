import base64
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch, Mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import converter_options as options
from synthea_to_excel import write_workbook


class ConverterOptionsWorkflowTest(unittest.TestCase):
    def test_generated_workbooks_contain_no_converter_settings(self):
        settings = {}
        settings.update(PID_PREFIX='demo-', START_ID_CONDITION='47',
                        SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER='false')
        with patch('synthea_to_excel.apply_workbook_edits') as apply:
            write_workbook({}, Path('/tmp/options-review.xlsx'), options=settings)
        written = {parts[2]: base64.b64decode(parts[3]).decode()
                   for op in apply.call_args.args[1] if (parts := op.split('\t'))[0] == 'set'
                   and parts[1] == 'Konvertierungsoptionen'}
        self.assertEqual({}, written)

    @patch('subprocess.run')
    def test_java_parser_failures_are_reported_before_conversion(self, run):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'DIZ.config'
            path.write_text('CHECK_INPUT_CONSISTENCY=bad')
            run.return_value = Mock(returncode=0, stdout=json.dumps({'errors': ['bad boolean', 'duplicate value']}))
            with self.assertRaisesRegex(ValueError, 'bad boolean\nduplicate value'):
                options.resolve_config(path)
            payload = json.loads(run.call_args.kwargs['input'])
            self.assertEqual({}, payload['defaults'])

    def test_each_requested_copy_is_audited_and_extra_patients_are_rejected(self):
        from check_synthea_roundtrip import check_configured
        def entry(kind, id, **fields):
            return {'resource': {'resourceType': kind, 'id': id, **fields}}
        bundle = {'entry': [entry('Patient', 'demo-p1'), entry('Patient', 'demo-p11'),
                            entry('Condition', 'c1', subject={'reference': 'Patient/demo-p1'}),
                            entry('Condition', 'c11', subject={'reference': 'Patient/demo-p11'}),
                            entry('Medication', 'shared')]}
        with patch('check_synthea_roundtrip.check', return_value={'conditions': 1, 'encounters': 0}) as check:
            result = check_configured({}, bundle, {'sourcePatient': 'p1'}, {}, ['demo-p1', 'demo-p11'])
            self.assertEqual(2, result['conditions'])
            self.assertEqual(2, check.call_count)
            for call in check.call_args_list:
                selected = call.args[1]['entry']
                self.assertEqual(3, len(selected))
                self.assertEqual(call.args[2]['outputPatient'], selected[0]['resource']['id'])
            with self.assertRaises(AssertionError):
                check_configured({}, bundle, {}, {}, ['demo-p1'])

    @patch.object(options, 'resolve_config')
    def test_one_external_configuration_per_run(self, resolve):
        resolve.return_value = {'name': 'DIZ-A', 'values': {'PID_PREFIX': 'A-'}, 'patients': {}}
        self.assertEqual('DIZ-A', options.selected_configs(['a.config'])[0]['name'])
        resolve.reset_mock()
        with self.assertRaisesRegex(ValueError, 'one converter configuration'):
            options.selected_configs(['a.config', 'b.config'])
        resolve.assert_not_called()
