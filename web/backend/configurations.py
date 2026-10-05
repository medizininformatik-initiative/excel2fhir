"""Named configurations stored as atomic JSON documents in the workbench volume."""
from contextlib import contextmanager
import fcntl
import json
from pathlib import Path
import tempfile
import time
import unicodedata
from uuid import UUID, uuid4

import store


class Conflict(ValueError):
    pass


def checked_name(name):
    name = unicodedata.normalize('NFC', name.strip())
    if not name or len(name) > 120 or any(unicodedata.category(c).startswith('C') for c in name):
        raise ValueError('Use a name of 1–120 characters without control characters')
    return name


@contextmanager
def locked():
    root = store.ROOT / 'configurations'
    root.mkdir(parents=True, exist_ok=True)
    with (root / '.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield root


def path_for(root, configuration_id):
    try:
        canonical = str(UUID(configuration_id))
    except ValueError:
        raise FileNotFoundError('Configuration not found')
    return root / (canonical + '.json')


def read(root, configuration_id):
    return json.loads(path_for(root, configuration_id).read_text())


def summaries():
    with locked() as root:
        items = [json.loads(p.read_text()) for p in root.glob('*.json')]
    return sorted([{k: v for k, v in item.items() if k != 'configurationProperties'} for item in items],
                  key=lambda item: (item['name'].casefold(), item['id']))


def get(configuration_id):
    with locked() as root:
        return read(root, configuration_id)


def check_revision(item, revision):
    if item['revision'] != revision:
        raise Conflict('The saved configuration changed in another tab. Reload the list and try again.')


def write(root, item):
    for path in root.glob('*.json'):
        other = json.loads(path.read_text())
        if other['id'] != item['id'] and other['name'].casefold() == item['name'].casefold():
            raise Conflict('A configuration with this name already exists')
    with tempfile.NamedTemporaryFile(mode='w', dir=root, suffix='.tmp', delete=False) as output:
        temporary = Path(output.name)
        try:
            json.dump(item, output, ensure_ascii=False, indent=2)
            output.flush()
            temporary.replace(path_for(root, item['id']))
        finally:
            temporary.unlink(missing_ok=True)
    return item


def validate(text):
    if not isinstance(text, str) or not 1 <= len(text) <= 1_000_000:
        raise ValueError('Invalid configuration size')
    store.ROOT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=store.ROOT) as directory:
        path = Path(directory) / 'configuration.config'
        path.write_text(text)
        store.validate_configuration(path)


def create(name, text):
    name = checked_name(name)
    validate(text)
    now = time.time()
    item = {'schemaVersion': 1, 'id': str(uuid4()), 'name': name, 'revision': 1,
            'created': now, 'updated': now, 'configurationProperties': text}
    with locked() as root:
        return write(root, item)


def update(configuration_id, revision, name=None, text=None):
    if name is None and text is None:
        raise ValueError('Provide a name or configuration settings')
    if name is not None:
        name = checked_name(name)
    if text is not None:
        validate(text)
    with locked() as root:
        item = read(root, configuration_id)
        check_revision(item, revision)
        if name is not None:
            item['name'] = name
        if text is not None:
            item['configurationProperties'] = text
        item['revision'] += 1
        item['updated'] = time.time()
        return write(root, item)


def duplicate(configuration_id, revision, name):
    name = checked_name(name)
    with locked() as root:
        item = read(root, configuration_id)
        check_revision(item, revision)
        now = time.time()
        item.update(id=str(uuid4()), name=name, revision=1, created=now, updated=now)
        return write(root, item)


def delete(configuration_id, revision):
    with locked() as root:
        item = read(root, configuration_id)
        check_revision(item, revision)
        trash = root / '.trash'
        trash.mkdir(exist_ok=True)
        path_for(root, configuration_id).replace(trash / (item['id'] + '.json'))


def start_jobs(source, selections, request_id, generation_settings=None):
    def prepare(prepared):
        if len({selection['id'] for selection in selections}) != len(selections):
            raise ValueError('Choose each saved configuration only once')
        with locked() as root:
            items = []
            for selection in selections:
                item = read(root, selection['id'])
                check_revision(item, selection['revision'])
                items.append(item)
        batch_id = str(uuid4())
        for item in items:
            store.prepare_job(prepared, source, 'default', item['configurationProperties'],
                              saved_configuration={key: item[key] for key in ('id', 'name', 'revision')},
                              batch_id=batch_id, generation_settings=generation_settings)
    descriptor = {'kind': 'saved', 'source': source, 'selections': selections}
    if generation_settings is not None:
        descriptor['generation'] = generation_settings
    return store.submit(descriptor, request_id, prepare)
