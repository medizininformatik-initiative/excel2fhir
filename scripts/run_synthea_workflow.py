#!/usr/bin/env python3
"""Generate with the pinned Synthea, then use the existing Excel/FHIR pipeline.
Usage: run_synthea_workflow.py [-o OUTPUT_ROOT] [-v] -- [native Synthea arguments...]
Build/copy target/synthea.jar and target/synthea-revision.txt for a local run.
"""
import json
from pathlib import Path
import shutil
import subprocess
import sys
from workflow_layout import create_run, write_status, generator_arguments
from converter_options import selected_configs
from run_synthea_cases import ROOT, run as convert_cases, sha256, write_json


def run(output, arguments, *, validate=False, option_files=(), **settings):
    jar = ROOT / 'target/synthea.jar'
    revision_file = ROOT / 'target/synthea-revision.txt'
    expected = (ROOT / 'scripts/synthea-version.txt').read_text().strip()
    if not jar.is_file() or not revision_file.is_file():
        raise FileNotFoundError('Synthea is missing. Use the Compose workflow or follow the local build instructions.')
    if revision_file.read_text().strip() != expected:
        raise ValueError('The Synthea revision does not match the bundled mappings.')
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    selected_configs(option_files)
    directory = create_run(output, 'synthea')
    snapshots = []
    for index, config in enumerate(option_files):
        target = directory / 'details/options-input' / str(index) / Path(config).name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(config, target)
        snapshots.append(target)
    command = ['java', '-Xmx4g', '-Duser.timezone=Europe/Berlin', '-jar', str(jar),
               '--exporter.years_of_history=0', *arguments,
               '--exporter.baseDirectory=' + str(directory / 'details/sources/synthea'),
               '--exporter.fhir.export=true', '--exporter.fhir_stu3.export=false',
               '--exporter.fhir_dstu2.export=false', '--exporter.fhir.bulk_data=false',
               '--exporter.use_uuid_filenames=true',
               '--exporter.hospital.fhir.export=false', '--exporter.practitioner.fhir.export=false']
    report = {'status': 'GENERATING', 'validationEnabled': validate, 'syntheaRevision': expected, 'syntheaJarSha256': sha256(jar),
              'syntheaArguments': command[5:], 'output': str(directory),
              'converterOptions': [dict(path=str(p), sha256=sha256(p)) for p in snapshots], 'conversionSettings': settings}
    report_path = directory / 'details/reports/workflow.json'
    write_json(report_path, report)
    try:
        print('Synthea is generating patients; see details/logs/synthea.log for progress.', flush=True)
        with (directory / 'details/logs/synthea.log').open('w') as log:
            generated = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
        if generated.returncode:
            raise RuntimeError('Synthea failed; see details/logs/synthea.log (exit code ' + str(generated.returncode) + ').')
        report['status'] = 'CONVERTING'
        write_json(report_path, report)
        print('Converting patients through Excel to FHIR and comparing them with the source data.', flush=True)
        code = convert_cases(directory / 'details/sources/synthea/fhir', output,
                             option_files=snapshots, directory=directory, validate=validate, **settings)
        summary = json.loads((directory / 'details/reports/summary.json').read_text())
        report['status'] = summary['status']
        report['completedSourcePatients'] = len(summary['results'])
        report['completedPatients'] = sum(len(r.get('outputPatients', [None])) for r in summary['results'])
        report['failedPatients'] = len(summary['failures'])
        print('FHIR files: ' + str(directory / 'fhir'), flush=True)
        print('Excel files: ' + str(directory / 'excel'), flush=True)
        if summary['status'] == 'NOT_CHECKED':
            print('Import and source comparison passed. Some FHIR validation checks could not run; see reports. Exit code: 1.', flush=True)
        elif summary['status'] == 'FAILED':
            print('Run INCOMPLETE: see details/reports/summary.json. Generated FHIR files and error reports are available for review.', flush=True)
        return code
    except Exception as error:
        report.update(status='FAILED', error=str(error))
        raise
    finally:
        write_json(report_path, report)
        write_status(directory, report['status'])


if __name__ == '__main__':
    output, native, settings = generator_arguments()
    raise SystemExit(run(output, native, **settings))
