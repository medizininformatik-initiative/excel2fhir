"""Publish reusable Workbench datasets in the host output directory."""
import os
from pathlib import Path
import shutil

import archive_names


ROOT = Path(os.environ.get('WORKBENCH_OUTPUT', '/global-output'))


def path(snapshot):
    name = snapshot['resultOutputName']
    if not name or Path(name).name != name:
        raise ValueError('Invalid result directory')
    return ROOT / name


def label(snapshot):
    return 'outputGlobal/' + snapshot['resultOutputName']


def publish(folder, snapshot, manifest):
    if 'resultOutputName' not in snapshot:
        return
    destination = path(snapshot)
    for index, item in enumerate(manifest['datasets']):
        source = folder / f'dataset-{index}.zip'
        if not source.is_file() or item.get('error'):
            continue
        destination.mkdir(parents=True, exist_ok=True)
        name = archive_names.filename(item['name'], archive_names.patient_count(source), str(index))
        target = destination / f'{index + 1:02d}-{name}'
        pending = target.with_name('.' + target.name + '.partial')
        try:
            shutil.copyfile(source, pending)
            pending.replace(target)
        finally:
            pending.unlink(missing_ok=True)
