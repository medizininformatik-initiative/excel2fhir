"""Remove technical contact columns from an existing workbook using UNO.
Usage: simplify_contact_template.py INPUT.xlsx OUTPUT.xlsx
Existing explicit patient data is rejected; regenerate Synthea cases instead.
"""
import base64
from pathlib import Path
import sys
from zipfile import ZipFile
import xml.etree.ElementTree as ET
from workbook_xml import NS
from workbook_xml import read_sheets
from synthea_to_excel import apply_workbook_edits


def simplify(source, target):
    sheets=read_sheets(source);cells=sheets['Fall']
    assert [cells.get(c+'1') for c in 'KLMN']==['Kontakt-ID','Kontaktebene','Kontaktart','Übergeordneter Kontakt']
    assert not any(v for k,v in cells.items() if k[0] in 'KLN' and k[1:].isdigit() and k[1:]!='1'), 'Explicit contacts must be regenerated'
    ops=['removeColumns\tFall\t13\t1','removeColumns\tFall\t10\t2',
         'clear\tCodes\tBE29:BE60','noValidation\tFall\tA1:L1048576']
    def put(sheet,cell,text):ops.append('\t'.join(['set',sheet,cell,base64.b64encode(text.encode()).decode()]))
    for i in range(2, 26):put('Fall','L'+str(i),'')
    notes=[
        'Erste Zeile eines Falls: Fall-Nr, Beginn, optional Ende und Einrichtungskontaktklasse.',
        'Weitere Aufenthalte folgen in zeitlicher Reihenfolge; dieselbe Fall-Nr darf wiederholt werden.',
        'Eine Fachabteilung erzeugt einen Abteilungskontakt. Ohne Fachabteilung entsteht kein neuer Abteilungskontakt.',
        'Station, Zimmer oder Bett erzeugen einen Versorgungsstellenkontakt; leere Ortsangaben bleiben leer.',
        'Bei mehreren Aufenthalten derselben Abteilung bleibt der Abteilungskontakt bestehen.',
        'Kontaktart leer, Normalstationär oder Intensivstationär: primärer Aufenthalt.',
        'Operation, Untersuchung und Behandlung oder Konsil: zusätzlicher sekundärer Kontakt.',
        'Sekundärkontakte direkt nach ihrem primären Aufenthalt eintragen; dessen Stationsbett bleibt erhalten.',
        'Ohne sekundäres Ende gilt das Ende des primären Aufenthalts; ohne bekanntes Ende bleibt der Kontakt offen.',
        'Ein neuer primärer Aufenthalt schließt den vorherigen offenen Aufenthalt und dessen offene Sekundärkontakte.',
        'Ein vorhandenes sekundäres Kontaktende bleibt erhalten. Kontaktzeiten sind keine Prozedurzeiten.',
        'Sekundärkontakte benötigen einen primären Aufenthalt; unpassende oder mehrdeutige Zeiträume werden gemeldet.',
        'Eine OP allein erzeugt keine Rückverlegung. Echte Verlegungen als neue primäre Aufenthalte eintragen.',
        'Aufnahmegrund nur in der ersten Fallzeile; Fehlgründe sind mit (Data Absent Reason) gekennzeichnet.',
        'Ohne Abteilung wird ein Ortskontakt direkt dem Einrichtungskontakt zugeordnet.',
        'Einrichtungskontakt ohne Abteilung/Ort: sein Zeitraum begrenzt die folgenden Aufenthalte.',
        'Enthält die erste Fallzeile selbst einen Aufenthalt, erweitern Folgeaufenthalte den Einrichtungszeitraum.',
    ]
    for i,note in enumerate(notes,2):put('Fall','L'+str(i),note)
    from clinical_selections import validation_ops
    new_sheets=dict(sheets)
    new_sheets['Fall']={k:v for k,v in cells.items() if k[0] not in 'KLMNO'}
    new_sheets['Fall'].update({'K'+k[1:]:v for k,v in cells.items() if k.startswith('M')})
    new_sheets['Fall'].update({'L'+k[1:]:v for k,v in cells.items() if k.startswith('O')})
    ops.extend(validation_ops(new_sheets))
    # UNO can otherwise recalculate automatic heights on unrelated sheets.
    with ZipFile(source) as archive:
        rels={r.attrib['Id']:r.attrib['Target'] for r in ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))}
        for sheet in ET.fromstring(archive.read('xl/workbook.xml')).findall('s:sheets/s:sheet',NS):
            path=rels[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
            path=path.lstrip('/') if path.startswith('/') else 'xl/'+path
            for row in ET.fromstring(archive.read(path)).findall('s:sheetData/s:row',NS):
                if 'ht' in row.attrib:
                    ops.append(f'rowHeight\t{sheet.attrib["name"]}\tA{row.attrib["r"]}:A{row.attrib["r"]}\t{row.attrib["ht"]}')
    apply_workbook_edits(Path(source),ops,Path(target))

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit(__doc__)
    simplify(*sys.argv[1:])
