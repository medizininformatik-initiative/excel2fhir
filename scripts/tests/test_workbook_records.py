"""Field identity survives schema additions, column moves and dictionary order."""
import base64
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workbook_records import SCHEMAS, record, ordered_values
from workbook_xml import read_sheets
from synthea_to_excel import ROOT, write_workbook
from clinical_selections import validation_ops
from german_texts import localize_rows
from check_medication_transformations import dose_text, baseline_medications


class WorkbookRecordsTest(unittest.TestCase):
    def test_schemas_match_all_shipped_workbooks(self):
        for path in ('FHIR_Testdatengenerator_Vorlage.xlsx', 'input/FHIR_Testdatengenerator_Vorlage.xlsx',
                     'FHIR_Testdatengenerator_Interpolar_Demo.xlsx'):
            sheets = read_sheets(ROOT / path)
            for name, fields in SCHEMAS.items():
                headers = [value for cell, value in sheets[name].items()
                           if cell.endswith('1') and cell[:-1].isalpha() and value != 'Erklärung/Ausfüllhilfe']
                self.assertEqual(set(fields), set(headers), (path, name))
                self.assertEqual(len(fields), len(headers), (path, name))

    def test_unknown_fields_and_positional_records_fail_explicitly(self):
        with self.assertRaises(TypeError): record('Medikation', ['p', 'f'])
        with self.assertRaises(ValueError): record('Medikation', {'DosierungsText': 'x'})
        row = record('Medikation')
        with self.assertRaises(ValueError): ordered_values('Medikation', row, ['Dosierungstext'] * len(row))

    def test_writer_follows_reordered_headers_for_every_sheet(self):
        sheets = read_sheets(ROOT / 'FHIR_Testdatengenerator_Vorlage.xlsx')
        rows, columns = {}, {}
        for name, fields in SCHEMAS.items():
            header_cells = [cell for cell, value in sheets[name].items()
                            if cell.endswith('1') and cell[:-1].isalpha() and value != 'Erklärung/Ausfüllhilfe']
            for cell, field in zip(header_cells, reversed(fields)):
                sheets[name][cell] = field
            columns[name] = sorted(header_cells, key=lambda cell: (len(cell), cell))
            rows[name] = [dict(reversed(list(record(name, {field: 'value:' + field for field in fields}).items())))]
        with patch('synthea_to_excel.read_sheets', return_value=sheets), patch('synthea_to_excel.apply_workbook_edits') as apply:
            write_workbook(rows, Path('/tmp/named-fields.xlsx'))
        written = set()
        for op in apply.call_args.args[1]:
            kind, name, address, *values = op.split('\t')
            if kind != 'row': continue
            written.add(name)
            for cell, encoded in zip(columns[name], values):
                self.assertEqual('value:' + sheets[name][cell], base64.b64decode(encoded).decode(), (name, cell))
            self.assertEqual(len(columns[name]), len(values))
        self.assertEqual(set(SCHEMAS), written)

    def test_medication_status_validation_follows_named_columns(self):
        sheets = read_sheets(ROOT / 'FHIR_Testdatengenerator_Vorlage.xlsx')
        cells = sheets['Medikation']
        headers = {value: cell for cell, value in cells.items() if cell.endswith('1') and cell[:-1].isalpha()}
        a, b = headers['Status'], headers['Medikationstyp']
        cells[a], cells[b] = cells[b], cells[a]
        ops = validation_ops(sheets)
        status = next(op for op in ops if op.startswith(f'validation\tMedikation\t{b[:-1]}2:'))
        self.assertIn(f'IF(${a[:-1]}2="Verordnung";', status)

    def test_dosage_text_is_translated_without_touching_frequency_or_system(self):
        row = record('Medikation', {'Medikationstyp': 'Verordnung', 'Einzeldosis': '5',
                     'Dosiereinheit': 'mg', 'Dosiereinheitcodesystem': 'urn:custom-units',
                     'Dosen pro Tag': '2', 'Dosierungstext': 'Take with water'})
        row = dict(reversed(list(row.items())))
        before = copy.deepcopy(row)
        with patch('german_texts.GermanTexts.text', return_value='Mit Wasser einnehmen') as translate:
            localize_rows({'Medikation': [row]})
        translate.assert_called_once_with('Take with water', 'Medikation.Freitext')
        before['Dosierungstext'] = 'Mit Wasser einnehmen'
        self.assertEqual(before, row)
        self.assertEqual('Mit Wasser einnehmen; Einzeldosis: 5 mg; Dosen pro Tag: 2', dose_text(row))
        medication, = baseline_medications([row], {}, 'p')
        dose, = medication['dosageInstruction']
        self.assertEqual(2, dose['timing']['repeat']['frequency'])
        self.assertEqual('urn:custom-units', dose['doseAndRate'][0]['doseQuantity']['system'])
        self.assertEqual(dose_text(row), dose['text'])
