#!/usr/bin/env python3
"""Synthea R4 -> German Excel -> FHIR, with import and validation reports.
Usage: run_synthea_cases.py [-i INPUT_DIRECTORY | -f INPUT_FILE] [-o OUTPUT_ROOT] [-v]
Requires Java/JDK 17+, LibreOffice and a freshly built target/excel2fhir.jar.
Each invocation creates a fresh run below outputGlobal (or -o).
Source files stay unchanged.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from check_synthea_roundtrip import check_configured
from converter_options import resolve_config, selected_configs
from synthea_to_excel import prepare, write_workbook
from workbook_xml import read_sheets
from workflow_layout import create_run, write_status, add_converter_arguments, converter_settings
from fhir_output import read_output

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'FHIR_Testdatengenerator_Vorlage.xlsx'
JAR = ROOT / 'target/excel2fhir.jar'


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def environment():
    for required in (TEMPLATE, JAR):
        if not required.is_file():
            raise FileNotFoundError(required)
    office = '/Applications/LibreOffice.app/Contents/MacOS/soffice'
    if not Path(office).is_file():
        office = shutil.which('libreoffice') or shutil.which('soffice')
    if not office:
        raise RuntimeError('LibreOffice fehlt')
    versions = {}
    for name, command in [('java', ['java', '-version']), ('javac', ['javac', '-version']),
                          ('libreoffice', [office, '--version'])]:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        versions[name] = (result.stdout + result.stderr).strip()
    versions['python'] = sys.version
    files = [TEMPLATE, JAR, *sorted((ROOT / 'scripts').glob('*.py')),
             ROOT / 'scripts/ReadXmlBundles.java', ROOT / 'scripts/WorkbookUno.java', ROOT / 'scripts/WorkflowOptions.java', ROOT / 'src/main/resources/workbook-absent-reasons.json', *sorted((ROOT / 'scripts/mappings').glob('*'))]
    return {'versions': versions, 'converterTimezone': 'Europe/Berlin', 'sha256': {
        str(p.relative_to(ROOT)): sha256(p) for p in files if p.is_file()}}


def inspect_conversion(directory, exit_code, reports=None, log=None, *, validate=False, expected_imports=1):
    reports = directory if reports is None else reports
    if exit_code < 0 or exit_code >= 128:
        raise ValueError(f'Konverterprozess abgebrochen (Exitcode {exit_code}); '
                         'siehe conversion.log. Bei SIGKILL/-9/137 auch das verfügbare '
                         'Docker-/System-RAM prüfen.')
    bundles = [p for p in directory.rglob('*') if p.is_file()
               and p.name.endswith(('.json', '.ndjson', '.xml', '.gz', '.bz2', '.zip'))
               and not p.name.endswith(('.import.json', '.validation.json'))]
    imports = list(reports.rglob('*.import.json'))
    validations = list(reports.rglob('*.validation.json'))
    if not bundles or len(imports) != expected_imports or (validate and not validations):
        log = directory.parent / 'conversion.log' if log is None else log
        if log.is_file():
            with log.open(encoding='utf-8', errors='replace') as lines:
                if any('java.lang.OutOfMemoryError' in line for line in lines):
                    raise ValueError(f'Java-Arbeitsspeicher erschöpft (Exitcode {exit_code}); '
                                     'für Docker bzw. den lokalen Lauf mehr RAM bereitstellen. '
                                     'Siehe conversion.log.')
        raise ValueError(f'FHIR-Bundle, Importbericht oder angeforderter Validierungsbericht fehlt '
                         f'(Konverter-Exitcode {exit_code}); siehe conversion.log')
    for path in imports:
        if json.loads(path.read_text())['status'] != 'COMPLETE':
            raise ValueError('Import unvollständig; siehe ' + str(path))
    if not validate:
        if exit_code != 0:
            raise ValueError(f'Unerwarteter Konverter-Exitcode {exit_code}')
        return bundles[0], {'importStatus': 'COMPLETE', 'validationStatus': 'NOT_VALIDATED',
                            'converterExitCode': exit_code}
    statuses = []
    for path in validations:
        validation = json.loads(path.read_text())
        if validation.get('referencesWithoutTargetInBundle'):
            raise ValueError('Nicht auflösbare lokale FHIR-Referenzen im erzeugten Bundle')
        status = validation['status']
        if status not in {'VALID', 'WARNING', 'IGNORED', 'NOT_CHECKED'}:
            raise ValueError('FHIR-Validierung fehlgeschlagen; siehe ' + str(path))
        statuses.append(status)
    status = 'NOT_CHECKED' if 'NOT_CHECKED' in statuses else 'WARNING' if 'WARNING' in statuses else statuses[0]
    expected_exit = 1 if status == 'NOT_CHECKED' else 0
    if exit_code != expected_exit:
        raise ValueError(f'Unerwarteter Konverter-Exitcode {exit_code} bei Status {status}')
    return bundles[0], {'importStatus': 'COMPLETE', 'validationStatus': status,
                        'converterExitCode': exit_code}


def run(source_dir, output_dir, *, directory=None, validate=False, option_files=(),
        formats=None, patients_per_bundle=2147483647, validation_log_level='ERROR',
        log_layout='DATE_LEVEL_SOURCE_LINENUMBER', temp_directory=None):
    # The workbook contains local contact times; Python, Office and Java must agree.
    os.environ['TZ'] = 'Europe/Berlin'
    time.tzset()
    source_dir = Path(source_dir).resolve()
    if not source_dir.exists():
        raise ValueError('Quellverzeichnis fehlt: ' + str(source_dir))
    metadata = environment()
    output = Path(output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    sources = [source_dir] if source_dir.is_file() else sorted(source_dir.glob('*.json'))
    source_patients = []
    for path in sources:
        try:
            candidate = json.loads(path.read_text(encoding='utf-8'))
            source_patients.extend(e['resource']['id'] for e in candidate.get('entry', [])
                                   if e.get('resource', {}).get('resourceType') == 'Patient')
        except (ValueError, KeyError, TypeError, AttributeError):
            pass  # The per-file conversion below records malformed inputs individually.
    selected = selected_configs(option_files, source_patients)
    defaults = resolve_config()['values'] if option_files else selected[0]['values']
    out = create_run(output, 'synthea-import') if directory is None else Path(directory)
    snapshots = []
    for index, config in enumerate(option_files):
        snapshot = out / 'details/options-input' / str(index) / Path(config).name
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        if Path(config).resolve() != snapshot.resolve():
            shutil.copy2(config, snapshot)
        snapshots.append(snapshot)
    metadata['validationEnabled'] = validate
    metadata['converterOptions'] = [{'name': item['name'], 'values': item['values']} for item in selected]
    metadata['formats'] = formats or ['JSON', 'NDJSON']
    write_json(out / 'details/reports/environment.json', metadata)
    codes = read_sheets(TEMPLATE)['Codes']
    summary = {'schemaVersion': 1, 'status': 'RUNNING', 'results': [], 'failures': [], 'skipped': []}
    summary_file = out / 'details/reports/summary.json'
    write_json(summary_file, summary)
    for source in sources:
        started = time.monotonic()
        try:
            bundle = json.loads(source.read_text(encoding='utf-8'))
            if not any(e.get('resource', {}).get('resourceType') == 'Patient'
                       for e in bundle.get('entry', [])):
                summary['skipped'].append({'source': str(source), 'reason': 'Kein Patient im Bundle'})
                continue
            case = out / 'details/cases' / source.stem
            case.mkdir()
            saved_source = out / 'details/sources' / source.name
            if not source.is_relative_to(out / 'details/sources'):
                shutil.copy2(source, saved_source)
            rows, report = prepare(bundle)
            write_json(case / 'source.json', {'file': str(source), 'sha256': sha256(source)})
            write_json(case / 'Fall.loss.json', report)
            book = out / 'excel' / ('Fall-' + source.stem + '.xlsx')
            write_workbook(rows, book, options=defaults)
            command = ['java', '-XX:MaxRAMPercentage=50', '-Duser.timezone=Europe/Berlin', '-jar', str(JAR),
                       '-f', str(book), '-o', str(case), '-r', ','.join(formats or ['JSON', 'NDJSON']),
                       '-p', str(patients_per_bundle), '-vll', validation_log_level, '-l', log_layout]
            if validate:
                command.append('-v')
            if temp_directory:
                command.extend(['-t', str(Path(temp_directory).resolve())])
            for config in snapshots:
                command.extend(['--converter-options', str(config)])
            with (case / 'conversion.log').open('w') as log:
                conversion = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
            runs = list(case.glob('run-*-excel-to-fhir*'))
            if len(runs) != 1:
                raise ValueError('Konverterlauf fehlt; siehe ' + str(case / 'conversion.log'))
            converted = runs[0]
            final = out / 'fhir'
            if len(sources) > 1:
                final = final / book.name
            # Publish precisely the converter's output formats and variant directories.
            if (converted / 'fhir').is_dir():
                final.mkdir(parents=True, exist_ok=True)
                for variant in (converted / 'fhir').iterdir():
                    shutil.move(str(variant), final / variant.name)
            _, statuses = inspect_conversion(final, conversion.returncode,
                converted / 'details/reports', case / 'conversion.log', validate=validate,
                expected_imports=len(selected))
            variants = []
            for item in selected:
                try:
                    result = check_configured(bundle, read_output(final / item['name']), report, item['values'],
                                              item['patients'][report['sourcePatient']])
                except Exception as error:
                    raise ValueError(f"Variante {item['name']}: {error}") from error
                variants.append(dict(result, name=item['name']))
            if read_sheets(book)['Codes'] != codes:
                raise ValueError('Codes-Blatt wurde beim Import verändert')
            result = dict(statuses, variants=variants, source=str(source), workbook=str(book),
                          outputPatients=[pid for v in variants for pid in v['outputPatients']],
                          rows={n: len(v) for n, v in rows.items()},
                          elapsedSeconds=round(time.monotonic() - started, 2))
            summary['results'].append(result)
            print(statuses['validationStatus'], source.name, flush=True)
        except Exception as ex:
            summary['failures'].append({'source': str(source), 'error': repr(ex)})
            print('FAILED', source.name, repr(ex), flush=True)
        finally:
            write_json(summary_file, summary)
    if not summary['results'] and not summary['failures']:
        summary['failures'].append({'error': 'Keine Synthea-Patientenbundles gefunden'})
    summary['status'] = ('FAILED' if summary['failures'] else 'NOT_CHECKED'
        if any(r['validationStatus'] == 'NOT_CHECKED' for r in summary['results']) else 'COMPLETE' if validate else 'NOT_VALIDATED')
    write_json(summary_file, summary)
    write_status(out, summary['status'])
    return 0 if summary['status'] in {'COMPLETE', 'NOT_VALIDATED'} else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group()
    inputs.add_argument('-i', '--input-directory', default='input')
    inputs.add_argument('-f', '--input-file')
    add_converter_arguments(parser)
    args = parser.parse_args()
    raise SystemExit(run(args.input_file or args.input_directory, args.output_directory, **converter_settings(args)))
