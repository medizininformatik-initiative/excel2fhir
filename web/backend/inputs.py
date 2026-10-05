"""Immutable uploaded workbooks, structurally inspected by the Java converter."""
from contextlib import contextmanager
import fcntl
import json
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
    if not name.lower().endswith('.xlsx') or len(name) > 200 or any(c in name for c in '/\\'):
        raise ValueError('Choose an .xlsx workbook with a filename of at most 200 characters')
    if any(unicodedata.category(c).startswith('C') for c in name):
        raise ValueError('The filename contains control characters')
    return name


@contextmanager
def incoming():
    root = store.ROOT / 'inputs'
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.incoming-', dir=root) as directory:
        yield Path(directory)


def inspect(path):
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            if len(entries) > 10000 or sum(entry.file_size for entry in entries) > MAX_UNCOMPRESSED:
                raise ValueError('The workbook exceeds the supported expanded size (256 MiB)')
            if any(entry.flag_bits & 1 for entry in entries):
                raise ValueError('Encrypted workbooks are not supported')
            if not {'[Content_Types].xml', 'xl/workbook.xml'}.issubset(archive.namelist()):
                raise ValueError('The file is not an Excel workbook')
    except zipfile.BadZipFile as error:
        raise ValueError('The file is not a readable .xlsx workbook') from error
    report = path.with_suffix('.inspection.json')
    try:
        with (store.ROOT / 'inputs' / '.inspection.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            checked = subprocess.run(['java', '-Xmx256m', '-cp', str(store.APP / 'excel2fhir.jar'),
                                      'de.uni_leipzig.life.csv2fhir.WorkbookPreflight', str(path), str(report)],
                                     capture_output=True, text=True, timeout=60)
        if checked.returncode:
            raise ValueError('The workbook could not be inspected. Check that it is an unencrypted .xlsx file using the supported template.')
        result = json.loads(report.read_text())
        if not result['valid']:
            raise ValueError('Workbook structure is invalid: ' + '; '.join(result['issues']))
        return result
    except subprocess.TimeoutExpired as error:
        raise ValueError('Workbook inspection exceeded 60 seconds') from error
    finally:
        report.unlink(missing_ok=True)


def publish(directory, name):
    name = checked_name(name)
    path = directory / 'input.xlsx'
    if not 0 < path.stat().st_size <= MAX_UPLOAD:
        raise ValueError('The workbook must be between 1 byte and 64 MiB')
    report = inspect(path)
    identifier = str(uuid4())
    item = {'id': 'upload:' + identifier, 'name': name, 'created': time.time(), 'size': path.stat().st_size,
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
    path = folder / 'input.xlsx'
    if store.digest(path) != item['sha256']:
        raise ValueError('The uploaded input has changed')
    return path, item['name']
