import gzip
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import archive_names
import bulk_archives


class ArchiveNameTests(unittest.TestCase):
    def test_actual_unique_output_patients_across_formats(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'result.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('output/run/fhir/p.json.gz', gzip.compress(json.dumps({
                    'resourceType': 'Bundle', 'entry': [{'resource': {'resourceType': 'Patient', 'id': 'p'}}]
                }).encode()))
                archive.writestr('output/run/fhir/p.xml', '<Patient xmlns="http://hl7.org/fhir"><id value="p"/></Patient>')
                archive.writestr('output/run/fhir/q.json', '{"resourceType":"Patient","id":"q"}')
                archive.writestr('output/run/fhir/org.json', '{"resourceType":"Organization","id":"org"}')
                archive.writestr('output/run/details/source.json', '{"resourceType":"Patient","id":"excluded"}')
            self.assertEqual(2, archive_names.patient_count(path, result=True))

    def test_short_safe_names(self):
        self.assertEqual('excel2fhir-17Pat-id.zip', archive_names.filename('', 17, 'id'))
        self.assertEqual('Kohorte-17Pat.zip', archive_names.filename('Kohorte', 17, 'id'))
        self.assertEqual('Test-Name-0Pat.zip', archive_names.filename('../Test/Name', 0, 'id'))

    def test_partial_package_excludes_support_and_resumes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            part = root / 'part-00001'
            part.mkdir()
            for name in ['p.json.gz', 'support-org.json.gz']:
                (part / name).write_bytes(b'content')
            base = root / 'packages/run-00001-package-00001.zip'
            target = bulk_archives.package_path(root, [part.name], base)
            self.assertEqual('run-00001-package-00001-1Pat.zip', target.name)
            bulk_archives.package(root, [part.name], target, lambda *_: None)
            self.assertEqual(target, bulk_archives.package_path(root, [part.name], base))
            bulk_archives.package(root, [part.name], target, lambda *_: None)
