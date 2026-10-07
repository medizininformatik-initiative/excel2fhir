"""Exercise upload, shared Java inspection, snapshotting and conversion in temporary storage.

Run after `mvn test package` with web/backend/requirements-test.txt installed.
"""
import json
from pathlib import Path
import sys
import tempfile
from uuid import uuid4

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / 'web' / 'backend'))
from fastapi.testclient import TestClient
from api import app
import store
import worker

original = (REPO / 'input' / 'FHIR_Testdatengenerator_Vorlage.xlsx').read_bytes()
with tempfile.TemporaryDirectory(prefix='workbook-upload-') as directory:
    store.ROOT = Path(directory)
    store.APP = REPO / 'target'
    with TestClient(app) as client:
        uploaded = client.post('/api/inputs?filename=Uploaded%20test.xlsx', content=original)
        assert uploaded.status_code == 201, uploaded.text
        item = uploaded.json()
        assert item['inspection']['valid']
        assert any(sheet['name'] == 'Person' and sheet['rows'] > 0 for sheet in item['inspection']['sheets'])
        created = client.post('/api/jobs', json={'source': item['id'],
                              'configurationProperties': 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n',
                              'requestId': str(uuid4())})
        assert created.status_code == 201, created.text
        job = created.json()
        assert store.claim() == job['id']
        worker.execute(job['id'])
        assert store.get(job['id'])['state'] == 'succeeded', (store.ROOT / 'jobs' / job['id'] / 'converter.log').read_text()[-3000:]
        assert (store.ROOT / 'jobs' / job['id'] / 'input.xlsx').read_bytes() == original
        snapshot = json.loads(client.get(f"/api/jobs/{job['id']}/snapshot").content)
        assert snapshot['sourceName'] == 'Uploaded test.xlsx'
        assert snapshot['inputSha256'] == item['sha256']
        assert client.get(f"/api/jobs/{job['id']}/download").status_code == 200
        print('PASS: raw upload, Java structural inspection, source selection, immutable input and successful conversion')
assert (REPO / 'input' / 'FHIR_Testdatengenerator_Vorlage.xlsx').read_bytes() == original
