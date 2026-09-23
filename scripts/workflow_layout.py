"""Directory conventions shared by the two Synthea entry points."""
import argparse
from datetime import datetime
import os
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from pathlib import Path


# Capture the system zone before the clinical import selects its workbook zone.
def system_zone():
    configured = os.environ.get('TZ')
    if configured:
        configured = configured.removeprefix(':')
        if Path(configured).is_absolute():
            with Path(configured).open('rb') as source:
                return ZoneInfo.from_file(source)
        try:
            return ZoneInfo(configured)
        except ZoneInfoNotFoundError:
            return datetime.now().astimezone().tzinfo
    try:
        with Path('/etc/localtime').open('rb') as source:
            return ZoneInfo.from_file(source)
    except FileNotFoundError:
        return datetime.now().astimezone().tzinfo


SYSTEM_ZONE = system_zone()


def run_time():
    return datetime.now(SYSTEM_ZONE)


def create_run(output, operation):
    root = Path(output).resolve()
    root.mkdir(parents=True, exist_ok=True)
    name = 'run-' + run_time().strftime('%Y%m%d_%H-%M-%S') + '-' + operation
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
    print('Output: ' + str(candidate), flush=True)
    return candidate


def write_status(directory, status):
    explanation = {'NOT_CHECKED': 'Import and source comparison passed; some terminology checks could not run.',
                   'FAILED': 'Run incomplete. Generated FHIR files and error reports are available for review.',
                   'NOT_VALIDATED': 'Import and source comparison completed; FHIR validation disabled.',
                   'COMPLETE': 'Import, source comparison and FHIR validation completed.'}.get(status, 'Run in progress.')
    (directory / 'status.txt').write_text(status + '\n' + explanation
        + '\nFHIR: fhir/ (selected formats and KDS variants)\nExcel: excel/\nDetails: details/\n', encoding='utf-8')


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
        raise ValueError('Unknown output format')
    if args.patients_count < 1:
        raise ValueError('-p must be positive')
    return dict(validate=args.validate_bundles, option_files=args.converter_options,
                formats=list(dict.fromkeys(formats)), patients_per_bundle=args.patients_count,
                validation_log_level=args.validation_log_level, log_layout=args.log_layout,
                temp_directory=args.temp_directory)


def generator_arguments(arguments=None):
    parser = argparse.ArgumentParser(description='Fill Excel with Synthea data and convert it. Place Synthea arguments after --.')
    add_converter_arguments(parser)
    parser.add_argument('synthea_arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args(arguments)
    native = args.synthea_arguments
    return args.output_directory, native[1:] if native[:1] == ['--'] else native, converter_settings(args)
