"""Directory conventions shared by the two Synthea entry points."""
import argparse
from datetime import datetime, timezone
from pathlib import Path


def create_run(output, operation):
    root = Path(output).resolve()
    root.mkdir(parents=True, exist_ok=True)
    name = 'run-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%SZ') + '-' + operation
    candidate = root / name
    suffix = 2
    while True:
        try:
            candidate.mkdir(mode=0o700)
            break
        except FileExistsError:
            candidate = root / (name + '-' + str(suffix))
            suffix += 1
    for folder in ('fhir', 'excel', 'details/sources', 'details/reports', 'details/logs', 'details/cases'):
        (candidate / folder).mkdir(parents=True, exist_ok=True)
    write_status(candidate, 'RUNNING')
    print('Ausgabe: ' + str(candidate), flush=True)
    return candidate


def write_status(directory, status):
    explanation = {'NOT_CHECKED': 'Import und Rückvergleich bestanden; Terminologieprüfung teilweise nicht ausführbar.',
                   'FAILED': 'Lauf unvollständig. fhir/ enthält nur erfolgreich abgeglichene Patienten.',
                   'COMPLETE': 'Import, Rückvergleich und FHIR-Prüfung abgeschlossen.'}.get(status, 'Lauf wird ausgeführt.')
    (directory / 'status.txt').write_text(status + '\n' + explanation
        + '\nFHIR: fhir/ (JSON und patients.ndjson)\nExcel: excel/\nDetails: details/\n', encoding='utf-8')


def generator_arguments(arguments=None):
    parser = argparse.ArgumentParser(description='Synthea erzeugen und über Excel nach FHIR konvertieren. Native Synthea-Argumente nach -- angeben.')
    parser.add_argument('-o', '--output-directory', default='outputGlobal')
    parser.add_argument('synthea_arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args(arguments)
    native = args.synthea_arguments
    return args.output_directory, native[1:] if native[:1] == ['--'] else native
