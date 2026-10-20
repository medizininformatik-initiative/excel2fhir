import hashlib
import json
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workbook_records import record
from german_texts import GermanTexts, PATH, catalog, localize_rows


class GermanTextsTest(unittest.TestCase):
    def test_parameterized_observation_text_preserves_counts_and_thresholds(self):
        examples = [
            ('29554-3', 'CABG Grafts: 3', 'Koronarbypass: 3 Transplantate'),
            ('29554-3', 'CABG Grafts: 1', 'Koronarbypass: 1 Transplantat'),
            ('29554-3', 'CABG Grafts: 12', 'Koronarbypass: 12 Transplantate'),
            ('29554-3', 'CABG Arterial Conduits: 2', 'Koronarbypass: 2 arterielle Transplantate'),
            ('29554-3', 'CABG Arterial Conduits: 6', 'Koronarbypass: 6 arterielle Transplantate'),
            ('29554-3', 'CABG Arterial Conduits: 1', 'Koronarbypass: 1 arterielles Transplantat'),
            ('29554-3', 'CABG Arterial Conduits: 0', 'Koronarbypass: 0 arterielle Transplantate'),
            ('85318-4', 'less than 1.8', 'kleiner als 1.8'),
            ('85318-4', 'less than 0.050', 'kleiner als 0.050'),
            ('85318-4', 'less than 2', 'kleiner als 2'),
            ('X9999-1', 'emergent_salvage', 'Notfallmäßiger Rettungseingriff'),
        ]
        for code, source, expected in examples:
            for sheet in ['Laborbefund', 'Klinische Dokumentation']:
                with self.subTest(source=source, sheet=sheet):
                    value_field = 'Messwert' if sheet == 'Laborbefund' else 'Wert'
                    code_field = 'LOINC' if sheet == 'Laborbefund' else 'Untersuchungscode'
                    row = record(sheet, {value_field: source, code_field: code, 'Codesystem': 'LOINC',
                                         'Werttyp': 'Text', 'Untersuchung ID': 'value',
                                         'Komponente von': 'parent' if code == 'X9999-1' else ''})
                    original = dict(row)
                    report = localize_rows({sheet: [row]})
                    self.assertEqual(row[value_field], expected)
                    labels = {value_field, 'Parameter', 'Bezeichner'}
                    self.assertEqual({k:v for k,v in row.items() if k not in labels},
                                     {k:v for k,v in original.items() if k not in labels})
                    self.assertFalse(any(m['context'] == sheet + '.Wert' for m in report['missing']))
                    self.assertEqual(GermanTexts().observation_text(source, 'http://loinc.org', code), expected)

    def test_unknown_observation_text_remains_detectable_by_source_comparison(self):
        for source, system, code in [
            ('CABG Grafts: 3', 'SNOMED CT (Version nicht angegeben)', '29554-3'),
            ('CABG Grafts: 3', 'LOINC', 'other'),
            ('CABG Grafts: 3 extra', 'LOINC', '29554-3'),
            ('less than 1.8 extra', 'LOINC', '85318-4'),
            ('Novel source value', 'LOINC', '85318-4'),
        ]:
            with self.subTest(source=source, system=system, code=code):
                tr = GermanTexts()
                self.assertEqual(tr.observation_text(source, system, code, require_translation=True), '')
                self.assertEqual(tr.report()['missing'], [
                    {'context': 'Observation.Wert', 'original': source, 'occurrences': 1}])
                self.assertEqual(GermanTexts().observation_text(source, system, code), source)

    def test_full_registry_coverage_and_provenance(self):
        data = catalog()[0]
        source = PATH.with_name('synthea-source-code-registry.json')
        self.assertEqual(data['registrySha256'], hashlib.sha256(source.read_bytes()).hexdigest())
        registry = json.loads(source.read_text())
        expected = {(e['system'], e['code']):set(e['displays']) - {''} for e in registry['entries']}
        actual = {(e['system'], e['code']):{t['original'] for t in e['translations']} for e in data['entries']}
        self.assertEqual(expected, actual)
        self.assertEqual(len(actual), len(data['entries']))
        for e in data['entries']:
            for t in e['translations']:
                self.assertTrue(t['de'].strip())
                self.assertIn(t['source'], data['sources'])
                self.assertTrue(t['review'])
                self.assertNotIn('(qualifier value)', t['de'])

    def test_all_medication_strengths_counts_and_brand_names_survive(self):
        for e in catalog()[0]['entries']:
            if not e['system'].endswith('rxnorm'): continue
            for t in e['translations']:
                numbers = lambda s: re.findall(r'\d+(?:\.\d+)?', s)
                self.assertEqual(numbers(t['original']), numbers(t['de']), t['original'])
                self.assertEqual(re.findall(r'\[[^\]]+\]', t['original']),
                                 re.findall(r'\[[^\]]+\]', t['de']))
                self.assertNotRegex(t['de'].lower(), r'\b(?:oral tablet|injectable|extended release|inhalation solution)\b')

    def test_negation_laterality_and_dangerous_draft_errors(self):
        tr = GermanTexts()
        for code, source, german in [('LA33-6','Yes','Ja'), ('LA32-8','No','Nein'),
                                     ('79893-4','Left eye Intraocular pressure','Augeninnendruck links')]:
            self.assertEqual(tr.text(source, system='LOINC', code=code), german)
        self.assertEqual(tr.text('Cardiac Arrest', system='SNOMED CT (Version nicht angegeben)', code='410429000'), 'Herzstillstand')
        self.assertEqual(tr.text('Tubal pregnancy', system='SNOMED CT (Version nicht angegeben)', code='79586000'), 'Eileiterschwangerschaft')
        for t in catalog()[0]['literals']:
            if '\nDo not take milk' in t['original']:
                self.assertEqual(t['de'].count('\n'), 1)

    def test_official_loinc_names_keep_code_scoped_provenance(self):
        tr = GermanTexts()
        entries = {e['code']: e for e in tr.data['entries'] if e['system'] == 'http://loinc.org'}
        official = [e for e in entries.values() if e.get('loinc')]
        self.assertEqual(len(official), 173)
        for entry in official:
            self.assertEqual(entry['loinc']['version'], '2.83')
            self.assertTrue(all(t['source'] == 'loinc-de-DE' for t in entry['translations']))
        self.assertEqual(tr.text('Hemoglobin [Mass/volume] in Blood', system='LOINC', code='718-7'),
                         'Hämoglobin [Masse/Volumen] in Blut')
        self.assertEqual(tr.text('No', system='LOINC', code='LA32-8'), 'Nein')
        report = tr.report()
        self.assertEqual(report['loinc']['translatedCodes'], 173)
        self.assertEqual(len(report['loinc']['codesWithoutOfficialTranslation']), 273)
        self.assertEqual(report['loincNotice']['conditionsUrl'], 'https://loinc.org/license')

    def test_unknown_code_and_new_note_content_are_reported(self):
        tr = GermanTexts()
        self.assertEqual(tr.text('Yes', system='CVX', code='LA33-6'), 'Yes')
        self.assertEqual(tr.document('- Novel unusual source text\n', 'note'), '- Novel unusual source text\n')
        self.assertTrue(tr.report()['missing'])

    def test_note_parameters_and_clinical_lists(self):
        tr = GermanTexts()
        source = ('# History of Present Illness\n'
                  'Ida is a 42 year-old nonhispanic white female. Patient has a history of cardiac arrest (disorder).\n'
                  'Patient is single. Patient quit smoking at age 16.\n'
                  'Patient currently has Medicare.\n'
                  'Patient was given the following immunizations: ipv. \n')
        target = tr.document(source, 'note')
        self.assertIn('Ida: 42 Jahre alt, weiblich.', target)
        self.assertIn('Anamnestisch bekannt: Herzstillstand', target)
        self.assertIn('Rauchstopp im Alter von 16 Jahren.', target)
        self.assertIn('Krankenversicherung laut Quelle: Medicare.', target)
        self.assertIn('Inaktivierter Poliomyelitis-Impfstoff', target)
        self.assertEqual(tr.report()['missing'], [])

    def test_only_text_columns_change_and_address_uses_same_identity(self):
        row = record('Klinische Dokumentation', {
            'Patient-ID': 'p',
            'Fall-Nr': 'f',
            'Bezeichner': 'Glucose [Mass/volume] in Blood',
            'Untersuchungscode': '2339-0',
            'Codesystem': 'LOINC',
            'Wert': '83.7',
            'Einheit': 'mg/dL',
            'Zeitstempel': '2026-01-01',
            'Werttyp': 'Zahl',
            'Kategorie': 'laboratory',
            'Status': 'final',
            'Untersuchung ID': 'o',
            'Einheitencode': 'mg/dL',
        })
        original = dict(row)
        report = localize_rows({'Klinische Dokumentation':[row]})
        self.assertNotEqual(row['Bezeichner'], original['Bezeichner'])
        self.assertEqual({k: v for k, v in row.items() if k != 'Bezeichner'}, {k: v for k, v in original.items() if k != 'Bezeichner'})
        tr = GermanTexts({'line':['Teststraße 12']})
        self.assertEqual(tr.observation_text('American street', 'LOINC','56799-0'), 'Teststraße 12')
        self.assertEqual(report['missing'], [])


if __name__ == '__main__': unittest.main()
