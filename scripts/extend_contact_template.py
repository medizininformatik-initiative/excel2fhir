#!/usr/bin/env python3
"""Add explicit contact columns to a copy of an existing template using UNO.
Usage: extend_contact_template.py INPUT.xlsx OUTPUT.xlsx
"""
import base64
from pathlib import Path
import sys
from synthea_to_excel import apply_workbook_edits
from workbook_xml import read_sheets


def extend(source,target):
    sheets=read_sheets(source)
    assert sheets['Fall'].get('K1')=='Erklärung/Ausfüllhilfe', 'Expected unextended Fall sheet'
    ops=['insert\tFall\t10\t4']
    def put(sheet,cell,value):ops.append('\t'.join(['set',sheet,cell,base64.b64encode(value.encode()).decode()]))
    for col,header in zip('KLMN',['Kontakt-ID','Kontaktebene','Kontaktart','Übergeordneter Kontakt']):
        ops.extend([f'copy\tFall\tB1:B1031\t{col}1',f'clear\tFall\t{col}1:{col}1031',f'text\tFall\t{col}1:{col}1031'])
        put('Fall',col+'1',header)
        ops.append(f'width\tFall\t{ord(col)-65}\t6000')
    for col,values in {'BE':['Kontaktebene','Einrichtungskontakt','Abteilungskontakt','Versorgungsstellenkontakt'],
                       'BF':['Kontaktart','Normalstationär','Intensivstationär','Operation','Untersuchung und Behandlung','Konsil']}.items():
        ops.extend([f'copy\tCodes\tAA29:AA60\t{col}29',f'clear\tCodes\t{col}29:{col}60'])
        for i,value in enumerate(values,29):put('Codes',col+str(i),value)
        ops.append(f'width\tCodes\t{56 if col=="BE" else 57}\t9000')
    ops.extend(['validation\tFall\tL2:L1031\t$Codes.$BE$30:$BE$32\ttrue',
                'validation\tFall\tM2:M1031\t$Codes.$BF$30:$BF$34\ttrue'])
    notes=['Eine Zeile mit Kontaktebene beschreibt genau einen Kontakt; Fall-Nr verbindet alle Kontakte eines Falles.',
           'Einrichtungskontakt: Kontakt-ID entspricht Fall-Nr; Übergeordneter Kontakt bleibt leer.',
           'Abteilungskontakt: eigene Kontakt-ID; übergeordnete ID verweist auf den Einrichtungskontakt.',
           'Versorgungsstellenkontakt: eigene Kontakt-ID; übergeordnete ID verweist auf den Abteilungskontakt.',
           'Elternkontakt vor seinen untergeordneten Kontakten eintragen. Kontakt-IDs innerhalb des Falles eindeutig halten.',
           'Start und Ende gelten für diese Zeile. Untergeordnete Zeiträume liegen innerhalb des Elternkontakts.',
           'Kontaktart Operation bezeichnet den OP-Aufenthalt einschließlich Vor- und Nachbereitung.',
           'Station, Zimmer und Bett benennen Orte. Für einen Wechsel eine neue Versorgungsstellen-Zeile verwenden.',
           'Aufnahmegrund nur beim Einrichtungskontakt angeben. Die Kontaktklasse bleibt innerhalb des Falles gleich.',
           'Ohne die vier neuen Kontaktangaben gilt die bisherige Ableitung aus Fachabteilung und Ortswechseln.']
    ops.append('clear\tFall\tO2:O25')
    for i,note in enumerate(notes,2):put('Fall','O'+str(i),note)
    apply_workbook_edits(Path(source),ops,Path(target))

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit(__doc__)
    extend(*sys.argv[1:])
