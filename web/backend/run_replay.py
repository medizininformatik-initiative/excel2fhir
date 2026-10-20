"""Reuse saved Excel workbooks as an immutable input set, without Synthea."""
import json
import shutil
import time
import uuid

import inputs
import store


def workbooks(root, snapshot):
    kind = snapshot.get('inputKind', 'workbook')
    if kind == 'workbook':
        return [root / 'input.xlsx'] if (root / 'input.xlsx').is_file() else []
    if kind == 'workbook-set':
        return sorted((root / 'workbooks').glob('*.xlsx'))
    return sorted((root / 'output').glob('run-*/excel/*.xlsx'))


def verify(root, manifest):
    books = manifest['workbooks']
    if not books:
        raise ValueError('No saved Excel workbooks')
    names = set()
    for item in books:
        name = item['name']
        if '/' in name or '\\' in name or not name.endswith('.xlsx') or name in names:
            raise ValueError('Invalid saved workbook name')
        names.add(name)
        path = root / 'workbooks' / name
        if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()) or store.digest(path) != item['sha256']:
            raise ValueError('Saved workbook has changed')


def editor(job_id):
    with store.run_lock(job_id), inputs.library_lock():
        job = store.get(job_id)
        if not job:
            raise FileNotFoundError('Run not found')
        if job['state'] not in {'succeeded', 'failed'}:
            raise store.SubmissionConflict('Only completed runs can supply saved Excel workbooks')
        root = store.ROOT / 'jobs' / job_id
        snapshot = json.loads((root / 'snapshot.json').read_text())
        books = workbooks(root, snapshot)
        if not books:
            raise store.SubmissionConflict('No saved Excel workbooks are available')
        if any(path.is_symlink() or not path.resolve().is_relative_to(root.resolve()) for path in books):
            raise store.SubmissionConflict('Saved Excel workbook is unavailable')
        manifest = {'schemaVersion': 1, 'sourceRun': job_id,
                    'sourceSnapshotSha256': store.digest(root / 'snapshot.json'),
                    'clinicalTimeZone': 'Europe/Berlin' if snapshot.get('inputKind', '').startswith('synthea') else snapshot.get('clinicalTimeZone'),
                    'workbooks': [{'name': path.name if sum(other.name == path.name for other in books) == 1 else f'{index + 1:04d}-{path.name}', 'sha256': store.digest(path)} for index, path in enumerate(books)]}
        identifier = str(uuid.uuid5(uuid.NAMESPACE_URL, json.dumps(manifest, sort_keys=True)))
        target = store.ROOT / 'inputs' / identifier
        if not target.exists():
            with inputs.incoming() as incoming:
                (incoming / 'workbooks').mkdir()
                for path, item in zip(books, manifest['workbooks']):
                    saved = incoming / 'workbooks' / item['name']
                    shutil.copyfile(path, saved)
                    saved.chmod(0o444)
                (incoming / 'workbooks.json').write_text(json.dumps(manifest, indent=2))
                verify(incoming, manifest)
                metadata = {'id': 'upload:' + identifier, 'kind': 'workbook-set',
                            'name': (job.get('dataset_name') or job.get('source_name') or job_id[:8]) + ' · Excel',
                            'created': time.time(), 'size': sum(p.stat().st_size for p in books),
                            'sha256': store.digest(incoming / 'workbooks.json'),
                            'inspection': {'sheets': [{'name': p.name, 'rows': 1} for p in books]}}
                (incoming / 'metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
                (incoming / 'workbooks.json').chmod(0o444)
                (incoming / 'metadata.json').chmod(0o444)
                incoming.rename(target)
        return {'source': 'upload:' + identifier, 'datasetName': job.get('dataset_name') or '',
                'configurationProperties': snapshot['profile'].get('optionsProperties') or (root / 'default.config').read_text(),
                'workbookCount': len(books), 'sourceRun': job_id}
