"""Check resolved Compose combinations without starting or changing services."""
import hashlib
import itertools
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ComposeProfilesTest(unittest.TestCase):
    def test_combinations_preserve_service_and_storage_boundaries(self):
        for count in range(4):
            for profiles in itertools.combinations(['blaze', 'hapi', 'data-portal'], count):
                with self.subTest(profiles=profiles):
                    command = ['docker', 'compose', '-f', str(ROOT / 'web/compose.yml')]
                    for profile in profiles:
                        command += ['--profile', profile]
                    config = json.loads(subprocess.check_output(command + ['config', '--format', 'json']))
                    services = config['services']
                    expected = {'api', 'worker', 'web'}
                    if 'blaze' in profiles or 'data-portal' in profiles:
                        expected.add('blaze')
                    if 'hapi' in profiles:
                        expected.update(['hapi', 'hapi-db'])
                    if 'data-portal' in profiles:
                        expected.update(['portal-init', 'dataportal-ui', 'dataportal-backend',
                                         'dataportal-postgres', 'dataportal-elastic', 'init-elasticsearch',
                                         'auth', 'auth-db', 'dataportal-nginx', 'availability-updater',
                                         'torch', 'torch-nginx', 'fhir-data-evaluator'])
                        self.assertEqual(0, services['fhir-data-evaluator']['scale'])
                        self.assertEqual('http://blaze:8080/fhir', services['dataportal-backend']['environment']['CQL_SERVER_BASE_URL'])
                    self.assertEqual(expected, set(services))
                    published = []
                    for service in services.values():
                        self.assertFalse(service.get('privileged', False))
                        self.assertNotEqual('host', service.get('network_mode'))
                        for mount in service.get('volumes', []):
                            self.assertNotIn('docker.sock', str(mount))
                        for port in service.get('ports', []):
                            self.assertEqual('127.0.0.1', port['host_ip'])
                            published.append(port['published'])
                    self.assertEqual(len(published), len(set(published)))
                    for name, volume in config.get('volumes', {}).items():
                        self.assertEqual(f"{config['name']}_{name}", volume['name'])
                    if 'blaze' in services:
                        self.assertEqual('blaze-data', services['blaze']['volumes'][0]['source'])

    def test_pinned_upstream_files(self):
        root = ROOT / 'web/deployment/upstream'
        lock = json.loads((root / 'source.json').read_text())
        for name, digest in lock['files'].items():
            self.assertEqual(digest, hashlib.sha256((root / name).read_bytes()).hexdigest(), name)


if __name__ == '__main__':
    unittest.main()
