import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from clinical_selections import validation_ops
from workbook_xml import read_sheets

class CustomSystemSelectionsTest(unittest.TestCase):
    def test_generated_system_dropdowns_allow_custom_input(self):
        sheets = read_sheets(Path(__file__).resolve().parents[2] / 'FHIR_Testdatengenerator_Vorlage.xlsx')
        operations = [op.split('\t') for op in validation_ops(sheets)]
        checked = 0
        for sheet, cells in sheets.items():
            for cell, value in cells.items():
                if cell.endswith('1') and cell[:-1].isalpha() and value.lower().endswith('codesystem'):
                    matches = [op for op in operations if op[0] == 'validation' and op[1] == sheet and op[2].startswith(cell[:-1]+'2:')]
                    self.assertEqual(1, len(matches), (sheet, value))
                    self.assertEqual('false', matches[0][4], (sheet, value))
                    checked += 1
        self.assertGreaterEqual(checked, 10)
