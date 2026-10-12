"""Exercise real UNO row writes across batching and operation boundaries."""
import base64
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from synthea_to_excel import ROOT, apply_workbook_edits
from workbook_xml import NS, read_sheets


@unittest.skipUnless(shutil.which('java') and (
    Path('/Applications/LibreOffice.app/Contents/MacOS/soffice').exists()
    or shutil.which('libreoffice') or shutil.which('soffice')), 'Requires Java and LibreOffice')
class WorkbookUnoTest(unittest.TestCase):
    def test_rows_preserve_text_styles_and_interleaved_operations(self):
        def encoded(value):
            return base64.b64encode(value.encode()).decode()

        def row(sheet, number, *values):
            return '\t'.join(['row', sheet, 'A' + str(number), *map(encoded, values)])

        ops = ['clear\tDiagnose\tA2:D1052']
        ops += [f'copy\tDiagnose\tA2:D2\tA{i}' for i in range(1032, 1053)]
        expected = {}
        for number in range(2, 1052):
            values = (f'{number:06}', 'Größe\tÄÖÜ\nß', '=1+1' if number % 2 else '')
            ops.append(row('Diagnose', number, *values))
            expected.update({f'{column}{number}': value for column, value in zip('ABC', values) if value})
        # Width change, overwrite, gap, reverse order, and a sheet change must stop a block.
        ops += [row('Diagnose', 1052, 'last', 'short'),
                row('Diagnose', 1052, 'overwritten'),
                row('Diagnose', 1054, 'gap'), row('Diagnose', 1053, 'reverse'),
                row('Person', 2, 'other sheet'), row('Diagnose', 1054, 'back'),
                'set\tDiagnose\tA512\t' + encoded('interleaved'),
                row('Diagnose', 512, 'after set'),
                'clear\tDiagnose\tA513:A513', row('Diagnose', 513, 'after clear'),
                'formula\tDiagnose\tD2\t' + encoded('=1+1'),
                'text\tDiagnose\tA2:C1054']
        expected.update(A1052='overwritten', B1052='short', A1053='reverse', A1054='back',
                        A512='after set', A513='after clear', D2='2')
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'test.xlsx'
            apply_workbook_edits(ROOT / 'FHIR_Testdatengenerator_Vorlage.xlsx', ops, output)
            sheets = read_sheets(output)
            for cell, value in expected.items():
                self.assertEqual(value, sheets['Diagnose'][cell], cell)
            self.assertEqual('other sheet', sheets['Person']['A2'])
            self.assertNotIn('C2', sheets['Diagnose'])
            with ZipFile(output) as archive:
                cells = {}
                for name in archive.namelist():
                    if name.startswith('xl/worksheets/sheet') and name.endswith('.xml'):
                        sheet = ET.fromstring(archive.read(name))
                        candidate = {c.attrib['r']: c for c in sheet.findall('.//s:sheetData/s:row/s:c', NS)}
                        if 'A1054' in candidate:
                            cells = candidate
                            break
                self.assertEqual(cells['A2'].attrib['s'], cells['A1051'].attrib['s'])
                self.assertEqual(cells['B2'].attrib['s'], cells['B1051'].attrib['s'])
                self.assertIsNone(cells['C3'].find('s:f', NS), 'Formula-like row values stay text')
                self.assertIsNotNone(cells['D2'].find('s:f', NS))


if __name__ == '__main__':
    unittest.main()
