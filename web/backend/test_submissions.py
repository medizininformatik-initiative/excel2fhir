from concurrent.futures import ThreadPoolExecutor
import json
from unittest.mock import patch
from uuid import uuid4
from fastapi.testclient import TestClient
from api import app
import inputs
import store
from test_queue import QueueFixture

TEXT = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n'
METADATA = {'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1}

class SubmissionTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)
        validation = patch.object(store, 'validate_configuration', return_value=METADATA)
        validation.start(); self.addCleanup(validation.stop)
        inspection = patch.object(inputs, 'inspect', return_value={'sheets': []})
        inspection.start(); self.addCleanup(inspection.stop)

    def test_submission_retry_preserves_exact_editor_snapshot_and_name(self):
        body = {'requestId': str(uuid4()), 'datasetName': ' Meine Testdaten ä ', 'configurationProperties': TEXT}
        first = self.client.post('/api/jobs', json=body)
        self.assertEqual(201, first.status_code)
        self.assertEqual(first.json()['id'], self.client.post('/api/jobs', json=body).json()['id'])
        self.assertEqual('Meine Testdaten ä', first.json()['dataset_name'])
        self.assertEqual(409, self.client.post('/api/jobs', json={**body, 'configurationProperties': TEXT+'# changed'}).status_code)
        self.assertEqual(1, len(store.jobs()))

    def test_editor_load_uses_saved_input_without_starting_a_job(self):
        job = store.create('starter', 'default', TEXT, dataset_name='Example')
        folder = store.ROOT / 'jobs' / job
        before = (folder / 'snapshot.json').read_bytes()
        (store.APP / store.SOURCES['starter']).write_bytes(b'changed')
        loaded = self.client.post(f'/api/jobs/{job}/editor').json()
        self.assertEqual(TEXT, loaded['configurationProperties'])
        self.assertEqual('Example', loaded['datasetName'])
        self.assertEqual(b'workbook', inputs.resolve(loaded['source'])[0].read_bytes())
        self.assertEqual(1, len(store.jobs()))
        self.assertEqual(before, (folder / 'snapshot.json').read_bytes())
        loaded['configurationProperties'] = TEXT+'PID_PREFIX=edited-\n'
        response = self.client.post('/api/jobs', json=loaded)
        self.assertEqual(201, response.status_code, response.text)
        self.assertEqual(2, len(store.jobs()))

    def test_editor_load_rejects_changed_input_and_cross_origin(self):
        job = store.create('starter', 'default', TEXT)
        url = f'/api/jobs/{job}/editor'
        self.assertEqual(403, self.client.post(url, headers={'Origin':'https://other.invalid'}).status_code)
        path = store.ROOT / 'jobs' / job / 'input.xlsx'
        path.chmod(0o644); path.write_bytes(b'changed')
        self.assertEqual(422, self.client.post(url).status_code)

    def test_batch_and_direct_repeat_routes_are_absent(self):
        job = store.create('starter', 'default', TEXT)
        self.assertEqual(404, self.client.post('/api/job-batches', json={}).status_code)
        self.assertEqual(404, self.client.post(f'/api/jobs/{job}/repeat', json={}).status_code)
