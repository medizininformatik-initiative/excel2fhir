"""Run inside the worker image, using isolated temporary storage and the real JAR.

  docker compose -f web/compose.yml exec -T worker python - < web/integration/smoke_saved_runs.py
"""
import json
from pathlib import Path
import tempfile
from uuid import uuid4
import zipfile

import configurations
import store
import worker


def snapshot(job_id):
    return json.loads((store.ROOT / 'jobs' / job_id / 'snapshot.json').read_text())


def measurements(job_id):
    values = []
    def visit(value):
        if isinstance(value, dict):
            if value.get('resourceType') == 'Observation':
                for observation in [value] + value.get('component', []):
                    quantity = observation.get('valueQuantity', {})
                    if quantity.get('system') == 'http://unitsofmeasure.org':
                        values.append(quantity)
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    with zipfile.ZipFile(store.ROOT / 'jobs' / job_id / 'result.zip') as archive:
        for name in archive.namelist():
            if '/fhir/' in name and name.endswith('.json'):
                visit(json.loads(archive.read(name)))
    return values


with tempfile.TemporaryDirectory(prefix='saved-runs-') as directory:
    store.ROOT = Path(directory)
    base = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n'
    normal = configurations.create('Standard', base)
    faulty = configurations.create('Unit only', base + 'OBSERVATION_LABORATORY_UCUM_CODE_IN_UNIT=true\nOBSERVATION_VITAL_SIGNS_UCUM_CODE_IN_UNIT=true\n')
    jobs = [store.create('starter', 'default', item['configurationProperties']) for item in [normal, faulty]]
    for job_id in jobs:
        assert store.claim() == job_id
        worker.execute(job_id)
        assert store.get(job_id)['state'] == 'succeeded', (store.ROOT / 'jobs' / job_id / 'converter.log').read_text()[-3000:]
    first = measurements(jobs[0]); second = measurements(jobs[1])
    assert first and len(first) == len(second)
    coded = 0
    for normal, changed in zip(first, second):
        if normal.get('code'):
            coded += 1
            assert 'code' not in changed and changed.get('unit') == normal['code'], (normal, changed)
        else:
            assert normal == changed, (normal, changed)
    assert coded > 0
    old_snapshot = snapshot(jobs[1])
    configurations.delete(faulty['id'], 1)
    loaded = store.editor_input(jobs[1])
    repeated = store.create(loaded['source'], 'default', loaded['configurationProperties'])
    assert store.claim() == repeated
    worker.execute(repeated)
    assert store.get(repeated)['state'] == 'succeeded'
    assert old_snapshot['profile'] == snapshot(repeated)['profile']
    assert old_snapshot['inputSha256'] == snapshot(repeated)['inputSha256']
    assert second == measurements(repeated)
    assert old_snapshot == snapshot(jobs[1])
    print(f'PASS: two independent configurations, {len(first)} quantities each, immutable snapshots, load into editor and resubmit after configuration deletion')
