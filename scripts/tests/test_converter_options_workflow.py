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
    def test_shared_reference_defaults(self):
        defaults = options.workflow_defaults()
        self.assertEqual('false', defaults['SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER'])
        self.assertEqual('true', defaults['SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION'])
        self.assertEqual('false', defaults['SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER'])

    def test_effective_values_replace_every_template_setting(self):
        settings = options.workflow_defaults()
        settings.update(PID_PREFIX='demo-', START_ID_CONDITION='47',
                        SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER='false')
        with patch('synthea_to_excel.apply_workbook_edits') as apply:
            write_workbook({}, Path('/tmp/options-review.xlsx'), options=settings)
        written = {parts[2]: base64.b64decode(parts[3]).decode()
                   for op in apply.call_args.args[1] if (parts := op.split('\t'))[0] == 'set'
                   and parts[1] == 'Konvertierungsoptionen'}
        self.assertEqual(len(settings), len(written))
        self.assertIn('PID_PREFIX = demo-', written.values())
        self.assertIn('START_ID_CONDITION = 47', written.values())
        self.assertIn('SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER = false', written.values())
        self.assertIn('PID_SUFFIX =', written.values())

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

    def test_properties_values_survive_csv_without_quotes_or_literal_newlines(self):
        line = options.property_line('PID_PREFIX', 'a b,"\\\n')
        self.assertNotIn('"', line)
        self.assertNotIn(',', line)
        self.assertNotIn('\n', line)
        self.assertIn(r'\u0020', line)

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
    def test_named_variants_retain_independent_values_and_reject_collisions(self, resolve):
        resolve.side_effect = [{'name': 'DIZ-A', 'values': {'PID_PREFIX': 'a-'}},
                               {'name': 'DIZ-B', 'values': {'PID_PREFIX': 'b-'}}]
        selected = options.selected_configs(['a.config', 'b.config'], ['patient1'])
        self.assertEqual(['a-', 'b-'], [s['values']['PID_PREFIX'] for s in selected])
        self.assertEqual(['a.config', 'b.config'], [s['path'] for s in selected])
        resolve.assert_any_call('b.config', ['patient1'])
        resolve.side_effect = [{'name': 'same'}, {'name': 'SAME'}]
        with self.assertRaisesRegex(ValueError, 'denselben Ausgabenamen'):
            options.selected_configs(['first.config', 'second.config'])
