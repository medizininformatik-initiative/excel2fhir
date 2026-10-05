import io
import json
from unittest.mock import patch
from uuid import uuid4
import zipfile

from fastapi.testclient import TestClient
from api import app
import configurations
import inputs
import store
from test_queue import QueueFixture

REPORT = {'valid': True, 'errors': 0, 'warnings': 0, 'issues': [], 'sheets': [{'name': 'Person', 'rows': 2}]}
TEXT = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n'


class InputTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)

    def upload(self, name='Eigene Fälle.xlsx', content=b'exact original workbook bytes'):
        with patch.object(inputs, 'inspect', return_value=REPORT):
            response = self.client.post('/api/inputs', params={'filename': name}, content=content)
        self.assertEqual(201, response.status_code, response.text)
        return response.json()

    def test_upload_is_persistent_and_preserves_original_bytes_and_name(self):
        item = self.upload()
        path, name = inputs.resolve(item['id'])
        self.assertEqual('Eigene Fälle.xlsx', name)
        self.assertEqual(b'exact original workbook bytes', path.read_bytes())
        self.assertEqual(item, self.client.get('/api/inputs').json()[0])
        self.assertEqual(store.digest(path), item['sha256'])
        self.assertEqual([], list((store.ROOT / 'inputs').glob('.incoming-*')))

    def test_invalid_names_origins_size_and_workbooks_are_not_published(self):
        for name in ['../file.xlsx', '/tmp/file.xlsx', 'file.csv', 'bad\nname.xlsx']:
            self.assertEqual(422, self.client.post('/api/inputs', params={'filename': name}, content=b'data').status_code)
        self.assertEqual(403, self.client.post('/api/inputs?filename=test.xlsx', content=b'data', headers={'Origin': 'https://other.invalid'}).status_code)
        with patch.object(inputs, 'MAX_UPLOAD', 3):
            self.assertEqual(413, self.client.post('/api/inputs?filename=test.xlsx', content=b'data').status_code)
        self.assertEqual(422, self.client.post('/api/inputs?filename=test.xlsx', content=b'not zip').status_code)
        self.assertEqual([], inputs.summaries())
        self.assertEqual([], list((store.ROOT / 'inputs').glob('.incoming-*')))

    def test_failed_structure_inspection_and_expansion_limit_do_not_publish(self):
        with patch.object(inputs, 'inspect', side_effect=ValueError('Invalid structure')):
            self.assertEqual(422, self.client.post('/api/inputs?filename=test.xlsx', content=b'data').status_code)
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('xl/workbook.xml', 'x' * 100)
            archive.writestr('[Content_Types].xml', 'x')
        with patch.object(inputs, 'MAX_UNCOMPRESSED', 50):
            self.assertEqual(422, self.client.post('/api/inputs?filename=test.xlsx', content=stream.getvalue()).status_code)
        self.assertEqual([], inputs.summaries())

    def test_uploaded_source_works_in_multiple_runs_and_repeat_is_independent(self):
        item = self.upload()
        with patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1}):
            configs = [configurations.create(name, TEXT) for name in ['One', 'Two']]
            selection = [{'id': c['id'], 'revision': 1} for c in configs]
            jobs = configurations.start_jobs(item['id'], selection, str(uuid4()))
            for job in jobs:
                saved = json.loads((store.ROOT / 'jobs' / job / 'snapshot.json').read_text())
                self.assertEqual(item['sha256'], saved['inputSha256'])
                self.assertEqual(item['name'], saved['sourceName'])
                self.assertEqual(item['name'], store.get(job)['source_name'])
            path, _ = inputs.resolve(item['id'])
            path.chmod(0o644); path.write_bytes(b'changed original')
            with self.assertRaises(ValueError):
                store.create(item['id'], 'workbook')
            store.finish(jobs[0], 'succeeded', 0)
            repeated = store.repeat(jobs[0], str(uuid4()))
            self.assertEqual(item['sha256'], store.digest(store.ROOT / 'jobs' / repeated / 'input.xlsx'))
            self.assertEqual(item['name'], store.get(repeated)['source_name'])

    def test_source_ids_cannot_select_arbitrary_paths(self):
        for source in ['upload:../../etc/passwd', 'upload:' + str(uuid4())]:
            with self.assertRaises(ValueError):
                store.create(source, 'workbook')
        self.assertEqual([], store.jobs())
