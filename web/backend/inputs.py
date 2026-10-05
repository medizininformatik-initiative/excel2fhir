"""Immutable uploaded inputs, structurally inspected by the Java converter."""
from contextlib import contextmanager
import fcntl
import json
import shutil
import stat
from pathlib import Path
import subprocess
import tempfile
import time
import unicodedata
from uuid import UUID, uuid4
import zipfile

import store

MAX_UPLOAD = 64 * 1024 * 1024
MAX_UNCOMPRESSED = 256 * 1024 * 1024


def checked_name(name):
    name = unicodedata.normalize('NFC', name.strip())
    if not name.lower().endswith(('.xlsx', '.zip')) or len(name) > 200 or any(c in name for c in '/\\'):
        raise ValueError('Choose an .xlsx workbook or CSV .zip archive with a filename of at most 200 characters')
    if any(unicodedata.category(c).startswith('C') for c in name):
        raise ValueError('The filename contains control characters')
    return name


@contextmanager
def incoming():
    root = store.ROOT / 'inputs'
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.incoming-', dir=root) as directory:
        yield Path(directory)


def input_filename(kind):
    try:
        return {'workbook': 'input.xlsx', 'csv': 'input.zip'}[kind]
    except KeyError as error:
        raise ValueError('Unknown input kind') from error


def kind_for_name(name):
    return 'csv' if name.lower().endswith('.zip') else 'workbook'


def zip_entries(archive):
    entries = archive.infolist()
    if len(entries) > 10000 or sum(entry.file_size for entry in entries) > MAX_UNCOMPRESSED:
        raise ValueError('The input exceeds the supported expanded size (256 MiB)')
    if any(entry.flag_bits & 1 for entry in entries):
        raise ValueError('Encrypted archives are not supported')
    return entries


def extract_csv(path, destination):
    destination.mkdir()
    with zipfile.ZipFile(path) as archive:
        entries = zip_entries(archive)
        names = set()
        for entry in entries:
            name = entry.filename
            if (not name.endswith('.csv') or '/' in name or '\\' in name or len(name) > 200
                    or any(unicodedata.category(c).startswith('C') for c in name)
                    or stat.S_ISLNK(entry.external_attr >> 16)):
                raise ValueError('CSV archives must contain only .csv files directly at the archive root')
            if name.casefold() in names:
                raise ValueError('CSV archive contains duplicate filenames')
            names.add(name.casefold())
        total = 0
        for entry in entries:
            with archive.open(entry) as source, (destination / entry.filename).open('xb') as output:
                while chunk := source.read(65536):
                    total += len(chunk)
                    if total > MAX_UNCOMPRESSED:
                        raise ValueError('The expanded CSV input exceeds 256 MiB')
                    output.write(chunk)


def inspect(path):
    csv = path.suffix == '.zip'
    expanded = path.parent / 'inspection-csv'
    report = path.with_suffix('.inspection.json')
    try:
        if csv:
            extract_csv(path, expanded)
        else:
            with zipfile.ZipFile(path) as archive:
                zip_entries(archive)
                if not {'[Content_Types].xml', 'xl/workbook.xml'}.issubset(archive.namelist()):
                    raise ValueError('The file is not an Excel workbook')
        with (store.ROOT / 'inputs' / '.inspection.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            checked = subprocess.run(['java', '-Xmx256m', '-cp', str(store.APP / 'excel2fhir.jar'),
                                      'de.uni_leipzig.life.csv2fhir.' + ('CsvInputPreflight' if csv else 'WorkbookPreflight'),
                                      str(expanded if csv else path), str(report)],
                                     capture_output=True, text=True, timeout=60)
        if checked.returncode:
            raise ValueError('The input could not be inspected. Check that it uses the supported template or CSV schema.')
        result = json.loads(report.read_text())
        if not result['valid']:
            raise ValueError('Input structure is invalid: ' + '; '.join(result['issues']))
        return result
    except zipfile.BadZipFile as error:
        raise ValueError('The file is not a readable .xlsx workbook or CSV .zip archive') from error
    except subprocess.TimeoutExpired as error:
        raise ValueError('Input inspection exceeded 60 seconds') from error
    finally:
        report.unlink(missing_ok=True)
        if expanded.exists():
            shutil.rmtree(expanded)


def publish(directory, name):
    name = checked_name(name)
    kind = kind_for_name(name)
    path = directory / input_filename(kind)
    if not 0 < path.stat().st_size <= MAX_UPLOAD:
        raise ValueError('The input must be between 1 byte and 64 MiB')
    report = inspect(path)
    identifier = str(uuid4())
    item = {'id': 'upload:' + identifier, 'name': name, 'kind': kind, 'created': time.time(), 'size': path.stat().st_size,
            'sha256': store.digest(path), 'inspection': report}
    (directory / 'metadata.json').write_text(json.dumps(item, ensure_ascii=False, indent=2))
    path.chmod(0o444)
    (directory / 'metadata.json').chmod(0o444)
    directory.rename(store.ROOT / 'inputs' / identifier)
    return item


def summaries():
    root = store.ROOT / 'inputs'
    return sorted([json.loads(path.read_text()) for path in root.glob('*/metadata.json')
                   if not path.parent.name.startswith('.')], key=lambda item: item['created'], reverse=True)


def resolve(source):
    if not source.startswith('upload:'):
        raise ValueError('Unknown input')
    try:
        identifier = str(UUID(source.removeprefix('upload:')))
    except ValueError as error:
        raise ValueError('Unknown input') from error
    folder = store.ROOT / 'inputs' / identifier
    try:
        item = json.loads((folder / 'metadata.json').read_text())
    except FileNotFoundError as error:
        raise ValueError('The uploaded input is no longer available') from error
    path = folder / input_filename(item.get('kind', 'workbook'))
    if store.digest(path) != item['sha256']:
        raise ValueError('The uploaded input has changed')
    return path, item['name'], item.get('kind', 'workbook')
