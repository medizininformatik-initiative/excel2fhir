import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workbook_records import record
from check_medication_transformations import quantity, dose_text, baseline_medications


class MedicationExpectationsTest(unittest.TestCase):
    def test_unknown_display_keeps_supplied_unit(self):
        self.assertEqual({'value': 1.0,
                          'code': '{caps}', 'unit': '{caps}'}, quantity('1', '{caps}'))
        self.assertEqual({'value': 1.0}, quantity('1', ''))
        self.assertEqual({'value': 1.0, 'code': 'µg', 'unit': 'µg', 'system': 'http://unitsofmeasure.org'},
                         quantity('1', 'µg', 'http://unitsofmeasure.org'))

    def test_dar_dose_text_uses_workbook_display(self):
        row = record('Medikation')
        row['Einzeldosis'] = '!dar:unknown'
        self.assertEqual('Einzeldosis: Unbekannt (Data Absent Reason) (Einheit unbekannt)', dose_text(row))

    def test_empty_status_and_intent_are_not_repaired(self):
        for kind in ('Verordnung', 'Verabreichung', 'Medikationsaussage'):
            row = record('Medikation', {'Medikationstyp': kind})
            resource = baseline_medications([row], {}, 'patient')[0]
            self.assertNotIn('status', resource)
            self.assertNotIn('intent', resource)
            row['Status'] = '!dar:custom-reason'
            resource = baseline_medications([row], {}, 'patient')[0]
            self.assertNotIn('status', resource)
            self.assertEqual('custom-reason', resource['_status']['extension'][0]['valueCode'])
