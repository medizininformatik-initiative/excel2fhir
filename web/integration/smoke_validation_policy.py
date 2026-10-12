"""Run locally or in the worker: real validation and UNO edits in isolated files.

Prints AUDIT_ROOT with retained inputs, outputs and reports for inspection.
"""
import base64
import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time

PROJECT = Path('/synthea') if Path('/synthea/target/excel2fhir.jar').is_file() else Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PROJECT / 'scripts'))
sys.path.insert(0, '/app' if PROJECT == Path('/synthea') else str(PROJECT / 'web/backend'))
from synthea_to_excel import write_workbook, apply_workbook_edits, column_number
from workbook_xml import read_sheets
from run_outcomes import collect

ROOT = Path(tempfile.mkdtemp(prefix='validation-policy-'))
print('AUDIT_ROOT', ROOT, flush=True)
JAR = PROJECT / 'target/excel2fhir.jar'
TEMPLATE = PROJECT / 'FHIR_Testdatengenerator_Vorlage.xlsx'
headers = read_sheets(TEMPLATE)
patient = {'Patient-ID': 'audit-1', 'Vorname': 'Jörg', 'Nachname': 'Groß',
           'Geburtsdatum': '2000-01-01', 'Geschlecht': 'invalid-gender'}
foreign = {'Patient-ID': 'unknown-patient', 'Fall-Nr': '1', 'Start': '2026-01-01',
           'Ende': '2026-01-02', 'Einrichtungskontaktklasse': 'stationaer', 'Fachabteilung': '0100'}


def records(sheet, record):
    columns = sorted(((column_number(cell[:-1]), value) for cell, value in headers[sheet].items()
                      if cell.endswith('1') and cell[:-1].isalpha()), key=lambda item: item[0])
    boundary = next(n for n, value in columns if value == 'Erklärung/Ausfüllhilfe')
    return [[record.get(value, '') for n, value in columns if n < boundary]]


def csv_input(name, bad_reference=False, bad_header=False):
    folder = ROOT / name
    folder.mkdir()
    for sheet, record in [('Person', patient)] + ([('Fall', foreign)] if bad_reference else []):
        with (folder / ('case_' + sheet + '.csv')).open('w') as stream:
            writer = csv.writer(stream)
            writer.writerow(['wrong-header' if bad_header and key == 'Patient-ID' else key for key in record])
            writer.writerow(record.values())
    return folder


sources = {'csv': {'free-codes': csv_input('csv'), 'foreign-patient': csv_input('csv-foreign', True),
                   'missing-header': csv_input('csv-header', bad_header=True)}, 'excel': {}}
for label, rows in [('free-codes', {'Person': records('Person', patient)}),
                    ('foreign-patient', {'Person': records('Person', patient), 'Fall': records('Fall', foreign)})]:
    path = ROOT / (label + '.xlsx')
    write_workbook(rows, path)
    sources['excel'][label] = path
bad_header = ROOT / 'missing-header.xlsx'
apply_workbook_edits(sources['excel']['free-codes'],
                     ['set\tPerson\tA1\t' + base64.b64encode(b'wrong-header').decode()], bad_header)
sources['excel']['missing-header'] = bad_header

results = []
baseline = None
for kind, inputs in sources.items():
    for consistency in [False, True]:
        for label, source in inputs.items():
            for validate in ([False, True] if label == 'free-codes' else [False]):
                name = f'{kind}-{label}-consistency-{consistency}-validation-{validate}'
                folder = ROOT / name
                folder.mkdir()
                config = folder / 'options.config'
                config.write_text('CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON,NDJSON\n'
                                  f'CHECK_INPUT_CONSISTENCY={str(consistency).lower()}\n'
                                  f'CHECKS_FHIR_VALIDATION={str(validate).lower()}\n'
                                  'DAR_PATIENT_NAME_FAMILY=invalid-dar\n')
                command = ['java', '-Xmx3g', '-Duser.timezone=Europe/Berlin']
                command += (['-jar', str(JAR), '-f', str(source)] if kind == 'excel'
                            else ['-cp', str(JAR), 'de.uni_leipzig.life.csv2fhir.Main', '-i', str(source)])
                started = time.monotonic()
                with (folder / 'process.log').open('w') as log:
                    process = subprocess.run(command + ['-o', str(folder / 'output'), '--converter-options', str(config)],
                                             stdout=log, stderr=subprocess.STDOUT, timeout=180)
                run = next((folder / 'output').glob('run-*'))
                if label != 'free-codes':
                    assert process.returncode != 0, name
                    assert not (run / 'fhir').exists(), name
                    outcomes = collect(folder, {'validation': False}, 'failed')
                    assert outcomes['generation'] == 'failed', name
                else:
                    assert process.returncode == 0, (name, process.returncode, folder / 'process.log')
                    formats = {}
                    for suffix in ['.json', '.ndjson']:
                        entries = []
                        for path in (run / 'fhir').glob('*' + suffix):
                            bundles = [json.loads(line) for line in path.read_text().splitlines()] if suffix == '.ndjson' else [json.loads(path.read_text())]
                            entries.extend(e for bundle in bundles for e in bundle['entry'])
                        assert len(entries) == 1, (name, suffix)
                        resource = entries[0]['resource']
                        assert resource['gender'] == 'invalid-gender', name
                        assert resource['name'][0]['_family']['extension'][0]['valueCode'] == 'invalid-dar', name
                        formats[suffix] = entries
                    assert formats['.json'] == formats['.ndjson'], name
                    if baseline is None:
                        baseline = formats['.json']
                    assert baseline == formats['.json'], ('Validation or consistency changed output', name)
                    reports = [json.loads(path.read_text()) for path in run.rglob('*.validation.json')]
                    outcomes = collect(folder, {'validation': validate}, 'succeeded')
                    assert outcomes['generation'] == 'succeeded', name
                    if validate:
                        assert reports and all('validatorFailure' not in report for report in reports), name
                        messages = [m for report in reports for m in report['messages']]
                        assert any(m['severity'] in ['ERROR', 'FATAL'] and 'invalid-gender' in m['message'] for m in messages), name
                        assert any(m['severity'] in ['ERROR', 'FATAL'] and 'invalid-dar' in m['message'] for m in messages), name
                        assert outcomes['validation']['status'] == 'findings', (name, outcomes)
                        assert (run / 'status.txt').read_text().splitlines()[0] == 'COMPLETE', name
                    else:
                        assert not reports, name
                        assert outcomes['validation']['status'] == 'not-requested', name
                        assert (run / 'status.txt').read_text().splitlines()[0] == 'NOT_VALIDATED', name
                results.append({'case': name, 'exitCode': process.returncode, 'outcomes': outcomes,
                                'seconds': round(time.monotonic() - started, 3)})
                (ROOT / 'audit.json').write_text(json.dumps(results, indent=2))
                print('PASS', name, flush=True)
print(json.dumps({'cases': len(results), 'allPassed': True, 'auditRoot': str(ROOT)}), flush=True)
