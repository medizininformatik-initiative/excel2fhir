from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient
import engine
from server import app


class ControllerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        root = patch.object(engine, 'ROOT', self.root)
        root.start(); self.addCleanup(root.stop)
        self.identifier = str(uuid4())
        (self.root / self.identifier).mkdir()
        self.item = {'id': self.identifier, 'name': 'Test node', 'project': 'excel2fhir-diz-' + self.identifier.replace('-', ''),
                     'state': 'stopped', 'created': 1, 'ports': {}, 'dataset': {'id': 'retained-data'}}
        with patch.object(engine, 'host_root', return_value=Path('/host/environments')):
            engine.write_compose(self.item)
        self.client = TestClient(app)

    def test_compose_uses_owned_project_loopback_ports_and_host_mount_paths(self):
        content = (self.root / self.identifier / 'compose.yml').read_text()
        self.assertIn('name: ' + self.item['project'], content)
        self.assertIn('127.0.0.1::8080', content)
        self.assertIn('127.0.0.1::8443', content)
        self.assertIn('/host/environments/' + self.identifier + '/data-node/auth/cert.pem:/etc/nginx/certs/cert.pem:ro', content)
        self.assertIn('ports: !override []', content)
        self.assertNotIn('/var/run/docker.sock', content)
        self.assertNotIn('network_mode: host', content)

    def test_actions_are_allowlisted_and_only_canonical_ids_resolve(self):
        with patch.object(engine, 'command') as command:
            for identifier in ['../outside', self.identifier.upper(), 'arbitrary-container']:
                with self.assertRaises(FileNotFoundError):
                    engine.read(identifier)
            with self.assertRaises(ValueError):
                engine.submit(self.identifier, 'down --volumes')
        command.assert_not_called()
        self.assertEqual(404, self.client.post(f'/environments/{self.identifier}/exec', json={'command': 'docker ps'}).status_code)
        self.assertEqual(422, self.client.post('/environments', json={'name': 'Test', 'command': 'arbitrary'}).status_code)

    def test_modified_compose_is_rejected_before_docker_execution(self):
        (self.root / self.identifier / 'compose.yml').write_text('changed')
        with patch.object(engine, 'command') as command:
            with self.assertRaises(ValueError):
                engine.compose(self.item, ['stop'])
        command.assert_not_called()

    def test_one_operation_per_environment_is_persisted_before_dispatch(self):
        with patch.object(engine, 'EXECUTOR') as executor:
            result = engine.submit(self.identifier, 'start')
            self.assertEqual('starting', result['state'])
            self.assertEqual('start', engine.read(self.identifier)['operation'])
            executor.submit.assert_called_once_with(engine.operate, self.identifier, 'start')
            with self.assertRaises(ValueError):
                engine.submit(self.identifier, 'stop')

    def test_stop_preserves_dataset_and_volumes(self):
        with patch.object(engine, 'compose', return_value='') as compose, patch.object(engine, 'service_status', return_value=[]):
            engine.operate(self.identifier, 'stop')
        self.assertEqual(['stop', '--timeout', '20', *engine.SERVICES], compose.call_args.args[1])
        result = engine.read(self.identifier)
        self.assertEqual('stopped', result['state'])
        self.assertEqual({'id': 'retained-data'}, result['dataset'])
        self.assertTrue((self.root / self.identifier / 'compose.yml').exists())

    def test_start_records_allocated_ports_after_readiness(self):
        def compose(item, args, timeout=180):
            if args[:1] == ['port']:
                return '127.0.0.1:' + ('32001' if args[1] == 'rev-proxy' else '32002')
            return ''
        with patch.object(engine, 'host_root', return_value=Path('/host/environments')), patch.object(engine, 'compose', side_effect=compose), patch.object(engine, 'helper') as helper, patch.object(engine, 'service_status', return_value=[]):
            engine.operate(self.identifier, 'start')
        self.assertEqual('ready', engine.read(self.identifier)['state'])
        self.assertEqual({'fhir': 32002, 'torch': 32001}, engine.read(self.identifier)['ports'])
        helper.assert_called_once()

    def test_restart_marks_in_flight_operations_as_interrupted_without_docker_actions(self):
        self.item['state'] = 'starting'
        engine.save(self.item)
        with patch.object(engine, 'command') as command:
            engine.recover()
        command.assert_not_called()
        result = engine.read(self.identifier)
        self.assertEqual('error', result['state'])
        self.assertIn('restarted', result['error'])

    def test_public_metadata_does_not_include_controller_paths_or_credentials(self):
        self.item.update(password='private', composeSha256='hash', privatePath='/private')
        engine.save(self.item)
        data = self.client.get('/environments').json()[0]
        for key in ['password', 'composeSha256', 'privatePath', 'project']:
            self.assertNotIn(key, data)
