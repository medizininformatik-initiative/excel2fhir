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
                   'FAILED': 'Lauf unvollständig. Erzeugte FHIR-Dateien und Fehlerberichte stehen zur Prüfung bereit.',
                   'NOT_VALIDATED': 'Import und Rückvergleich abgeschlossen; FHIR-Validierung deaktiviert.',
                   'COMPLETE': 'Import, Rückvergleich und FHIR-Prüfung abgeschlossen.'}.get(status, 'Lauf wird ausgeführt.')
    (directory / 'status.txt').write_text(status + '\n' + explanation
        + '\nFHIR: fhir/ (gewählte Formate und Optionsvarianten)\nExcel: excel/\nDetails: details/\n', encoding='utf-8')


def add_converter_arguments(parser):
    parser.add_argument('-o', '--output-directory', default='outputGlobal')
    parser.add_argument('--converter-options', action='append', default=[], metavar='FILE')
    parser.add_argument('-r', '--result-file-format', action='append', metavar='FORMATS')
    parser.add_argument('-p', '--patients-count', type=int, default=2147483647)
    parser.add_argument('-v', '--validate-bundles', action='store_true')
    parser.add_argument('--no-validate-bundles', action='store_false', dest='validate_bundles')
    parser.add_argument('-vll', '--validation-log-level', default='ERROR',
                        choices=['ERROR', 'IGNORED', 'WARNING', 'VALID', 'NOT_CHECKED'])
    parser.add_argument('-l', '--log-layout', default='DATE_LEVEL_SOURCE_LINENUMBER',
                        choices=['MESSANGE_ONLY', 'DATE_LEVEL_SOURCE', 'DATE_LEVEL_SOURCE_LINENUMBER'])
    parser.add_argument('-t', '--temp-directory')


def converter_settings(args):
    formats = [f for group in (args.result_file_format or ['JSON,NDJSON']) for f in group.split(',')]
    if any(f not in {'JSON', 'NDJSON', 'XML', 'JSONGZIP', 'JSONBZ2', 'ZIPJSON'} for f in formats):
        raise ValueError('Unbekanntes Ausgabeformat')
    if args.patients_count < 1:
        raise ValueError('-p muss positiv sein')
    return dict(validate=args.validate_bundles, option_files=args.converter_options,
                formats=list(dict.fromkeys(formats)), patients_per_bundle=args.patients_count,
                validation_log_level=args.validation_log_level, log_layout=args.log_layout,
                temp_directory=args.temp_directory)


def generator_arguments(arguments=None):
    parser = argparse.ArgumentParser(description='Excel mit Synthea befüllen und konvertieren. Synthea-Argumente nach -- angeben.')
    add_converter_arguments(parser)
    parser.add_argument('synthea_arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args(arguments)
    native = args.synthea_arguments
    return args.output_directory, native[1:] if native[:1] == ['--'] else native, converter_settings(args)
