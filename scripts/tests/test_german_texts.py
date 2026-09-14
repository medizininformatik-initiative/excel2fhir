import hashlib
import json
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from german_texts import GermanTexts, PATH, catalog, localize_rows


class GermanTextsTest(unittest.TestCase):
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
        row = ['p','f','Glucose [Mass/volume] in Blood','2339-0','LOINC','','','83.7','mg/dL',
               '2026-01-01','Zahl','','','laboratory','final','o','','','mg/dL']
        original = list(row)
        report = localize_rows({'Klinische Dokumentation':[row]})
        self.assertNotEqual(row[2], original[2])
        self.assertEqual(row[:2]+row[3:], original[:2]+original[3:])
        tr = GermanTexts({'line':['Teststraße 12']})
        self.assertEqual(tr.observation_text('American street', 'LOINC','56799-0'), 'Teststraße 12')
        self.assertEqual(report['missing'], [])


if __name__ == '__main__': unittest.main()
