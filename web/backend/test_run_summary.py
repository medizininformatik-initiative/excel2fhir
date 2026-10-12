import json
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import run_summary


class RunSummaryTests(TestCase):
    def book(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        ns = run_summary.NS['s']
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('xl/workbook.xml', f'<workbook xmlns="{ns}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Person" r:id="p"/><sheet name="Fall" r:id="f"/></sheets></workbook>')
            archive.writestr('xl/_rels/workbook.xml.rels', '<Relationships><Relationship Id="p" Target="worksheets/p.xml"/><Relationship Id="f" Target="worksheets/f.xml"/></Relationships>')
            archive.writestr('xl/sharedStrings.xml', f'<sst xmlns="{ns}"><si><t>patient-1</t></si></sst>')
            archive.writestr('xl/worksheets/p.xml', f'<worksheet xmlns="{ns}"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Patient-ID</t></is></c></row><row r="2"><c r="A2" t="s"><v>0</v></c></row><row r="3"><c r="A3" t="s"><v>0</v></c></row><row r="4"><c r="A4"/></row></sheetData></worksheet>')
            archive.writestr('xl/worksheets/f.xml', f'<worksheet xmlns="{ns}"><sheetData><row r="1"/><row r="2"><c r="A2" t="s"><v>0</v></c><c r="B2"><v>1</v></c></row><row r="3"><c r="A3" t="s"><v>0</v></c><c r="B3"><v>2</v></c></row></sheetData></worksheet>')

    def test_counts_saved_patients_and_cases_without_duplicates_or_blank_rows(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            book = root / 'input.xlsx'
            self.book(book)
            original = book.read_bytes()
            self.assertEqual({'patients': 1, 'cases': 2}, run_summary.summary(root, {}, 'succeeded'))
            self.assertEqual({}, run_summary.summary(root, {}, 'running'))
            self.assertEqual(original, book.read_bytes())
            for name in ['one', 'two']:
                self.book(root / f'output/run-1/excel/{name}.xlsx')
            self.assertEqual({'patients': 1, 'cases': 2}, run_summary.summary(root, {'inputKind': 'synthea-generation'}, 'failed'))
            book.write_bytes(b'broken')
            self.assertEqual({}, run_summary.summary(root, {}, 'failed'))

    def test_native_synthea_uses_inspected_counts_and_unknown_counts_stay_unknown(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot = {'inputKind': 'synthea-generation', 'generation': {'outputMode': 'synthea'}}
            self.assertEqual({}, run_summary.summary(root, snapshot, 'succeeded'))
            (root / 'datasets.json').write_text(json.dumps({'datasets': [{'inspection': {'patients': 12, 'resourceCounts': {'Encounter': 34}}}]}))
            self.assertEqual({'patients': 12, 'cases': 34}, run_summary.summary(root, snapshot, 'succeeded'))
