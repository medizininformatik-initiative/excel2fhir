from decimal import InvalidOperation
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_synthea_projection import text_dose


class ProjectionDoseTest(unittest.TestCase):
    def test_numeric_and_explicit_absent_doses_remain_distinguishable(self):
        self.assertEqual(text_dose('Schema; Einzeldosis: Unbekannt (Data Absent Reason) (Einheit unbekannt)'),
                         ('!dar:unknown', ''))
        self.assertEqual(text_dose('Einzeldosis: 0.0 mg; Dosen pro Tag: 2'), ('0', 'mg'))
        self.assertEqual(text_dose('Einzeldosis: 2.0 (Einheit unbekannt)'), ('2', ''))
        self.assertIsNone(text_dose('Bei Bedarf'))
        with self.assertRaises(InvalidOperation):
            text_dose('Einzeldosis: kaputter Wert')


if __name__ == '__main__':
    unittest.main()
