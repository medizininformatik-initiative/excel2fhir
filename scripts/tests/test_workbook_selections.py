"""Regression checks for the actual shipped input contract, not just list strings."""
import re
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from workbook_xml import read_sheets, NS
from workbook_absent import display, canonical

ROOT = Path(__file__).resolve().parents[2]

class WorkbookSelectionsTest(unittest.TestCase):
    def test_presentation_does_not_rewrite_narratives_or_boolean_results(self):
        self.assertEqual('Unbekannt', display('Medikation', 'Einzeldosis', '!dar:unknown', {}))
        self.assertEqual('!dar:unknown', display('Medikation', 'Dosierungstext', '!dar:unknown', {}))
        self.assertEqual('Unbekannt', canonical('Laborbefund', {'Werttyp': 'Text', 'Messwert': 'Unbekannt'})['Messwert'])
        self.assertEqual('!dar:unknown', canonical('Laborbefund', {'Werttyp': 'Fehlend', 'Messwert': 'Unbekannt'})['Messwert'])

    def test_shipped_dropdowns_target_only_input_columns_and_existing_values(self):
        for filename in ('FHIR_Testdatengenerator_Vorlage.xlsx', 'FHIR_Testdatengenerator_Interpolar_Demo.xlsx'):
            path = ROOT / filename
            cells = read_sheets(path)
            with ZipFile(path) as z:
                rels = {r.attrib['Id']: r.attrib['Target'] for r in ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
                for sheet in ET.fromstring(z.read('xl/workbook.xml')).findall('s:sheets/s:sheet', NS):
                    name = sheet.attrib['name']
                    if name in ('Codes', 'Konvertierungsoptionen'): continue
                    target = rels[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
                    target = target.lstrip('/') if target.startswith('/') else 'xl/' + target
                    headers = {k[:-1]: v for k,v in cells[name].items() if re.fullmatch('[A-Z]+1',k)}
                    for d in ET.fromstring(z.read(target)).findall('.//s:dataValidation', NS):
                        if d.get('type') != 'list': continue
                        formula = d.findtext('s:formula1',namespaces=NS)
                        for area in d.get('sqref').split():
                            col, row = re.match(r'([A-Z]+)(\d+)', area).groups()
                            self.assertGreater(int(row),1,(name,area))
                            self.assertNotEqual('Erklärung/Ausfüllhilfe',headers[col])
                            if name == 'Klinische Dokumentation':
                                self.assertNotEqual('Bezeichner',headers[col])
                            if headers[col] in ('Code','Zusatzcode','Einzeldosis','Wirkstoffcode','Wert','Messwert','Prozedurencode'):
                                self.assertNotIn(d.get('showErrorMessage'), ('1', 'true'), (name,col))
                        for col, first, endcol, last in re.findall(r'Codes!\$([A-Z]+)\$(\d+)(?::\$([A-Z]+)\$(\d+))?',formula):
                            self.assertTrue(all(cells['Codes'].get(col+str(n)) for n in range(int(first),int(last or first)+1)),formula)
                        if name == 'Medikation' and '$C2=' in formula:
                            self.assertIn('$BJ$30:$BJ$36',formula)
                            self.assertNotIn('$BH$',formula)
                            self.assertRegex(d.get('sqref'), r'^L2:L[0-9]+$')
                            self.assertGreaterEqual(int(d.get('sqref').split(':L')[1]), 1000)
            self.assertEqual('in-progress',cells['Codes']['BJ30'])
            self.assertEqual('Unbekannt',cells['Codes']['AA36'])
            self.assertNotIn('Positive Unendlichkeit',[cells['Codes'].get('AA'+str(i)) for i in range(30,42)])
