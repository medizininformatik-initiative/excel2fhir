"""Source retention at clinical import boundaries, including German text fallback."""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from synthea_to_excel import prepare
from test_synthea_import import bundle
from test_contextual_procedures import source as procedure_source


class ImportRetentionTest(unittest.TestCase):
    def prepare_event(self, event):
        source = bundle()
        source['entry'] = source['entry'][:2]
        event = copy.deepcopy(event)
        event.setdefault('subject', {'reference': 'urn:uuid:p'})
        source['entry'].append({'resource': event})
        before = copy.deepcopy(source)
        rows, report = prepare(source)
        self.assertEqual(source, before)
        self.assertEqual(rows['Diagnose'], [])
        return rows, report

    def test_categoryless_versioned_observation_and_components_survive(self):
        rows, report = self.prepare_event({
            'resourceType': 'Observation', 'id': 'obs', 'status': 'final',
            'code': {'coding': [{'system': 'http://loinc.org', 'code': '85354-9', 'version': '2.83'}]},
            'component': [
                {'code': {'coding': [{'system': 'urn:local', 'code': 'count'}]}, 'valueInteger': 0},
                {'code': {'coding': [{'system': 'urn:local', 'code': 'range'}]},
                 'valueRange': {'low': {'value': 1}, 'high': {'value': 3}}},
                {'code': {'coding': [{'system': 'http://loinc.org', 'code': '8480-6'}]},
                 'valueQuantity': {'value': 120, 'code': 'mm[Hg]', 'system': 'http://unitsofmeasure.org'}}]})
        observations = rows['Klinische Dokumentation']
        self.assertEqual(len(observations), 4)
        self.assertTrue(all(r['Kategorie'] == '' for r in observations))
        self.assertEqual(observations[1]['Wert'], '0')
        self.assertEqual(observations[1]['Codesystem'], 'urn:local')
        self.assertEqual(observations[2]['Untersuchungscode'], 'range')
        self.assertEqual(observations[3]['Wert'], '120')
        losses = {l['path'] for l in report['losses'] if l['resourceType'] == 'Observation'}
        self.assertIn('component[1].value[x]', losses)
        self.assertIn('.code.coding[0].version', losses)
        self.assertNotIn('$', losses)

    def test_unknown_text_uses_german_code_label_and_reports_original(self):
        rows, report = self.prepare_event({
            'resourceType': 'Observation', 'id': 'text',
            'code': {'text': 'A novel English label', 'coding': [
                {'system': 'http://loinc.org', 'code': '718-7'}]},
            'valueString': 'A novel English result'})
        row = rows['Klinische Dokumentation'][0]
        self.assertEqual(row['Bezeichner'], 'Hämoglobin [Masse/Volumen] in Blut')
        self.assertEqual(row['Wert'], '')
        missing = {m['original'] for m in report['germanTexts']['missing']}
        self.assertTrue({'A novel English label', 'A novel English result'} <= missing)

    def test_germ_results_use_existing_dictionary_style(self):
        for organism in ('Klebsiella pneumoniae', 'Proteus mirabilis'):
            rows, report = self.prepare_event({
                'resourceType': 'Observation', 'id': 'culture',
                'code': {'coding': [{'system': 'http://loinc.org', 'code': '630-4'}]},
                'valueString': 'Greater than 100,000 colony forming units per mL ' + organism})
            self.assertEqual(rows['Klinische Dokumentation'][0]['Wert'],
                             'Mehr als 100.000 koloniebildende Einheiten pro ml ' + organism)
            self.assertFalse(any(m['context'].endswith('.Wert') for m in report['germanTexts']['missing']))

    def test_statement_keeps_dose_and_context_without_diagnoses(self):
        dose = {'text': 'Bei Bedarf', 'timing': {'repeat': {'frequency': 2, 'period': 1, 'periodUnit': 'd'}},
                'doseAndRate': [{'doseQuantity': {'value': 0.6, 'code': 'mg', 'system': 'http://unitsofmeasure.org'}}]}
        rows, report = self.prepare_event({
            'resourceType': 'MedicationStatement', 'id': 'statement', 'status': 'active',
            'context': {'reference': 'urn:uuid:e'}, 'dateAsserted': '2026-09-02',
            'effectivePeriod': {'start': '2026-09-01', 'end': '2026-09-03'},
            'medicationCodeableConcept': {'coding': [{'system': 'http://www.nlm.nih.gov/research/umls/rxnorm', 'code': '197541'}]},
            'dosage': [dose]})
        row = rows['Medikation'][0]
        self.assertEqual(row['Medikationstyp'], 'Medikationsaussage')
        self.assertEqual(row['Fall-Nr'], '1')
        self.assertEqual(row['Einzeldosis'], '0.6')
        self.assertEqual(row['Dosen pro Tag'], '2')
        self.assertEqual(row['Dosierungstext'], 'Bei Bedarf')
        self.assertEqual(row['Dokumentationszeitpunkt'], '2026-09-02')
        self.assertEqual(row['Beginn'], '2026-09-01')
        self.assertEqual(row['Ende'], '2026-09-03')
        self.assertIn({'sourceId': 'statement', 'resourceType': 'MedicationStatement'}, report['clinicalImports'])

    def test_suspicious_procedure_is_preserved_with_warning(self):
        source = {'resourceType': 'Bundle', 'entry': procedure_source('65575008')}
        rows, report = prepare(source)
        row = rows['Prozedur'][0]
        self.assertEqual(row['Prozedurencode'], '65575008')
        self.assertEqual(row['Codesystem'], 'SNOMED CT (Version nicht angegeben)')
        self.assertTrue(report['clinicalMappings'][0]['contextWarning'])
        self.assertFalse(any(l['resourceType'] == 'Procedure' and l['path'] == '$' for l in report['losses']))

    def test_careplan_description_uses_existing_dictionary(self):
        rows, report = self.prepare_event({
            'resourceType': 'CarePlan', 'id': 'plan', 'status': 'active', 'intent': 'plan',
            'activity': [{'detail': {'description': 'Cardiac Arrest'}},
                         {'detail': {'code': {'coding': [{'system': 'http://snomed.info/sct', 'code': '183051005'}]},
                                     'description': 'A novel activity description'}}]})
        self.assertEqual(rows['Behandlungsplan'][0]['Aktivitätstext'], 'Herzstillstand')
        self.assertTrue(rows['Behandlungsplan'][1]['Aktivitätstext'])
        self.assertNotIn('novel', rows['Behandlungsplan'][1]['Aktivitätstext'])
        self.assertTrue(any('A novel activity description' in l['reason'] for l in report['losses']))


if __name__ == '__main__':
    unittest.main()
