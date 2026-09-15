import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).resolve().parents[2] / 'examples/synthea-hospital/select_cases.py'
spec = importlib.util.spec_from_file_location('hospital_example', path)
example = importlib.util.module_from_spec(spec)
spec.loader.exec_module(example)


class HospitalExampleTest(unittest.TestCase):
    def test_selection_preserves_sources_and_does_not_count_old_admissions(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / 'source'
            source.mkdir()
            for i, admissions in enumerate([2] * 6 + [1] * 2 + [0] * 2):
                resources = [{'resourceType': 'Patient', 'id': str(i)},
                             {'resourceType': 'Encounter', 'class': {'code': 'IMP'},
                              'period': {'start': '2010-01-01'}},
                             {'resourceType': 'Encounter', 'class': {'code': 'AMB'},
                              'period': {'start': '2026-01-01'}}]
                resources += [{'resourceType': 'Encounter', 'class': {'code': 'IMP'},
                               'period': {'start': '2020-01-01'}} for _ in range(admissions)]
                (source / f'{i}.json').write_text(json.dumps({'entry': [{'resource': r} for r in resources]}))
            report = example.select(source, root / 'selected')
            self.assertEqual(len(report['selected']), 10)
            for item in report['selected']:
                before = (source / item['file']).read_bytes()
                self.assertEqual((root / 'selected/fhir' / item['file']).read_bytes(), before)
                self.assertEqual(item['sha256'], hashlib.sha256(before).hexdigest())
                self.assertEqual(item['contextEncountersOutsidePeriod'], 1)
                self.assertEqual(item['encounters2020to2026']['AMB'], 1)
            with self.assertRaises(FileExistsError):
                example.select(source, root / 'selected')
            (source / '0.json').unlink()
            with self.assertRaisesRegex(ValueError, 'Insufficient'):
                example.select(source, root / 'incomplete')
            self.assertFalse((root / 'incomplete').exists())


if __name__ == '__main__':
    unittest.main()
