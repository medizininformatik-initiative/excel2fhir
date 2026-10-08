import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_medication_transformations import quantity, dose_text


class MedicationExpectationsTest(unittest.TestCase):
    def test_unknown_display_keeps_supplied_unit(self):
        self.assertEqual({'system': 'http://unitsofmeasure.org', 'value': 1.0,
                          'code': '{caps}', 'unit': '{caps}'}, quantity('1', '{caps}'))
        self.assertIn('_unit', quantity('1', ''))

    def test_dar_dose_text_uses_workbook_display(self):
        row = [''] * 20
        row[16] = '!dar:unknown'
        self.assertEqual('Einzeldosis: Unbekannt (Data Absent Reason) (Einheit unbekannt)', dose_text(row))
