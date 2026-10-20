"""Native bulk output retains all generator bytes and resumes without a KDS config."""
import gzip
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout
import zipfile

import bulk_archives
import bulk_generation as bulk
import generation


class NativeBulkTests(unittest.TestCase):
    def test_native_packages_preserve_bytes_providers_counts_and_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / 'job'
            folder.mkdir()
            settings = generation.Settings(executionMode='bulk', outputMode='synthea',
                                           population=101, patientsPerRun=100, patientSeed='1000').model_dump(mode='json')
            (folder / 'snapshot.json').write_text(json.dumps({'generation': settings,
                'bulkLayoutVersion': 3, 'bulkOutputName': 'native'}))
            originals = {}
            seeds = []
            fail = [True]
            def process(command, log, update=None):
                self.assertIn('--exporter.hospital.fhir.export=true', command)
                self.assertIn('--exporter.practitioner.fhir.export=true', command)
                self.assertNotIn('-cp', command)
                seed = command[command.index('-s') + 1]
                size = int(command[command.index('-p') + 1])
                seeds.append(seed)
                if seed == '1001' and fail[0]:
                    raise RuntimeError('deliberate interruption')
                target = log.parent / 'synthea/fhir'
                target.mkdir(parents=True)
                values = []
                for index in range(size):
                    values.append(('patient-' + str(index), ('{ "resourceType": "Bundle", "type":"transaction", "entry":['
                        '{"resource":{"resourceType":"Patient","id":"p-' + seed + '-' + str(index) + '",'
                        '"extension":[{"url":"urn:test:precision","valueDecimal":1.234567890123456789}]}}]}\n').encode()))
                for kind in ('Organization', 'Practitioner'):
                    values.append((kind, json.dumps({'resourceType': 'Bundle', 'type': 'collection',
                        'entry': [{'resource': {'resourceType': kind, 'id': kind.lower()}}]}).encode()))
                originals[seed] = sorted(raw for _, raw in values)
                for name, raw in values:
                    (target / (name + '.json')).write_bytes(raw)
            with patch.object(bulk, 'OUTPUT_ROOT', root / 'output'), patch.object(bulk, 'checked_process', side_effect=process), redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'deliberate interruption'):
                    bulk.run(folder)
                archive = root / 'output/native/run-00001-package-00001-100Pat.zip'
                saved = archive.read_bytes()
                fail[0] = False
                bulk.run(folder)
                self.assertEqual(saved, archive.read_bytes())
                paths = sorted((root / 'output/native').glob('*.zip'))
                self.assertEqual(2, len(paths))
                for seed, path in zip(('1000', '1001'), paths):
                    manifest = bulk_archives.read_manifest(path)
                    with zipfile.ZipFile(path) as package:
                        values = sorted(gzip.decompress(package.read(name)) for name in manifest['files'])
                    self.assertEqual(originals[seed], values)
                    self.assertEqual(2, sum(v.get('role') == 'support' for v in manifest['files'].values()))
                # A completed job also resumes without touching either package.
                bulk.run(folder)
            self.assertEqual(['1000', '1001', '1001'], seeds)
            self.assertFalse((folder / 'default.config').exists())
            self.assertFalse((folder / 'bulk-work').exists())
            self.assertEqual([], list((root / 'output').rglob('*.json.gz')))
            published = json.loads((folder / 'datasets.json').read_text())['datasets']
            self.assertEqual([100, 1], [item['inspection']['patients'] for item in published])
            self.assertEqual(101, json.loads((folder / 'bulk-progress.json').read_text())['completed'])

    def test_native_compression_rejects_missing_patients(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / 'source'; source.mkdir()
            output = root / 'output'; output.mkdir()
            with self.assertRaisesRegex(RuntimeError, 'expected 1'):
                bulk.compress_native(source, output, 1, lambda **_: None)
