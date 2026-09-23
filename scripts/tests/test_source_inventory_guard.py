import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_synthea_code_registry import build


class SourceInventoryGuardTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.archive = self.root / 'archive'
        self.checkout = self.root / 'synthea'
        self.archive.mkdir()
        self.source = self.checkout / 'src/main/resources/modules/example.json'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('{}')
        (self.archive / 'source-manifest.json').write_text(json.dumps([
            {'file': 'src/main/resources/modules/example.json',
             'sha256': hashlib.sha256(self.source.read_bytes()).hexdigest()}]))
        (self.archive / 'inventory-combined.json').write_text('[]')

    def test_new_module_or_java_generator_requires_new_inventory(self):
        for relative in ['resources/modules/new.json', 'java/NewGenerator.java']:
            added = self.checkout / 'src/main' / relative
            added.parent.mkdir(parents=True, exist_ok=True)
            added.write_text('new clinical content')
            with self.assertRaisesRegex(ValueError, 'file set changed; added:'):
                build(self.archive, self.checkout)
            added.unlink()

    def test_modified_and_removed_source_are_rejected(self):
        self.source.write_text('{"value_set": "https://example.test/new"}')
        with self.assertRaisesRegex(ValueError, 'source changed'):
            build(self.archive, self.checkout)
        self.source.unlink()
        with self.assertRaisesRegex(ValueError, 'missing:'):
            build(self.archive, self.checkout)

    def test_only_known_generated_version_label_is_ignored(self):
        (self.checkout / 'src/main/resources/version.txt').write_text('generated version')
        self.assertEqual(build(self.archive, self.checkout)['verifiedSourceFiles'], 1)


if __name__ == '__main__':
    unittest.main()
