"""Run inside the worker image against one isolated Compose FHIR target.

Mount this script and run `python /probe.py blaze` (or hapi). Uses temporary
workbench storage and unique Patient IDs; deletes only its own FHIR resources.
"""
import json
from pathlib import Path
import sys
import tempfile
import urllib.request
import uuid

sys.path.insert(0, '/app')
import datasets
import fhir_uploads
import store
import worker

server = sys.argv[1]
base = fhir_uploads.TARGETS[server]['url']
ids = ['upload-probe-' + uuid.uuid4().hex for _ in range(2)]

def request(path, method='GET'):
    req = urllib.request.Request(base + path, method=method, headers={'Accept': 'application/fhir+json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response) if method == 'GET' else None

with tempfile.TemporaryDirectory() as directory:
    store.ROOT = Path(directory)
    def dataset(gender):
        job = store.create('starter', 'default')
        folder = store.ROOT / 'jobs' / job
        data = folder / 'output/run-probe/fhir'
        data.mkdir(parents=True)
        bundles = []
        for identifier in ids:
            bundle = {'resourceType': 'Bundle', 'type': 'transaction', 'entry': [
                {'resource': {'resourceType': 'Patient', 'id': identifier, 'gender': gender},
                 'request': {'method': 'POST', 'url': 'Patient'}}]}
            (data / (identifier + '.json')).write_text(json.dumps(bundle))
            bundles.append(bundle)
        (data / 'patients.ndjson').write_text('\n'.join(json.dumps(b) for b in bundles))
        result = datasets.build(job, json.loads((folder / 'snapshot.json').read_text()))
        store.finish(job, 'succeeded', 0)
        return result['datasets'][0]['id']
    def load(selection):
        action = fhir_uploads.create(str(uuid.uuid4()), server, selection)
        fhir_uploads.execute(action['id'], lambda: False, worker.terminate)
        result = fhir_uploads.get(action['id'])
        print(result['state'], result['result'])
        print(fhir_uploads.logs(action['id']))
        return result
    assert fhir_uploads.target_status(server)['available']
    first, second = dataset('female'), dataset('male')
    try:
        conflict = load([first, second])
        assert conflict['state'] == 'conflict'
        assert not conflict['result']['mayHaveWritten']
        for selection, expected in [([first], 'female'), ([second], 'male')]:
            result = load(selection)
            assert result['state'] == 'succeeded', result
            assert result['result']['bundles'] == 2, 'JSON + NDJSON must not double uploads'
            for identifier in ids:
                assert request('/Patient/' + identifier)['gender'] == expected
        print('PASS:', server, 'concurrency', fhir_uploads.TARGETS[server]['concurrency'],
              'conflict before writes; repeated-ID updates; single format selection; readback')
    finally:
        for identifier in ids:
            request('/Patient/' + identifier, 'DELETE')
