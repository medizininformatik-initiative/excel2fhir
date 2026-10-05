"""Inspect real CSV variants and expose separate persistent dataset archives.

Run after `mvn test package` with web/backend/requirements-test.txt installed.
"""
import io
import zipfile
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
        created = client.post('/api/jobs', json={'source': item['id'], 'profile': 'default',
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
        csv_files = list((store.ROOT / 'jobs' / job['id'] / 'output').glob('*/details/csv/*.csv'))
        assert csv_files, list((store.ROOT / 'jobs' / job['id'] / 'output').rglob('*.csv'))
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in csv_files:
                if 'Konvertierungsoptionen' not in path.name:
                    archive.writestr(path.name, path.read_bytes())
            prefix = next(path.name[:-len('Person.csv')] for path in csv_files if path.name.endswith('Person.csv'))
            for name, formats in [('One', 'JSON,NDJSON'), ('Two', 'XML')]:
                archive.writestr(prefix + 'Konvertierungsoptionen_' + name + '.csv', 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=' + formats + '\n')
        uploaded_csv = client.post('/api/inputs?filename=Cases.zip', content=data.getvalue())
        assert uploaded_csv.status_code == 201, uploaded_csv.text
        csv_item = uploaded_csv.json()
        assert csv_item['kind'] == 'csv'
        response = client.post('/api/jobs', json={'source': csv_item['id'], 'profile': 'workbook',
                              'requestId': str(uuid4())})
        assert response.status_code == 201, response.text
        csv_job = response.json()
        assert store.claim() == csv_job['id']
        worker.execute(csv_job['id'])
        csv_folder = store.ROOT / 'jobs' / csv_job['id']
        assert store.get(csv_job['id'])['state'] == 'succeeded', (csv_folder / 'converter.log').read_text()[-3000:]
        assert (csv_folder / 'input.zip').read_bytes() == data.getvalue()
        manifest = client.get(f"/api/jobs/{csv_job['id']}/artifacts").json()
        assert len(manifest['datasets']) == 2, manifest
        one, two = manifest['datasets']
        assert one['inspection']['resourceCounts'] == two['inspection']['resourceCounts'], (one, two)
        assert one['inspection']['patients'] == 10, one
        assert one['inspection']['inspectedFormats'] == ['json'], one
        assert two['inspection']['inspectedFormats'] == ['xml'], two
        for item in manifest['datasets']:
            response = client.get(f"/api/datasets/{item['id']}/download")
            assert response.status_code == 200
            with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
                assert archive.namelist()
                assert not any('Konvertierungsoptionen_' in name for name in archive.namelist())
        report = next(file for file in manifest['artifacts'] if file['report'])
        assert client.get(f"/api/jobs/{csv_job['id']}/artifacts/{report['id']}").status_code == 200
        print('PASS: two independent CSV variants, equal JSON/XML resource counts, no double-counted NDJSON and separate dataset downloads')
assert (REPO / 'input' / 'FHIR_Testdatengenerator_Vorlage.xlsx').read_bytes() == original
