from concurrent.futures import ThreadPoolExecutor
import json
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient
from api import app
import configurations
import store
from test_queue import QueueFixture

TEXT = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n'
METADATA = {'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1}


class SubmissionTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)
        validation = patch.object(store, 'validate_configuration', return_value=METADATA)
        self.validate = validation.start()
        self.addCleanup(validation.stop)

    def test_dataset_name_is_metadata_and_survives_retry_batch_and_repeat(self):
        request = {'requestId': str(uuid4()), 'source': 'starter', 'profile': 'default', 'datasetName': '  Meine Testdaten ä  '}
        response = self.client.post('/api/jobs', json=request)
        self.assertEqual(201, response.status_code, response.text)
        named = response.json()
        self.assertEqual('Meine Testdaten ä', named['dataset_name'])
        self.assertEqual(named['id'], self.client.post('/api/jobs', json=request).json()['id'])
        self.assertEqual(409, self.client.post('/api/jobs', json={**request, 'datasetName': 'Other'}).status_code)
        plain = self.client.post('/api/jobs', json={'source': 'starter', 'profile': 'default'}).json()
        named_snapshot = self.snapshot(named)
        self.assertEqual('Meine Testdaten ä', named_snapshot.pop('datasetName'))
        self.assertEqual(self.snapshot(plain), named_snapshot)
        for filename in ('input.xlsx', 'default.config'):
            self.assertEqual((store.ROOT / 'jobs' / plain['id'] / filename).read_bytes(),
                             (store.ROOT / 'jobs' / named['id'] / filename).read_bytes())
        store.finish(named['id'], 'succeeded', 0)
        repeated = self.client.post(f"/api/jobs/{named['id']}/repeat", json={'requestId': str(uuid4())}).json()
        self.assertEqual('Meine Testdaten ä', repeated['dataset_name'])
        batch = self.client.post('/api/job-batches', json={**self.batch(), 'datasetName': 'Batch'}).json()
        self.assertEqual(['Batch', 'Batch'], [job['dataset_name'] for job in batch])
        blank = self.client.post('/api/jobs', json={'datasetName': '   '}).json()
        self.assertIsNone(blank['dataset_name'])
        self.assertNotIn('datasetName', self.snapshot(blank))
        self.assertEqual(422, self.client.post('/api/jobs', json={'datasetName': 'x' * 201}).status_code)

    def batch(self):
        items = [configurations.create(name, TEXT + f'TIME_SHIFT_BASE_DAYS={index}\n')
                 for index, name in enumerate(['First', 'Second'])]
        return {'requestId': str(uuid4()), 'source': 'starter',
                'configurations': [{'id': item['id'], 'revision': item['revision']} for item in items]}

    def snapshot(self, job):
        return json.loads((store.ROOT / 'jobs' / job['id'] / 'snapshot.json').read_text())

    def test_batch_publishes_separate_named_immutable_snapshots_and_retries_once(self):
        request = self.batch()
        response = self.client.post('/api/job-batches', json=request)
        self.assertEqual(201, response.status_code, response.text)
        jobs = response.json()
        self.assertEqual(['First', 'Second'], [job['configuration']['name'] for job in jobs])
        snapshots = [self.snapshot(job) for job in jobs]
        self.assertEqual(snapshots[0]['batchId'], snapshots[1]['batchId'])
        self.assertEqual(snapshots[0]['inputSha256'], snapshots[1]['inputSha256'])
        self.assertNotEqual(snapshots[0]['profile']['optionsProperties'], snapshots[1]['profile']['optionsProperties'])
        configurations.update(request['configurations'][0]['id'], 1, name='Renamed', text=TEXT)
        configurations.delete(request['configurations'][1]['id'], 1)
        retry = self.client.post('/api/job-batches', json=request)
        self.assertEqual([job['id'] for job in jobs], [job['id'] for job in retry.json()])
        self.assertEqual(2, len(store.jobs()))
        self.assertEqual(snapshots, [self.snapshot(job) for job in jobs])
        self.assertEqual(jobs[0]['id'], store.claim())
        store.cancel(jobs[1]['id'])
        self.assertIsNone(store.claim())

    def test_invalid_or_stale_batch_publishes_nothing_and_cleans_prepared_files(self):
        request = self.batch()
        with patch.object(store, 'validate_configuration', side_effect=[METADATA, ValueError('invalid second configuration')]):
            response = self.client.post('/api/job-batches', json=request)
        self.assertEqual(422, response.status_code)
        self.assertEqual([], store.jobs())
        self.assertEqual([], list((store.ROOT / 'jobs').iterdir()))
        configurations.update(request['configurations'][1]['id'], 1, name='Changed')
        self.assertEqual(409, self.client.post('/api/job-batches', json=request).status_code)
        self.assertEqual([], store.jobs())
        request['configurations'] = [request['configurations'][0]] * 2
        self.assertEqual(422, self.client.post('/api/job-batches', json=request).status_code)

    def test_submission_ids_are_transactional_and_cannot_be_reused_for_other_settings(self):
        request_id = str(uuid4())
        def submit(_):
            return store.create('starter', 'default', TEXT, request_id)
        with ThreadPoolExecutor(max_workers=4) as pool:
            ids = list(pool.map(submit, range(4)))
        self.assertEqual(1, len(set(ids)))
        self.assertEqual(1, len(store.jobs()))
        self.assertEqual(1, len(list((store.ROOT / 'jobs').iterdir())))
        response = self.client.post('/api/jobs', json={'requestId': request_id, 'profile': 'workbook'})
        self.assertEqual(409, response.status_code)

    def test_repeat_uses_saved_files_after_source_and_configuration_changes(self):
        jobs = self.client.post('/api/job-batches', json=self.batch()).json()
        original = jobs[0]
        before = self.snapshot(original)
        store.finish(original['id'], 'failed', 1)
        configurations.delete(original['configuration']['id'], 1)
        (store.APP / store.SOURCES['starter']).write_bytes(b'changed workbook')
        (store.APP / 'excel2fhir.jar').write_bytes(b'new converter')
        payload = {'requestId': str(uuid4())}
        response = self.client.post(f"/api/jobs/{original['id']}/repeat", json=payload)
        self.assertEqual(201, response.status_code, response.text)
        repeated = response.json()
        snapshot = self.snapshot(repeated)
        self.assertEqual(before['profile'], snapshot['profile'])
        self.assertEqual(before['inputSha256'], snapshot['inputSha256'])
        self.assertNotEqual(before['converterSha256'], snapshot['converterSha256'])
        self.assertEqual(before['converterSha256'], snapshot['repeatedFrom']['converterSha256'])
        self.assertEqual(original['id'], repeated['repeated_from'])
        self.assertNotIn('batchId', snapshot)
        self.assertEqual(repeated['id'], self.client.post(f"/api/jobs/{original['id']}/repeat", json=payload).json()['id'])
        self.assertEqual(before, self.snapshot(original))

    def test_repeat_workbook_and_reject_active_or_tampered_input(self):
        job_id = store.create('starter', 'workbook')
        path = f'/api/jobs/{job_id}/repeat'
        self.assertEqual(409, self.client.post(path, json={'requestId': str(uuid4())}).status_code)
        store.cancel(job_id)
        response = self.client.post(path, json={'requestId': str(uuid4())})
        self.assertEqual(201, response.status_code)
        self.assertEqual('workbook', self.snapshot(response.json())['profile']['id'])
        original_input = store.ROOT / 'jobs' / job_id / 'input.xlsx'
        original_input.chmod(0o644)
        original_input.write_bytes(b'tampered')
        self.assertEqual(422, self.client.post(path, json={'requestId': str(uuid4())}).status_code)
        self.assertEqual(2, len(store.jobs()))

    def test_batch_and_repeat_reject_untrusted_origins_and_malformed_requests(self):
        payload = self.batch()
        self.assertEqual(403, self.client.post('/api/job-batches', json=payload, headers={'Origin': 'https://other.invalid'}).status_code)
        payload['configurations'][0]['revision'] = True
        self.assertEqual(422, self.client.post('/api/job-batches', json=payload).status_code)
        self.assertEqual([], store.jobs())
