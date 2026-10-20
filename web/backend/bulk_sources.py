"""Durable, ordered 100-patient source blocks and immutable series remainders."""
import json
from pathlib import Path
import shutil
import zipfile

import bulk_plan
import store

REMAINDER = 'bulk-remainder.zip'
INHERITED = 'series-remainder.zip'


def manifest(path):
    with zipfile.ZipFile(path) as archive:
        return json.loads(archive.read('manifest.json'))


def write(path, metadata, contents):
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_suffix('.partial')
    with zipfile.ZipFile(pending, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in contents:
            archive.writestr(name, data)
        archive.writestr('manifest.json', json.dumps(metadata))
    pending.replace(path)


def source_path(folder, snapshot, index):
    if index == 0 and snapshot.get('continuation', {}).get('sourceOffset', 0):
        path = folder / INHERITED
        if store.digest(path) != snapshot['continuation']['remainder']['sha256']:
            raise ValueError('Saved source remainder changed')
        return path
    return folder / 'bulk-source-cache' / f'{index:06d}.zip'


def select(folder, snapshot, start, size, work, generate):
    """Return source paths in seed/filename order, with full-block consent assignments."""
    settings = snapshot['generation']
    offset = snapshot.get('continuation', {}).get('sourceOffset', 0)
    position = offset + start
    selected = []
    while len([item for item in selected if item['patient']]) < size:
        index, within = divmod(position, bulk_plan.CHUNK_SIZE)
        seed = bulk_plan.seed_at(settings['patientSeed'], index)
        path = source_path(folder, snapshot, index)
        if not path.exists():
            generated = work / f'source-{index}'
            generate(index, generated)
            records, support, patient_ids = [], [], []
            for file in sorted((generated / 'fhir').glob('*.json')):
                bundle = json.loads(file.read_text())
                patients = [entry['resource']['id'] for entry in bundle.get('entry', [])
                            if entry.get('resource', {}).get('resourceType') == 'Patient']
                if len(patients) > 1:
                    raise ValueError('Expected one patient per source bundle')
                record = {'name': file.name, 'patient': bool(patients)}
                if patients:
                    record['id'] = patients[0]
                    patient_ids.extend(patients)
                    records.append(record)
                else:
                    support.append(record)
            if len(records) != bulk_plan.CHUNK_SIZE:
                raise ValueError('Source block must contain exactly 100 selected patients')
            if settings.get('syntheticConsent'):
                from synthea_consent import assignments
                consent = assignments(patient_ids, seed)
                for record in records:
                    record['consent'] = consent[record['id']]
            metadata = {'seed': seed, 'offset': 0, 'patients': records, 'support': support}
            write(path, metadata, ((r['name'], (generated / 'fhir' / r['name']).read_bytes()) for r in records + support))
            shutil.rmtree(generated)
        metadata = manifest(path)
        if metadata['seed'] != seed or not metadata['offset'] <= within < 100 or len(metadata['patients']) != 100 - metadata['offset']:
            raise ValueError('Saved source block does not match the continuation position')
        count = min(size - sum(r['patient'] for r in selected), 100 - within)
        records = metadata['patients'][within - metadata['offset']:within - metadata['offset'] + count]
        if settings.get('outputMode') == 'synthea':
            records = records + metadata['support']
        with zipfile.ZipFile(path) as archive:
            for record in records:
                name = record['name']
                if Path(name).name != name:
                    raise ValueError('Invalid source filename')
                target = work / 'selected' / f'{len(selected):04d}' / name
                target.parent.mkdir(parents=True)
                target.write_bytes(archive.read(name))
                selected.append({**record, 'path': target, 'seed': seed})
        position += count
    return selected


def finish(folder, snapshot):
    position = snapshot.get('continuation', {}).get('sourceOffset', 0) + snapshot['generation']['population']
    index, offset = divmod(position, 100)
    if not offset:
        return None
    source = source_path(folder, snapshot, index)
    metadata = manifest(source)
    metadata['patients'] = metadata['patients'][offset - metadata['offset']:]
    metadata['offset'] = offset
    records = metadata['patients'] + metadata['support']
    target = folder / REMAINDER
    with zipfile.ZipFile(source) as archive:
        write(target, metadata, ((r['name'], archive.read(r['name'])) for r in records))
    return description(target)


def description(path):
    data = manifest(path)
    if not 0 < data['offset'] < 100 or len(data['patients']) != 100 - data['offset']:
        raise ValueError('Invalid saved source remainder')
    return {'sha256': store.digest(path), 'seed': data['seed'], 'offset': data['offset'], 'count': len(data['patients'])}


def prune(folder, snapshot, completed):
    next_index = (snapshot.get('continuation', {}).get('sourceOffset', 0) + completed) // 100
    for path in (folder / 'bulk-source-cache').glob('*.zip'):
        if int(path.stem) < next_index:
            path.unlink()
