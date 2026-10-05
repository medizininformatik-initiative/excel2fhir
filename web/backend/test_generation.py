import json
from pathlib import Path
from unittest.mock import Mock, patch
from uuid import uuid4

from fastapi.testclient import TestClient
from api import app
import configurations
import generation
import store
import synthea_runtime
import worker
from test_queue import QueueFixture

CATALOG = {'revision': 'revision', 'sha256': 'generator', 'locations': {'Massachusetts': ['Boston']},
           'modules': [{'id': 'diabetes'}], 'keepModules': [{'id': 'keep_diabetes.json'}]}


class GenerationTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)
        for item in [patch.object(generation, 'catalogue', return_value=CATALOG),
                     patch.object(synthea_runtime, 'fingerprint', return_value='importer')]:
            item.start(); self.addCleanup(item.stop)

    def test_invalid_settings_do_not_create_jobs(self):
        for settings in [{'population': 0}, {'population': True}, {'minAge': 81}, {'patientSeed': str(2**63)},
                         {'state': '--some-option'}, {'city': 'Unknown'}, {'modules': ['../../file']},
                         {'keepModule': '/tmp/file'}, {'patientFilter': 'unknown'}, {'unknown': True},
                         {'population': 2, 'singlePersonSeed': '1'}, {'endDate': '2099-01-01'},
                         {'referenceDate': '2026-09-13', 'endDate': '2026-09-12'}]:
            response = self.client.post('/api/jobs', json={'source': 'synthea-generation', 'profile': 'workbook', 'generation': settings})
            self.assertEqual(422, response.status_code, response.text)
        self.assertEqual([], store.jobs())

    def test_fixed_settings_are_snapshotted_and_repeat_preserves_them(self):
        settings = generation.normalize({'patientSeed': '-9223372036854775808', 'modules': ['diabetes']})
        request = {'source': 'synthea-generation', 'profile': 'workbook', 'generation': settings, 'requestId': str(uuid4())}
        response = self.client.post('/api/jobs', json=request)
        self.assertEqual(201, response.status_code, response.text)
        job = response.json()['id']
        self.assertEqual(job, self.client.post('/api/jobs', json=request).json()['id'])
        folder = store.ROOT / 'jobs' / job
        snapshot = json.loads((folder / 'snapshot.json').read_text())
        self.assertEqual(settings, snapshot['generation'])
        self.assertEqual('generator', snapshot['syntheaGeneratorSha256'])
        store.cancel(job)
        repeated = store.repeat(job, str(uuid4()))
        self.assertEqual((folder / 'generation.json').read_bytes(), (store.ROOT / 'jobs' / repeated / 'generation.json').read_bytes())
        original = json.loads((store.ROOT / 'jobs' / repeated / 'snapshot.json').read_text())['repeatedFrom']
        self.assertEqual('generator', original['syntheaGeneratorSha256'])

    def test_independent_configurations_share_exact_generation_settings(self):
        with patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False, 'patientsPerFile': 1}):
            items = [configurations.create(name, 'CONFIGURATION_VERSION=1\n') for name in ['One', 'Two']]
            jobs = configurations.start_jobs('synthea-generation', [{'id': i['id'], 'revision': 1} for i in items], str(uuid4()), {})
        self.assertEqual(2, len(jobs))
        snapshots = [json.loads((store.ROOT / 'jobs' / job / 'snapshot.json').read_text()) for job in jobs]
        self.assertEqual(snapshots[0]['inputSha256'], snapshots[1]['inputSha256'])
        self.assertNotEqual(snapshots[0]['profile']['id'], snapshots[1]['profile']['id'])

    def test_arguments_keep_paths_and_export_destinations_out_of_user_settings(self):
        values = generation.normalize({'modules': ['diabetes'], 'keepModule': 'keep_diabetes.json',
                                       'patientFilter': 'alive', 'city': 'Boston'})
        args = generation.arguments(values)
        self.assertIn('--generate.only_alive_patients=true', args)
        self.assertIn('--generate.only_dead_patients=false', args)
        self.assertEqual(['-m', 'diabetes'], args[args.index('-m'):args.index('-m') + 2])
        self.assertEqual(['Massachusetts', 'Boston'], args[-2:])
        self.assertNotIn('-c', args)
        with self.assertRaises(ValueError):
            store.create('starter', 'workbook', generation_settings={})
