from fastapi.testclient import TestClient

from api import app
import store
from test_queue import QueueFixture


class ApiTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)

    def test_submission_snapshot_and_cancel(self):
        result = self.client.post('/api/jobs', json={'source': 'starter', 'profile': 'default'})
        self.assertEqual(201, result.status_code)
        job_id = result.json()['id']
        snapshot = self.client.get(f'/api/jobs/{job_id}/snapshot')
        self.assertEqual('starter', snapshot.json()['source'])
        self.assertEqual(409, self.client.get(f'/api/jobs/{job_id}/download').status_code)
        self.assertEqual(202, self.client.post(f'/api/jobs/{job_id}/cancel').status_code)
        self.assertEqual('cancelled', self.client.get('/api/jobs').json()[0]['state'])
        self.assertEqual(snapshot.content, self.client.get(f'/api/jobs/{job_id}/snapshot').content)

    def test_rejects_untrusted_origin_host_and_unknown_input(self):
        self.assertEqual(403, self.client.post('/api/jobs', json={}, headers={'Origin': 'https://other.invalid'}).status_code)
        self.assertEqual(400, self.client.get('/api/jobs', headers={'Host': 'other.invalid'}).status_code)
        self.assertEqual(422, self.client.post('/api/jobs', json={'source': '/etc/passwd'}).status_code)
        self.assertEqual(422, self.client.post('/api/jobs', json={'command': 'echo injected'}).status_code)
        self.assertEqual(404, self.client.get('/api/jobs/not-a-uuid/snapshot').status_code)
        self.assertEqual([], store.jobs())
