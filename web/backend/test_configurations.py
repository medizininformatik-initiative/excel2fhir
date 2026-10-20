from concurrent.futures import ThreadPoolExecutor
import json
from unittest.mock import patch

from fastapi.testclient import TestClient
from api import app
import configurations
import store
from test_queue import QueueFixture

TEXT = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\n'


class ConfigurationTests(QueueFixture):
    def setUp(self):
        super().setUp()
        self.client = TestClient(app)
        self.validation = patch.object(store, 'validate_configuration', return_value={'formats': ['NDJSON'], 'validation': False, 'patientsPerFile': 1})
        self.validate = self.validation.start()
        self.addCleanup(self.validation.stop)

    def create(self, name='Meine Konfiguration'):
        response = self.client.post('/api/configurations', json={'name': name, 'configurationProperties': TEXT})
        self.assertEqual(201, response.status_code, response.text)
        return response.json()

    def test_save_reload_duplicate_rename_and_delete_preserve_independent_content(self):
        first = self.create()
        self.validate.assert_called_once()
        path = '/api/configurations/' + first['id']
        self.assertEqual(TEXT, self.client.get(path).json()['configurationProperties'])
        self.assertNotIn('configurationProperties', self.client.get('/api/configurations').json()[0])
        second = self.client.post(path + '/duplicate', json={'revision': 1, 'name': 'Kopie'}).json()
        self.assertNotEqual(first['id'], second['id'])
        updated = self.client.patch(path, json={'revision': 1, 'name': 'Neuer Name', 'configurationProperties': TEXT + 'PATIENT_MODE=reference-only\n'})
        self.assertEqual(200, updated.status_code)
        self.assertEqual(2, updated.json()['revision'])
        self.assertEqual(TEXT, self.client.get('/api/configurations/' + second['id']).json()['configurationProperties'])
        self.assertEqual(204, self.client.request('DELETE', path, json={'revision': 2}).status_code)
        self.assertEqual(404, self.client.get(path).status_code)
        self.assertEqual(1, len(self.client.get('/api/configurations').json()))
        self.assertTrue((store.ROOT / 'configurations' / '.trash' / (first['id'] + '.json')).is_file())

    def test_rejects_duplicate_names_invalid_content_and_stale_changes(self):
        first = self.create('Test')
        path = '/api/configurations/' + first['id']
        self.assertEqual(409, self.client.post('/api/configurations', json={'name': ' test ', 'configurationProperties': TEXT}).status_code)
        self.assertEqual(422, self.client.post('/api/configurations', json={'name': '  ', 'configurationProperties': TEXT}).status_code)
        self.assertEqual(200, self.client.patch(path, json={'revision': 1, 'name': 'Renamed'}).status_code)
        for method, suffix, body in [('PATCH', '', {'name': 'Stale'}), ('DELETE', '', {}), ('POST', '/duplicate', {'name': 'Copy'})]:
            self.assertEqual(409, self.client.request(method, path + suffix, json={'revision': 1, **body}).status_code)
        with patch.object(store, 'validate_configuration', side_effect=ValueError('Invalid configuration')):
            self.assertEqual(422, self.client.patch(path, json={'revision': 2, 'configurationProperties': 'bad'}).status_code)
        self.assertEqual(TEXT, self.client.get(path).json()['configurationProperties'])
        self.assertEqual(404, self.client.get('/api/configurations/not-an-id').status_code)

    def test_concurrent_changes_do_not_silently_overwrite(self):
        item = self.create()
        def update(name):
            try:
                configurations.update(item['id'], 1, name=name)
                return 'saved'
            except configurations.Conflict:
                return 'conflict'
        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertCountEqual(['saved', 'conflict'], pool.map(update, ['First', 'Second']))

    def test_job_snapshot_survives_saved_configuration_changes_and_deletion(self):
        item = self.create()
        job = store.create('starter', 'default', item['configurationProperties'])
        snapshot = store.ROOT / 'jobs' / job / 'snapshot.json'
        before = snapshot.read_bytes()
        configurations.update(item['id'], 1, text=TEXT + 'TIME_SHIFT_ENABLED=true\n')
        configurations.delete(item['id'], 2)
        self.assertEqual(before, snapshot.read_bytes())
        self.assertEqual(TEXT, json.loads(before)['profile']['optionsProperties'])

    def test_configurations_persist_in_json_and_mutations_check_origin(self):
        item = self.create()
        self.assertEqual(item, json.loads((store.ROOT / 'configurations' / (item['id'] + '.json')).read_text()))
        self.assertEqual(403, self.client.post('/api/configurations', headers={'Origin': 'https://other.invalid'}, json={'name': 'Other', 'configurationProperties': TEXT}).status_code)

    def test_invalid_mutations_leave_saved_content_and_revision_unchanged(self):
        item = self.create()
        path = '/api/configurations/' + item['id']
        for body in [{'revision': True, 'name': 'Other'},
                     {'revision': '1', 'name': 'Other'},
                     {'revision': 1},
                     {'revision': 1, 'unknown': 'value'},
                     {'revision': 1, 'name': 'Invalid\nname'}]:
            response = self.client.patch(path, json=body)
            self.assertEqual(422, response.status_code, response.text)
            self.assertEqual(item, self.client.get(path).json())
        for method, suffix, body in [('PATCH', '', {'revision': 1, 'name': 'Other'}),
                                     ('POST', '/duplicate', {'revision': 1, 'name': 'Copy'}),
                                     ('DELETE', '', {'revision': 1})]:
            response = self.client.request(method, path + suffix,
                                           headers={'Origin': 'https://other.invalid'}, json=body)
            self.assertEqual(403, response.status_code)
            self.assertEqual(item, self.client.get(path).json())
