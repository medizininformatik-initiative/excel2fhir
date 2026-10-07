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
        path, name, _ = inputs.resolve(item['id'])
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

    @patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1})
    def test_uploaded_source_works_in_multiple_runs_and_repeat_is_independent(self, validate):
        item = self.upload()
        with patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1}):
            configs = [configurations.create(name, TEXT) for name in ['One', 'Two']]
            selection = [{'id': c['id'], 'revision': 1} for c in configs]
            jobs = [store.create(item['id'], 'default', config['configurationProperties']) for config in configs]
            for job in jobs:
                saved = json.loads((store.ROOT / 'jobs' / job / 'snapshot.json').read_text())
                self.assertEqual(item['sha256'], saved['inputSha256'])
                self.assertEqual(item['name'], saved['sourceName'])
                self.assertEqual(item['name'], store.get(job)['source_name'])
            path, _, _ = inputs.resolve(item['id'])
            path.chmod(0o644); path.write_bytes(b'changed original')
            with self.assertRaises(ValueError):
                store.create(item['id'], 'default')
            store.finish(jobs[0], 'succeeded', 0)
            with patch.object(inputs, 'inspect', return_value={'sheets': []}):
                loaded = store.editor_input(jobs[0])
            repeated = store.create(loaded['source'], 'default', loaded['configurationProperties'])
            self.assertEqual(item['sha256'], store.digest(store.ROOT / 'jobs' / repeated / 'input.xlsx'))
            self.assertEqual(item['name'], store.get(repeated)['source_name'])

    def test_source_ids_cannot_select_arbitrary_paths(self):
        for source in ['upload:../../etc/passwd', 'upload:' + str(uuid4())]:
            with self.assertRaises(ValueError):
                store.create(source, 'default')
        self.assertEqual([], store.jobs())

    def archive(self, files):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            for name, value in files:
                archive.writestr(name, value)
        return stream.getvalue()

    def test_csv_archives_reject_path_traversal_duplicates_and_non_csv_members(self):
        for files in [[('../outside.csv', 'data')], [('folder/Person.csv', 'data')],
                      [('A.csv', 'data'), ('a.csv', 'data')], [('script.py', 'data')]]:
            response = self.client.post('/api/inputs?filename=cases.zip', content=self.archive(files))
            self.assertEqual(422, response.status_code, response.text)
        self.assertEqual([], inputs.summaries())
        self.assertFalse((store.ROOT / 'outside.csv').exists())

    @patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1})
    def test_csv_inputs_keep_archive_bytes_across_submission_and_repeat(self, validate):
        data = self.archive([('Case_Person.csv', 'Patient-ID\np1\n')])
        item = self.upload('Cases.zip', data)
        path, _, kind = inputs.resolve(item['id'])
        self.assertEqual('csv', kind)
        job = store.create(item['id'], 'default')
        snapshot = json.loads((store.ROOT / 'jobs' / job / 'snapshot.json').read_text())
        self.assertEqual('csv', snapshot['inputKind'])
        self.assertEqual(data, (store.ROOT / 'jobs' / job / 'input.zip').read_bytes())
        store.cancel(job)
        with patch.object(inputs, 'inspect', return_value={'sheets': [], 'kind': 'csv'}):
            loaded = store.editor_input(job)
        repeated = store.create(loaded['source'], 'default', None if (loaded.get('generation') or {}).get('outputMode') == 'synthea' else loaded['configurationProperties'], generation_settings=loaded.get('generation'))
        self.assertEqual(data, (store.ROOT / 'jobs' / repeated / 'input.zip').read_bytes())
