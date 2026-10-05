import io
import json
from pathlib import Path
from unittest.mock import Mock, patch
from uuid import uuid4
import zipfile

from fastapi.testclient import TestClient
from api import app
import inputs
import store
import synthea_runtime
import worker
from test_queue import QueueFixture


def bundle(patient='p1'):
    return json.dumps({'resourceType': 'Bundle', 'entry': [{'resource': {'resourceType': 'Patient', 'id': patient}}]}).encode()


class SyntheaInputTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)
        self.runtime = patch.object(synthea_runtime, 'ROOT', Path(__file__).resolve().parents[2])
        self.runtime.start()
        self.addCleanup(self.runtime.stop)

    def archive(self, files):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            for name, data in files:
                archive.writestr(name, data)
        return stream.getvalue()

    def test_real_json_inspection_and_worker_command_use_synthea_pipeline(self):
        response = self.client.post('/api/inputs?filename=patient.json', content=bundle())
        self.assertEqual(201, response.status_code, response.text)
        item = response.json()
        self.assertEqual('synthea', item['kind'])
        self.assertEqual(1, item['inspection']['patients'])
        job = store.create(item['id'], 'workbook')
        self.assertEqual(job, store.claim())
        process = Mock(returncode=0)
        process.poll.return_value = 0
        with patch.object(worker.subprocess, 'Popen', return_value=process) as launch:
            worker.execute(job)
        self.assertIn(str(synthea_runtime.ROOT / 'scripts/run_synthea_cases.py'), launch.call_args.args[0])
        self.assertNotIn('--converter-options', launch.call_args.args[0])
        self.assertEqual('succeeded', store.get(job)['state'])
        repeated = store.repeat(job, str(uuid4()))
        folder = store.ROOT / 'jobs' / repeated
        self.assertEqual(bundle(), (folder / 'input.json').read_bytes())
        self.assertEqual(synthea_runtime.fingerprint(), json.loads((folder / 'snapshot.json').read_text())['repeatedFrom']['syntheaImportSha256'])

    def test_zip_inspection_counts_patients_and_bundles_to_skip(self):
        data = self.archive([('one.json', bundle()), ('two.json', bundle('p2')),
                             ('providers.json', json.dumps({'resourceType': 'Bundle', 'entry': []}))])
        response = self.client.post('/api/inputs?filename=patients.zip', content=data)
        self.assertEqual(201, response.status_code, response.text)
        item = response.json()
        self.assertEqual('synthea-zip', item['kind'])
        self.assertEqual(2, item['inspection']['patients'])
        self.assertEqual(1, item['inspection']['bundlesWithoutPatient'])
        path, _, _ = inputs.resolve(item['id'])
        self.assertEqual(data, path.read_bytes())

    def test_malformed_and_duplicate_patients_are_rejected_before_submission(self):
        for data in [b'[]', b'{}', b'not JSON', b'{"resourceType":"Bundle","entry":[null]}',
                     b'{"resourceType":"Bundle","entry":[]}']:
            self.assertEqual(422, self.client.post('/api/inputs?filename=invalid.json', content=data).status_code)
        for files in [[('one.json', bundle()), ('two.json', bundle())],
                      [('../patient.json', bundle())], [('Person.csv', 'p'), ('patient.json', bundle())]]:
            self.assertEqual(422, self.client.post('/api/inputs?filename=invalid.zip', content=self.archive(files)).status_code)
        self.assertEqual([], inputs.summaries())

    def test_importer_change_is_rejected_before_execution(self):
        item = self.client.post('/api/inputs?filename=patient.json', content=bundle()).json()
        job = store.create(item['id'], 'workbook')
        store.claim()
        with patch.object(synthea_runtime, 'fingerprint', return_value='changed'), patch.object(worker.subprocess, 'Popen') as launch:
            worker.execute(job)
        launch.assert_not_called()
        self.assertEqual('failed', store.get(job)['state'])
