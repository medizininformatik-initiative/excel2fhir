#!/usr/bin/env python3
"""Generate with the pinned Synthea, then use the existing Excel/FHIR pipeline.
Usage: run_synthea_workflow.py [-o OUTPUT_ROOT] -- [native Synthea arguments...]
Build/copy target/synthea.jar and target/synthea-revision.txt for a local run.
"""
import json
from pathlib import Path
import shutil
import subprocess
import sys
from workflow_layout import create_run, write_status, generator_arguments
from converter_options import CONFIG_NAME, ensure_config, resolve_config
from run_synthea_cases import ROOT, run as convert_cases, sha256, write_json


def run(output, arguments):
    jar = ROOT / 'target/synthea.jar'
    revision_file = ROOT / 'target/synthea-revision.txt'
    expected = (ROOT / 'scripts/synthea-version.txt').read_text().strip()
    if not jar.is_file() or not revision_file.is_file():
        raise FileNotFoundError('Synthea fehlt. Den Compose-Komplettlauf oder die lokale Bauanleitung verwenden.')
    if revision_file.read_text().strip() != expected:
        raise ValueError('Synthea-Stand passt nicht zu den mitgelieferten Mappings.')
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    config = ensure_config(output)
    resolved = resolve_config(config)
    directory = create_run(output, 'synthea')
    shutil.copy2(config, directory / 'details' / CONFIG_NAME)
    resolved = resolve_config(directory / 'details' / CONFIG_NAME)
    command = ['java', '-Xmx4g', '-Duser.timezone=Europe/Berlin', '-jar', str(jar),
               '--exporter.years_of_history=0', *arguments,
               '--exporter.baseDirectory=' + str(directory / 'details/sources/synthea'),
               '--exporter.fhir.export=true', '--exporter.fhir_stu3.export=false',
               '--exporter.fhir_dstu2.export=false', '--exporter.fhir.bulk_data=false',
               '--exporter.use_uuid_filenames=true',
               '--exporter.hospital.fhir.export=false', '--exporter.practitioner.fhir.export=false']
    report = {'status': 'GENERATING', 'syntheaRevision': expected, 'syntheaJarSha256': sha256(jar),
              'syntheaArguments': command[5:], 'output': str(directory),
              'converterOptions': resolved['values'], 'converterOptionsSha256': sha256(directory / 'details' / CONFIG_NAME)}
    report_path = directory / 'details/reports/workflow.json'
    write_json(report_path, report)
    try:
        print('Synthea erzeugt Patienten; Fortschritt in details/logs/synthea.log.', flush=True)
        with (directory / 'details/logs/synthea.log').open('w') as log:
            generated = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
        if generated.returncode:
            raise RuntimeError('Synthea fehlgeschlagen; siehe details/logs/synthea.log (Exitcode ' + str(generated.returncode) + ').')
        report['status'] = 'CONVERTING'
        write_json(report_path, report)
        print('Patienten werden über Excel nach FHIR konvertiert und geprüft.', flush=True)
        code = convert_cases(directory / 'details/sources/synthea/fhir', output,
                             config=directory / 'details' / CONFIG_NAME, directory=directory)
        summary = json.loads((directory / 'details/reports/summary.json').read_text())
        report['status'] = summary['status']
        report['completedSourcePatients'] = len(summary['results'])
        report['completedPatients'] = sum(len(r.get('outputPatients', [None])) for r in summary['results'])
        report['failedPatients'] = len(summary['failures'])
        print('FHIR-Dateien: ' + str(directory / 'fhir'), flush=True)
        print('Excel-Dateien: ' + str(directory / 'excel'), flush=True)
        if summary['status'] == 'NOT_CHECKED':
            print('Import und Rückvergleich bestanden. FHIR-Validierung teilweise NICHT PRÜFBAR; siehe Berichte. Exitcode 1 bleibt erhalten.', flush=True)
        elif summary['status'] == 'FAILED':
            print('Lauf UNVOLLSTÄNDIG: siehe details/reports/summary.json. fhir/ enthält nur erfolgreich abgeglichene Patienten.', flush=True)
        return code
    except Exception as error:
        report.update(status='FAILED', error=str(error))
        raise
    finally:
        write_json(report_path, report)
        write_status(directory, report['status'])


if __name__ == '__main__':
    output, native = generator_arguments()
    raise SystemExit(run(output, native))
