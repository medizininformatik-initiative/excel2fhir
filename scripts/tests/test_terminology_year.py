import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from terminology_year import DATA, settings, target, SYSTEMS
from synthea_to_excel import prepare
from diagnosis_mapping import map_diagnosis, SNOMED
from test_synthea_import import bundle


class TerminologyYearTest(unittest.TestCase):
    def test_reviewed_2025_categories_replace_four_2026_subdivisions(self):
        system = 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
        for newer, older in {'R53.9': 'R53', 'R73.08': 'R73.0', 'R76.88': 'R76.8', 'Z98.88': 'Z98.8'}.items():
            result = target({'system': system, 'code': newer, 'display': 'newer', 'version': '2026'}, 2025)
            self.assertEqual(older, result['code'])
            self.assertEqual('2025', result['version'])
            self.assertNotEqual('newer', result['display'])
        with self.assertRaisesRegex(ValueError, 'not been reviewed'):
            target({'system': system, 'code': 'UNREVIEWED'}, 2025)

    def test_catalogue_subset_matches_current_mapping_sources(self):
        folder = Path(__file__).resolve().parents[1] / 'mappings'
        for name, expected in DATA['sourceMappings'].items():
            self.assertEqual(expected, hashlib.sha256((folder / name).read_bytes()).hexdigest(), name)
        self.assertEqual(6, len(DATA['catalogues']))
        for catalogue in DATA['catalogues'].values():
            self.assertTrue(catalogue['targets'])
            self.assertEqual(64, len(catalogue['sha256']))
            self.assertTrue(catalogue['url'].startswith('https://terminologien.bfarm.de/'))

    def test_year_and_output_policy_are_independent_and_leave_source_unchanged(self):
        source = bundle()
        source['entry'] = source['entry'][:3]
        table = json.loads((Path(__file__).resolve().parents[1] / 'mappings/synthea-diagnoses-icd10gm-2026.json').read_text())
        entry = next(e for e in table['entries'] if (e.get('target') or {}).get('code') == 'R53.9')
        source['entry'][2]['resource']['code']['coding'] = [{'system': SNOMED, 'code': entry['sourceCode'], 'display': entry['sourceDisplay']}]
        before = copy.deepcopy(source)
        for year, code in [('2025', 'R53'), ('2026', 'R53.9')]:
            for mode, expected in [('Jahr', year), ('', ''), ('Unbekannt (Data Absent Reason)', '!dar:unknown')]:
                rows, report = prepare(source, {'SYNTHEA_MAPPING_YEAR': year, 'SYNTHEA_VERSION_OUTPUT': mode})
                row = rows['Diagnose'][0]
                self.assertEqual([code, 'ICD-10-GM'], row[3:5])
                self.assertEqual([expected, ''], row[13:])
                self.assertEqual(year, report['diagnosisMappings'][0]['target']['version'])
                self.assertEqual(year, report['terminology']['mappingYear'])
        self.assertEqual(before, source)

    def test_mapping_year_is_restricted_but_all_dars_are_available(self):
        from workbook_absent import LABELS
        for value in LABELS.values(): self.assertEqual('2025', settings({'SYNTHEA_MAPPING_YEAR': '2025', 'SYNTHEA_VERSION_OUTPUT': value})[0])
        for year in ['2024', '2027', '2025,2026', '']:
            with self.assertRaises(ValueError): settings({'SYNTHEA_MAPPING_YEAR': year})
