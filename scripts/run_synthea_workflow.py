#!/usr/bin/env python3
"""Generate with the pinned Synthea, then use the existing Excel/FHIR pipeline.
Usage: run_synthea_workflow.py OUTPUT_DIRECTORY [native Synthea arguments...]
Build/copy target/synthea.jar and target/synthea-revision.txt for a local run.
"""
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
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
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory = Path(tempfile.mkdtemp(prefix='run-' + stamp + '-', dir=output))
    command = ['java', '-Xmx4g', '-Duser.timezone=Europe/Berlin', '-jar', str(jar),
               '--exporter.years_of_history=0', *arguments,
               '--exporter.baseDirectory=' + str(directory / 'synthea'),
               '--exporter.fhir.export=true', '--exporter.fhir_stu3.export=false',
               '--exporter.fhir_dstu2.export=false', '--exporter.fhir.bulk_data=false',
               '--exporter.use_uuid_filenames=true',
               '--exporter.hospital.fhir.export=false', '--exporter.practitioner.fhir.export=false']
    report = {'status': 'GENERATING', 'syntheaRevision': expected, 'syntheaJarSha256': sha256(jar),
              'syntheaArguments': command[5:], 'output': str(directory)}
    report_path = directory / 'workflow.json'
    write_json(report_path, report)
    print('Ausgabe: ' + str(directory), flush=True)
    try:
        print('Synthea erzeugt Patienten; Fortschritt in synthea.log.', flush=True)
        with (directory / 'synthea.log').open('w') as log:
            generated = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
        if generated.returncode:
            raise RuntimeError('Synthea fehlgeschlagen; siehe synthea.log (Exitcode ' + str(generated.returncode) + ').')
        report['status'] = 'CONVERTING'
        write_json(report_path, report)
        print('Patienten werden über Excel nach FHIR konvertiert und geprüft.', flush=True)
        code = convert_cases(directory / 'synthea/fhir', directory / 'cases')
        summary = json.loads((directory / 'cases/summary.json').read_text())
        report['status'] = summary['status']
        report['completedPatients'] = len(summary['results'])
        report['failedPatients'] = len(summary['failures'])
        # Only outputs with complete import and successful source comparison are
        # published in the convenient FHIR folder. Full evidence stays in cases/.
        fhir = directory / 'fhir'
        fhir.mkdir()
        for result in summary['results']:
            case = Path(result['workbook']).parent
            for bundle in (case / 'fhir').glob('*.json'):
                if not bundle.name.endswith(('.import.json', '.validation.json')):
                    shutil.copy2(bundle, fhir / (case.name + '.json'))
        print('FHIR-Dateien: ' + str(fhir), flush=True)
        print('Excel und Einzelberichte: ' + str(directory / 'cases'), flush=True)
        if summary['status'] == 'NOT_CHECKED':
            print('Import und Rückvergleich bestanden. FHIR-Validierung teilweise NICHT PRÜFBAR; siehe Berichte. Exitcode 1 bleibt erhalten.', flush=True)
        elif summary['status'] == 'FAILED':
            print('Lauf UNVOLLSTÄNDIG: siehe cases/summary.json. fhir/ enthält nur erfolgreich abgeglichene Patienten.', flush=True)
        return code
    except Exception as error:
        report.update(status='FAILED', error=str(error))
        raise
    finally:
        write_json(report_path, report)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    raise SystemExit(run(sys.argv[1], sys.argv[2:]))
