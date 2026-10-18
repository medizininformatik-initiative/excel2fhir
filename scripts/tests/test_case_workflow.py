import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_synthea_cases import inspect_conversion, run


def fake_batch(converter):
    def execute(jobs, validate):
        for job in jobs:
            converted = converter(job['arguments'])
            Path(job['result']).write_text(json.dumps({'exitCode': converted.returncode, 'seconds': 0}))
    return execute


class WorkflowTest(unittest.TestCase):
    def test_not_checked_is_preserved_but_unexpected_exit_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            (directory / 'Fall.import.json').write_text('{"status":"COMPLETE"}')
            (directory / 'Fall.validation.json').write_text('{"status":"NOT_CHECKED"}')
            bundle, statuses = inspect_conversion(directory, 0, validate=True)
            self.assertEqual('Fall.json', bundle.name)
            self.assertEqual('NOT_CHECKED', statuses['validationStatus'])
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 1, validate=True)
            (directory / 'Fall.import.json').write_text('{"status":"INCOMPLETE"}')
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 1, validate=True)

    def test_validation_findings_and_unchecked_results_do_not_abort(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            (directory / 'Fall.import.json').write_text('{"status":"COMPLETE"}')
            for status in ('ERROR', 'FATAL', 'WARNING', 'NOT_CHECKED', 'VALID'):
                (directory / 'Fall.validation.json').write_text(json.dumps({'status': status}))
                _, result = inspect_conversion(directory, 0, validate=True)
                self.assertEqual(status, result['validationStatus'])

    def test_killed_converter_reports_exit_and_memory_hint_without_trusting_partial_files(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            for code in (-9, 137):
                with self.subTest(code=code), self.assertRaisesRegex(ValueError, 'Docker/system memory'):
                    inspect_conversion(directory, code)

    def test_heap_exhaustion_is_explained_instead_of_only_listing_missing_reports(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d) / 'fhir'
            directory.mkdir()
            (directory.parent / 'conversion.log').write_text(
                'Exception in thread main java.lang.OutOfMemoryError: Java heap space')
            with self.assertRaisesRegex(ValueError, 'Java heap exhausted'):
                inspect_conversion(directory, 1, validate=True)

    def test_missing_references_are_findings_without_failing_generation(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            (directory / 'Fall.import.json').write_text('{"status":"COMPLETE"}')
            (directory / 'Fall.validation.json').write_text(json.dumps({
                'status': 'VALID', 'referencesWithoutTargetInBundle': {'Patient/missing': 1}}))
            _, statuses = inspect_conversion(directory, 0, validate=True)
            self.assertEqual('ERROR', statuses['validationStatus'])

    @patch('run_synthea_cases.selected_configs', return_value=[{'name': 'Konvertierungsoptionen', 'values': {}, 'patients': {}}])
    @patch('run_synthea_cases.environment', return_value={})
    @patch('run_synthea_cases.read_sheets', return_value={'Codes': []})
    def test_invalid_files_do_not_hide_later_results_and_empty_inputs_fail(self, *mocks):
        with tempfile.TemporaryDirectory() as d:
            source = Path(d) / 'source'
            source.mkdir()
            output = Path(d) / 'empty'
            self.assertEqual(1, run(source, output))
            self.assertEqual('FAILED', json.loads(next(output.glob('run-*/details/reports/summary.json')).read_text())['status'])
            (source / 'a.json').write_text('invalid')
            (source / 'b.json').write_text('{"resourceType":"Bundle","entry":[]}')
            output = Path(d) / 'invalid'
            self.assertEqual(1, run(source, output))
            summary = json.loads(next(output.glob('run-*/details/reports/summary.json')).read_text())
            self.assertEqual(1, len(summary['failures']))
            self.assertEqual(1, len(summary['skipped']))

    def test_default_conversion_accepts_complete_import_and_marks_validation_disabled(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            (directory / 'Fall.json').write_text('{}')
            report = directory / 'Fall.import.json'
            report.write_text('{"status":"COMPLETE"}')
            _, statuses = inspect_conversion(directory, 0)
            self.assertEqual('NOT_VALIDATED', statuses['validationStatus'])
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 1)
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 0, validate=True)
            report.write_text('{"status":"INCOMPLETE"}')
            with self.assertRaises(ValueError):
                inspect_conversion(directory, 0)

    def test_generator_validation_flag_is_separate_from_native_arguments(self):
        from workflow_layout import generator_arguments
        output, native, settings = generator_arguments(['--', '-p', '1'])
        self.assertEqual('outputGlobal', output)
        self.assertEqual(['-p', '1'], native)
        self.assertFalse(settings['validate'])
        output, native, settings = generator_arguments(['-o', 'custom', '-v', '-p', '2', '-r', 'XML',
            '--converter-options', 'a.config', '--converter-options', 'b.config', '--', '-p', '7'])
        self.assertEqual('custom', output)
        self.assertEqual(['-p', '7'], native)
        self.assertTrue(settings['validate'])
        self.assertEqual(['XML'], settings['formats'])
        self.assertEqual(2, settings['patients_per_bundle'])
        self.assertEqual(['a.config', 'b.config'], settings['option_files'])

    def test_each_variant_needs_a_complete_import_report_with_xml_output(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            for name in ('DIZ-A', 'DIZ-B'):
                variant = directory / name
                variant.mkdir()
                (variant / 'case.xml').write_text('<Bundle/>')
                (variant / 'case.import.json').write_text('{"status":"COMPLETE"}')
            _, status = inspect_conversion(directory, 0, expected_imports=2)
            self.assertEqual('COMPLETE', status['importStatus'])
            report = directory / 'DIZ-B/case.import.json'
            report.write_text('{"status":"INCOMPLETE"}')
            with self.assertRaisesRegex(ValueError, 'Import incomplete'):
                inspect_conversion(directory, 0, expected_imports=2)
            report.unlink()
            with self.assertRaisesRegex(ValueError, 'missing'):
                inspect_conversion(directory, 0, expected_imports=2)

    def test_phase_failures_continue_and_comparison_uses_saved_source(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            source = root / 'source'
            source.mkdir()
            for i in range(3):
                (source / f'p{i}.json').write_text(json.dumps({'entry': [
                    {'resource': {'resourceType': 'Patient', 'id': f'p{i}'}}]}))
            events = []
            class Session:
                def __enter__(self):
                    return self
                def __exit__(self, *_):
                    events.append('closed')
                    (source / 'p2.json').write_text('changed after snapshot')

            def workbook(rows, output, **kwargs):
                events.append(output.stem)
                if output.stem == 'Fall-p0':
                    raise ValueError('workbook failed')

            def convert(command, **kwargs):
                self.assertEqual(['Fall-p0', 'Fall-p1', 'Fall-p2', 'closed'], events[:4])
                case = Path(command[command.index('-o') + 1])
                events.append('convert:' + case.name)
                if case.name == 'p1':
                    return SimpleNamespace(returncode=1)
                converted = case / 'run-test-excel-to-fhir'
                (converted / 'fhir').mkdir(parents=True)
                (converted / 'fhir/case.json').write_text('{"entry": []}')
                (converted / 'details/reports').mkdir(parents=True)
                (converted / 'details/reports/case.import.json').write_text('{"status":"COMPLETE"}')
                return SimpleNamespace(returncode=0)

            selected = [{'name': 'default', 'values': {}, 'patients': {f'p{i}': [f'p{i}'] for i in range(3)}}]
            with patch('run_synthea_cases.environment', return_value={}), \
                 patch('run_synthea_cases.selected_configs', return_value=selected), \
                 patch('run_synthea_cases.read_sheets', return_value={'Codes': []}), \
                 patch('run_synthea_cases.prepare', side_effect=lambda bundle, options, **kwargs: (
                     {}, {'sourcePatient': bundle['entry'][0]['resource']['id']})), \
                 patch('run_synthea_cases.write_workbook', side_effect=workbook), \
                 patch('run_synthea_cases.WorkbookSession', Session), \
                 patch('run_synthea_cases.run_conversion_batch', side_effect=fake_batch(convert)), \
                 patch('run_synthea_cases.check_configured', return_value={'outputPatients': ['p2']}) as check:
                self.assertEqual(1, run(source, root / 'output', diagnostics=True))
            summary = json.loads(next((root / 'output').glob('*/details/reports/summary.json')).read_text())
            self.assertEqual('FAILED', summary['status'])
            self.assertEqual(['p0.json', 'p1.json'], [Path(x['source']).name for x in summary['failures']])
            self.assertEqual(['p2.json'], [Path(x['source']).name for x in summary['results']])
            self.assertEqual('p2', check.call_args.args[0]['entry'][0]['resource']['id'])
            self.assertEqual(['convert:p1', 'convert:p2'], events[4:])

    def test_output_layout_for_single_and_multiple_sources_and_variants(self):
        from types import SimpleNamespace
        for inputs in (1, 2):
            for variants in (1, 2):
                with self.subTest(inputs=inputs, variants=variants), tempfile.TemporaryDirectory() as d:
                    root = Path(d)
                    source = root / 'source'
                    source.mkdir()
                    bundle = {'resourceType': 'Bundle', 'entry': [
                        {'resource': {'resourceType': 'Patient', 'id': 'p1'}}]}
                    for i in range(inputs):
                        (source / f'patient{i}.json').write_text(json.dumps(bundle))
                    # An auxiliary bundle does not require an input grouping level.
                    (source / 'providers.json').write_text('{"entry": []}')
                    selected = [{'name': f'KDS-{v}', 'values': {}, 'patients': {'p1': ['p1']}}
                                for v in range(variants)]

                    events = []
                    class Session:
                        def __enter__(self):
                            events.append('open')
                            return self
                        def __exit__(self, *_):
                            events.append('close')

                    def workbook(*args, **kwargs):
                        events.append('workbook')

                    def convert(command, **kwargs):
                        self.assertIn('close', events)
                        self.assertEqual(inputs, events.count('workbook'))
                        events.append('convert')
                        case = Path(command[command.index('-o') + 1])
                        converted = case / 'run-test-excel-to-fhir'
                        for item in selected:
                            target = converted / 'fhir'
                            if variants > 1:
                                target /= item['name']
                            target.mkdir(parents=True, exist_ok=True)
                            (target / 'case.json').write_text(json.dumps(bundle))
                            (target / 'patients.ndjson').write_text(json.dumps(bundle) + '\n')
                            reports = converted / 'details/reports' / item['name']
                            reports.mkdir(parents=True)
                            (reports / 'case.import.json').write_text('{"status":"COMPLETE"}')
                        return SimpleNamespace(returncode=0)

                    with patch('run_synthea_cases.environment', return_value={}), \
                         patch('run_synthea_cases.selected_configs', return_value=selected), \
                         patch('run_synthea_cases.read_sheets', return_value={'Codes': []}), \
                         patch('run_synthea_cases.prepare', return_value=({}, {'sourcePatient': 'p1'})), \
                         patch('run_synthea_cases.write_workbook', side_effect=workbook), \
                         patch('run_synthea_cases.WorkbookSession', Session), \
                         patch('run_synthea_cases.check_configured', return_value={'outputPatients': ['p1']}) as check, \
                         patch('run_synthea_cases.run_conversion_batch', side_effect=fake_batch(convert)):
                        self.assertEqual(0, run(source, root / 'output'))
                        self.assertEqual(0, check.call_count)
                        self.assertFalse(list((root / 'output').rglob('Fall.loss.json')))
                        normal = json.loads(next((root / 'output').glob('*/details/reports/summary.json')).read_text())
                        self.assertEqual('not-requested', normal['results'][0]['variants'][0]['comparisonStatus'])
                        self.assertEqual(['open'] + ['workbook'] * inputs + ['close'] + ['convert'] * inputs, events)
                        events.clear()
                        check.side_effect = AssertionError('deliberate comparison difference')
                        self.assertEqual(0, run(source, root / 'findings-output', diagnostics=True))
                        findings_dir = next((root / 'findings-output').glob('run-*'))
                        summary = json.loads((findings_dir / 'details/reports/summary.json').read_text())
                        self.assertEqual([], summary['failures'])
                        self.assertEqual('differences', summary['results'][0]['variants'][0]['comparisonStatus'])
                        self.assertTrue(list((findings_dir / 'fhir').rglob('*.json')))
                    run_dir = next((root / 'output').glob('run-*'))
                    for item in selected:
                        for i in range(inputs):
                            target = run_dir / 'fhir'
                            if variants > 1:
                                target /= item['name']
                            if inputs > 1:
                                target /= f'Fall-patient{i}.xlsx'
                            self.assertEqual({'case.json', 'patients.ndjson'}, {p.name for p in target.iterdir()})
