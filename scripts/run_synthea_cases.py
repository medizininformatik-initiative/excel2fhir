#!/usr/bin/env python3
"""Synthea R4 -> German Excel -> FHIR, with import and validation reports.
Usage: run_synthea_cases.py SYNTHEA_FHIR_DIRECTORY OUTPUT_DIRECTORY
Requires Java/JDK 17+, LibreOffice and a freshly built target/excel2fhir.jar.
The output directory may contain converter-options.config, but no previous results.
Source files stay unchanged.
"""
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


def inspect_conversion(directory, exit_code):
    if exit_code < 0 or exit_code >= 128:
        raise ValueError(f'Konverterprozess abgebrochen (Exitcode {exit_code}); '
                         'siehe conversion.log. Bei SIGKILL/-9/137 auch das verfügbare '
                         'Docker-/System-RAM prüfen; unvollständige Ausgabe wird nicht übernommen.')
    bundles = [p for p in directory.glob('*.json')
               if not p.name.endswith(('.import.json', '.validation.json'))]
    imports = list(directory.glob('*.import.json'))
    validations = list(directory.glob('*.validation.json'))
    if len(bundles) != 1 or len(imports) != 1 or len(validations) != 1:
        log = directory.parent / 'conversion.log'
        if log.is_file():
            with log.open(encoding='utf-8', errors='replace') as lines:
                if any('java.lang.OutOfMemoryError' in line for line in lines):
                    raise ValueError(f'Java-Arbeitsspeicher erschöpft (Exitcode {exit_code}); '
                                     'für Docker bzw. den lokalen Lauf mehr RAM bereitstellen. '
                                     'Unvollständige Ausgabe wird nicht übernommen; siehe conversion.log.')
        raise ValueError(f'Genau ein FHIR-Bundle, Importbericht und Validierungsbericht erwartet '
                         f'(Konverter-Exitcode {exit_code}); siehe conversion.log')
    import_report = json.loads(imports[0].read_text())
    validation = json.loads(validations[0].read_text())
    if import_report['status'] != 'COMPLETE':
        raise ValueError('Import unvollständig; siehe ' + str(imports[0]))
    if validation.get('referencesWithoutTargetInBundle'):
        raise ValueError('Nicht auflösbare lokale FHIR-Referenzen im erzeugten Bundle')
    status = validation['status']
    if status not in {'VALID', 'WARNING', 'IGNORED', 'NOT_CHECKED'}:
        raise ValueError('FHIR-Validierung fehlgeschlagen; siehe ' + str(validations[0]))
    expected_exit = 1 if status == 'NOT_CHECKED' else 0
    if exit_code != expected_exit:
        raise ValueError(f'Unerwarteter Konverter-Exitcode {exit_code} bei Status {status}')
    return bundles[0], {'importStatus': 'COMPLETE', 'validationStatus': status,
                        'converterExitCode': exit_code}


def run(source_dir, output_dir, *, config=None):
    # The workbook contains local contact times; Python, Office and Java must agree.
    os.environ['TZ'] = 'Europe/Berlin'
    time.tzset()
    source_dir = Path(source_dir).resolve()
    if not source_dir.is_dir():
        raise ValueError('Quellverzeichnis fehlt: ' + str(source_dir))
    metadata = environment()
    out = Path(output_dir).resolve()
    if out.exists() and any(p.name != CONFIG_NAME for p in out.iterdir()):
        raise FileExistsError('Ausgabeordner enthält bereits Ergebnisse: ' + str(out))
    out.mkdir(parents=True, exist_ok=True)
    config = ensure_config(out) if config is None else Path(config)
    source_patients = []
    for path in sorted(source_dir.glob('*.json')):
        try:
            candidate = json.loads(path.read_text(encoding='utf-8'))
            source_patients.extend(e['resource']['id'] for e in candidate.get('entry', [])
                                   if e.get('resource', {}).get('resourceType') == 'Patient')
        except (ValueError, KeyError, TypeError, AttributeError):
            pass  # The per-file conversion below records malformed inputs individually.
    resolved = resolve_config(config, source_patients)
    metadata['converterOptions'] = resolved['values']
    metadata['converterOptionsSha256'] = sha256(config)
    write_json(out / 'environment.json', metadata)
    codes = read_sheets(TEMPLATE)['Codes']
    summary = {'schemaVersion': 1, 'status': 'RUNNING', 'results': [], 'failures': [], 'skipped': []}
    summary_file = out / 'summary.json'
    write_json(summary_file, summary)
    for source in sorted(source_dir.glob('*.json')):
        started = time.monotonic()
        try:
            bundle = json.loads(source.read_text(encoding='utf-8'))
            if not any(e.get('resource', {}).get('resourceType') == 'Patient'
                       for e in bundle.get('entry', [])):
                summary['skipped'].append({'source': str(source), 'reason': 'Kein Patient im Bundle'})
                continue
            case = out / source.stem
            case.mkdir()
            rows, report = prepare(bundle)
            write_json(case / 'source.json', {'file': str(source), 'sha256': sha256(source)})
            write_json(case / 'Fall.loss.json', report)
            book = case / 'Fall.xlsx'
            write_workbook(rows, book, options=resolved['values'])
            with (case / 'conversion.log').open('w') as log:
                conversion = subprocess.run(['java', '-XX:MaxRAMPercentage=50', '-Duser.timezone=Europe/Berlin', '-jar', str(JAR), '-v',
                    '-f', str(book), '-o', str(case / 'fhir'), '-t', str(case / 'csv')],
                    stdout=log, stderr=subprocess.STDOUT)
            fhir, statuses = inspect_conversion(case / 'fhir', conversion.returncode)
            result = check_configured(bundle, json.loads(fhir.read_text()), report, resolved['values'],
                                      resolved['patients'][report['sourcePatient']])
            if read_sheets(book)['Codes'] != codes:
                raise ValueError('Codes-Blatt wurde beim Import verändert')
            result.update(statuses, source=str(source), workbook=str(book),
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
        if any(r['validationStatus'] == 'NOT_CHECKED' for r in summary['results']) else 'COMPLETE')
    write_json(summary_file, summary)
    return 0 if summary['status'] == 'COMPLETE' else 1


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    raise SystemExit(run(*sys.argv[1:]))
