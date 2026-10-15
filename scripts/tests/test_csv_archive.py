import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_synthea_cases import archive_csv

class ArchiveTest(unittest.TestCase):
    def test_flat_streamed_archive_replaces_working_tables(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            table = root / 'details/cases/person/csv-input/Fall-person_Person.csv'
            table.parent.mkdir(parents=True)
            (root / 'details/cases/person/options/Fall-person.csv').mkdir(parents=True)
            table.write_text('ID,Name\n001,Änne\n')
            original = table.read_bytes()
            archive_csv(root)
            self.assertFalse(table.exists())
            with zipfile.ZipFile(root / 'csv.zip') as archive:
                self.assertEqual([table.name], archive.namelist())
                self.assertEqual(original, archive.read(table.name))

    def test_failed_archive_preserves_working_tables(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            table = root / 'details/cases/a/input.csv'
            table.parent.mkdir(parents=True)
            table.write_text('data')
            with patch.object(zipfile.ZipFile, 'write', side_effect=OSError('disk full')):
                with self.assertRaises(OSError):
                    archive_csv(root)
            self.assertEqual('data', table.read_text())
            self.assertFalse((root / 'csv.zip').exists())
