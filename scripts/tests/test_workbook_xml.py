"""Preserve raw XLSX cell values while reading rows incrementally."""
from pathlib import Path
import sys
import tempfile
import unittest
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workbook_xml import read_sheets


class WorkbookXmlTest(unittest.TestCase):
    def workbook(self, directory, *, shared_strings=True):
        path = Path(directory) / 'cells.xlsx'
        namespace = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
        with ZipFile(path, 'w') as archive:
            archive.writestr('xl/workbook.xml', f'''<workbook xmlns="{namespace}"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets><sheet name="Values" sheetId="1" r:id="r1"/>
                <sheet name="Hidden" sheetId="2" state="hidden" r:id="r2"/></sheets></workbook>''')
            archive.writestr('xl/_rels/workbook.xml.rels', '''<Relationships>
                <Relationship Id="r1" Target="worksheets/values.xml"/>
                <Relationship Id="r2" Target="/xl/worksheets/hidden.xml"/></Relationships>''')
            if shared_strings:
                archive.writestr('xl/sharedStrings.xml', f'''<sst xmlns="{namespace}">
                    <si><t></t></si><si><r><t>Größe</t></r><r><t> ß</t></r></si></sst>''')
            shared = '<c r="A1" t="s"><v>1</v></c><c r="B1" t="s"><v>0</v></c>' if shared_strings else ''
            archive.writestr('xl/worksheets/values.xml', f'''<worksheet xmlns="{namespace}"><sheetData>
                <row r="1">{shared}<c r="C1" t="inlineStr"><is><r><t>001</t></r><r><t>\nÄ</t></r></is></c>
                <c r="D1"><f>1+1</f><v>2</v></c><c r="E1" t="b"><v>0</v></c>
                <c r="F1"><v>46000.50</v></c><c r="G1" t="e"><v>#N/A</v></c>
                <c r="H1"><f>1+1</f></c><c r="I1"><v/></c></row>
                <row r="3"/><row r="8"><c r="A8" t="inlineStr"><is><t xml:space="preserve"> </t></is></c></row>
                </sheetData><extLst><ext><c r="Z9"><v>not a worksheet cell</v></c></ext></extLst></worksheet>''')
            archive.writestr('xl/worksheets/hidden.xml', f'''<worksheet xmlns="{namespace}">
                <sheetData><row r="100"><c r="Z100"><v>-3.2</v></c></row></sheetData></worksheet>''')
        return path

    def test_shared_inline_formula_and_sparse_cells_keep_raw_values(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual({'Values': {'A1': 'Größe ß', 'C1': '001\nÄ', 'D1': '2', 'E1': '0',
                                        'F1': '46000.50', 'G1': '#N/A', 'A8': ' '},
                              'Hidden': {'Z100': '-3.2'}}, read_sheets(self.workbook(directory)))

    def test_inline_workbook_needs_no_shared_string_part(self):
        with tempfile.TemporaryDirectory() as directory:
            sheets = read_sheets(self.workbook(directory, shared_strings=False))
            self.assertEqual('001\nÄ', sheets['Values']['C1'])
            self.assertNotIn('A1', sheets['Values'])
            self.assertEqual({'Z100': '-3.2'}, sheets['Hidden'])


if __name__ == '__main__':
    unittest.main()
