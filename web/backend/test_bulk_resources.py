import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, Mock

import bulk_resources as resources
import generation
import store
import synthea_runtime
import worker
from fastapi.testclient import TestClient
from api import app
from test_generation import CATALOG
from test_queue import QueueFixture


class MemoryTests(unittest.TestCase):
    def test_memory_boundary_unknown_and_worker_headroom(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(resources, 'MEMINFO', root / 'meminfo'), patch.object(resources, 'CGROUP', root):
                self.assertFalse(resources.status()['ready'])
                (root / 'meminfo').write_text('MemAvailable: 3145727 kB\n')
                with self.assertRaises(ValueError): resources.require_available()
                (root / 'meminfo').write_text('MemAvailable: 3145728 kB\n')
                resources.require_available()
                with self.assertRaises(ValueError): resources.require_available(container=True)
                (root / 'memory.max').write_text(str(4 * 1024**3))
                (root / 'memory.current').write_text(str(2 * 1024**3))
                (root / 'memory.stat').write_text(f'inactive_file {1024**3}\n')
                resources.require_available(container=True)
                (root / 'memory.stat').write_text('inactive_file 0\n')
                with self.assertRaisesRegex(ValueError, 'headroom'): resources.require_available(container=True)


class AdmissionTests(QueueFixture):
    def test_api_rejects_without_queuing_and_worker_rechecks_after_admission(self):
        synthea = self.root / 'synthea'
        (synthea / 'target').mkdir(parents=True)
        (synthea / 'target/synthea.jar').write_bytes(b'generator')
        with patch.object(generation, 'catalogue', return_value=CATALOG), patch.object(synthea_runtime, 'fingerprint', return_value='importer'), patch.object(synthea_runtime, 'ROOT', synthea):
            client = TestClient(app)
            request = {'source': 'synthea-generation', 'generation': {'population': 2, 'executionMode': 'bulk'}}
            with patch.object(resources, 'status', return_value={'ready': False, 'availableBytes': 1024}):
                self.assertEqual(422, client.post('/api/jobs', json=request).status_code)
                self.assertEqual([], store.jobs())
            with patch.object(resources, 'status', return_value={'ready': True}):
                job = client.post('/api/jobs', json=request).json()['id']
            snapshot_path = store.ROOT / 'jobs' / job / 'snapshot.json'
            snapshot = json.loads(snapshot_path.read_text())
            snapshot['syntheaGeneratorSha256'] = store.digest(synthea_runtime.ROOT / 'target/synthea.jar')
            snapshot_path.chmod(0o644)
            snapshot_path.write_text(json.dumps(snapshot))
            store.claim()
            with patch.object(resources, 'require_available', side_effect=ValueError('RAM changed')), patch.object(worker.subprocess, 'Popen') as launch:
                worker.execute(job)
            launch.assert_not_called()
            self.assertEqual('failed', store.get(job)['state'])
            self.assertIn('RAM changed', (store.ROOT / 'jobs' / job / 'converter.log').read_text())

    def test_service_commands_require_idle_queue_and_transfers(self):
        resources.require_idle()
        job = store.create('starter', 'default')
        with self.assertRaises(ValueError): resources.require_idle()
        store.finish(job, 'succeeded')
        with store.connect() as db:
            db.execute("INSERT INTO uploads(id,state,created,descriptor) VALUES ('transfer','uploading',1,'{}')")
        with self.assertRaises(ValueError): resources.require_idle()
