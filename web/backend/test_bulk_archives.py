import gzip
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import bulk_archives as archives


class PackageTests(unittest.TestCase):
    def test_verified_zip_replaces_loose_files_and_recovers_cleanup(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); part = root / 'part-00001'; part.mkdir()
            source = part / 'p001.json.gz'; data = gzip.compress(b'{"resourceType":"Patient"}')
            source.write_bytes(data)
            target = root / 'packages/test.zip'
            updates = []
            with patch.object(archives.shutil, 'rmtree', side_effect=OSError('cleanup interrupted')):
                with self.assertRaisesRegex(OSError, 'cleanup interrupted'):
                    archives.package(root, [part.name], target, lambda done, count: updates.append((done, count)))
            self.assertEqual([(1, 2), (2, 2)], updates)
            self.assertTrue(source.exists()); modified = target.stat().st_mtime_ns
            archives.package(root, [part.name], target, lambda *_: None)
            self.assertFalse(part.exists()); self.assertEqual(modified, target.stat().st_mtime_ns)
            with zipfile.ZipFile(target) as archive:
                self.assertEqual(data, archive.read('part-00001/p001.json.gz'))
                self.assertEqual(zipfile.ZIP_STORED, archive.getinfo('part-00001/p001.json.gz').compress_type)

    def test_verification_failure_keeps_originals(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); part = root / 'part-00001'; part.mkdir()
            (part / 'p001.json.gz').write_bytes(gzip.compress(b'{}'))
            target = root / 'packages/test.zip'
            with patch.object(archives, 'read_manifest', side_effect=ValueError('damaged')):
                with self.assertRaisesRegex(ValueError, 'damaged'):
                    archives.package(root, [part.name], target, lambda *_: None)
            self.assertEqual(gzip.compress(b'{}'), (part / 'p001.json.gz').read_bytes())
            self.assertFalse(target.exists())

    def test_changed_loose_file_is_not_deleted_on_retry(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); part = root / 'part-00001'; part.mkdir()
            source = part / 'p001.json.gz'; source.write_bytes(gzip.compress(b'{}'))
            target = root / 'test.zip'
            with patch.object(archives.shutil, 'rmtree', side_effect=OSError()):
                with self.assertRaises(OSError): archives.package(root, [part.name], target, lambda *_: None)
            source.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Loose file differs'):
                archives.package(root, [part.name], target, lambda *_: None)
            self.assertEqual(b'changed',source.read_bytes())
