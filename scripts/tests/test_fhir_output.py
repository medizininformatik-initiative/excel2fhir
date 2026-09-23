import bz2
import gzip
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fhir_output import read_output


class OutputComparisonTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.shared = {'resourceType': 'Location', 'id': 'ward', 'name': 'Station'}
        self.bundles = [{'resourceType': 'Bundle', 'entry': [
            {'resource': {'resourceType': 'Patient', 'id': str(index)}},
            {'resource': self.shared}]} for index in range(2)]

    def test_split_bundles_and_shared_resources_in_each_representation(self):
        for suffix in ('.json', '.ndjson', '.json.gz', '.json.bz2', '.json.zip'):
            with self.subTest(format=suffix):
                folder = self.directory / suffix
                folder.mkdir()
                for index, bundle in enumerate(self.bundles):
                    path = folder / (str(index) + suffix)
                    data = json.dumps(bundle).encode()
                    if suffix == '.json.gz':
                        path.write_bytes(gzip.compress(data))
                    elif suffix == '.json.bz2':
                        path.write_bytes(bz2.compress(data))
                    elif suffix == '.json.zip':
                        with zipfile.ZipFile(path, 'w') as archive:
                            archive.writestr(str(index) + '.json', data)
                    else:
                        path.write_bytes(data + b'\n')
                result = read_output(folder)
                resources = [e['resource'] for e in result['entry']]
                self.assertEqual(['0', '1'], [r['id'] for r in resources if r['resourceType'] == 'Patient'])
                self.assertEqual([self.shared], [r for r in resources if r['resourceType'] == 'Location'])

    def test_parallel_formats_are_read_once(self):
        (self.directory / 'case.json').write_text(json.dumps(self.bundles[0]))
        (self.directory / 'patients.ndjson').write_text(json.dumps(self.bundles[0]) + '\n')
        self.assertEqual(2, len(read_output(self.directory)['entry']))

    def test_ndjson_multiple_lines_and_conflicting_shared_resource(self):
        path = self.directory / 'patients.ndjson'
        path.write_text('\n'.join(json.dumps(b) for b in self.bundles) + '\n')
        self.assertEqual(3, len(read_output(self.directory)['entry']))
        self.bundles[1]['entry'][1] = {'resource': dict(self.shared, name='Other ward')}
        path.write_text('\n'.join(json.dumps(b) for b in self.bundles))
        with self.assertRaisesRegex(ValueError, 'Conflicting shared resource'):
            read_output(self.directory)

    def test_missing_output_is_reported(self):
        with self.assertRaisesRegex(ValueError, 'FHIR output is missing'):
            read_output(self.directory)
