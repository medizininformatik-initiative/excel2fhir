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
from converter_options import CONFIG_NAME, ensure_config, resolve_config
from synthea_to_excel import prepare, write_workbook
from workbook_xml import read_sheets
from workflow_layout import create_run, write_status

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
             ROOT / 'scripts/WorkbookUno.java', ROOT / 'scripts/WorkflowOptions.java', ROOT / 'src/main/resources/workbook-absent-reasons.json', *sorted((ROOT / 'scripts/mappings').glob('*'))]
    return {'versions': versions, 'converterTimezone': 'Europe/Berlin', 'sha256': {
        str(p.relative_to(ROOT)): sha256(p) for p in files if p.is_file()}}


def inspect_conversion(directory, exit_code, reports=None, log=None, *, validate=False):
    reports = directory if reports is None else reports
    if exit_code < 0 or exit_code >= 128:
        raise ValueError(f'Konverterprozess abgebrochen (Exitcode {exit_code}); '
                         'siehe conversion.log. Bei SIGKILL/-9/137 auch das verfügbare '
                         'Docker-/System-RAM prüfen; unvollständige Ausgabe wird nicht übernommen.')
    bundles = [p for p in directory.glob('*.json')
               if not p.name.endswith(('.import.json', '.validation.json'))]
    imports = list(reports.rglob('*.import.json'))
    validations = list(reports.rglob('*.validation.json'))
    if len(bundles) != 1 or len(imports) != 1 or (validate and not validations):
        log = directory.parent / 'conversion.log' if log is None else log
        if log.is_file():
            with log.open(encoding='utf-8', errors='replace') as lines:
                if any('java.lang.OutOfMemoryError' in line for line in lines):
                    raise ValueError(f'Java-Arbeitsspeicher erschöpft (Exitcode {exit_code}); '
                                     'für Docker bzw. den lokalen Lauf mehr RAM bereitstellen. '
                                     'Unvollständige Ausgabe wird nicht übernommen; siehe conversion.log.')
        raise ValueError(f'FHIR-Bundle, Importbericht oder angeforderter Validierungsbericht fehlt '
                         f'(Konverter-Exitcode {exit_code}); siehe conversion.log')
    import_report = json.loads(imports[0].read_text())
    if import_report['status'] != 'COMPLETE':
        raise ValueError('Import unvollständig; siehe ' + str(imports[0]))
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


def run(source_dir, output_dir, *, config=None, directory=None, validate=False):
    # The workbook contains local contact times; Python, Office and Java must agree.
    os.environ['TZ'] = 'Europe/Berlin'
    time.tzset()
    source_dir = Path(source_dir).resolve()
    if not source_dir.exists():
        raise ValueError('Quellverzeichnis fehlt: ' + str(source_dir))
    metadata = environment()
    output = Path(output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    config = ensure_config(output) if config is None else Path(config)
    sources = [source_dir] if source_dir.is_file() else sorted(source_dir.glob('*.json'))
    source_patients = []
    for path in sources:
        try:
            candidate = json.loads(path.read_text(encoding='utf-8'))
            source_patients.extend(e['resource']['id'] for e in candidate.get('entry', [])
                                   if e.get('resource', {}).get('resourceType') == 'Patient')
        except (ValueError, KeyError, TypeError, AttributeError):
            pass  # The per-file conversion below records malformed inputs individually.
    resolved = resolve_config(config, source_patients)
    out = create_run(output, 'synthea-import') if directory is None else Path(directory)
    snapshot = out / 'details' / CONFIG_NAME
    if config != snapshot:
        shutil.copy2(config, snapshot)
    config = snapshot
    metadata['validationEnabled'] = validate
    metadata['converterOptions'] = resolved['values']
    metadata['converterOptionsSha256'] = sha256(config)
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
            write_workbook(rows, book, options=resolved['values'])
            with (case / 'conversion.log').open('w') as log:
                conversion = subprocess.run(['java', '-XX:MaxRAMPercentage=50', '-Duser.timezone=Europe/Berlin', '-jar', str(JAR), *(['-v'] if validate else []),
                    '-f', str(book), '-o', str(case)],
                    stdout=log, stderr=subprocess.STDOUT)
            runs = list(case.glob('run-*-excel-to-fhir*'))
            if len(runs) != 1:
                raise ValueError('Konverterlauf fehlt; siehe ' + str(case / 'conversion.log'))
            converted = runs[0]
            fhir, statuses = inspect_conversion(converted / 'fhir', conversion.returncode,
                                                converted / 'details/reports', case / 'conversion.log', validate=validate)
            result = check_configured(bundle, json.loads(fhir.read_text()), report, resolved['values'],
                                      resolved['patients'][report['sourcePatient']])
            if read_sheets(book)['Codes'] != codes:
                raise ValueError('Codes-Blatt wurde beim Import verändert')
            result.update(statuses, source=str(source), workbook=str(book),
                          rows={n: len(v) for n, v in rows.items()},
                          elapsedSeconds=round(time.monotonic() - started, 2))
            ndjson = converted / 'fhir/patients.ndjson'
            if not ndjson.is_file():
                raise ValueError('NDJSON-Ausgabe fehlt')
            shutil.move(str(fhir), out / 'fhir' / (source.stem + '.json'))
            with (out / 'fhir/patients.ndjson').open('ab') as destination, ndjson.open('rb') as lines:
                shutil.copyfileobj(lines, destination)
            ndjson.unlink()
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
    parser.add_argument('-o', '--output-directory', default='outputGlobal')
    parser.add_argument('-v', '--validate-bundles', action='store_true', help='FHIR-Bundles validieren')
    args = parser.parse_args()
    raise SystemExit(run(args.input_file or args.input_directory, args.output_directory, validate=args.validate_bundles))
