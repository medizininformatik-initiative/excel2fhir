#!/usr/bin/env python3
"""Apply field-specific central dropdown lists to an existing clinical workbook.

Usage: clinical_selections.py INPUT.xlsx OUTPUT.xlsx
The output must be new; clinical values and input-sheet formatting are preserved.
"""
import base64
from pathlib import Path
import sys
from zipfile import ZipFile
import xml.etree.ElementTree as ET

from synthea_to_excel import apply_workbook_edits, column_number
from workbook_xml import read_sheets, NS

SNOMED = 'SNOMED CT (Version nicht angegeben)'

# The lists describe supported input choices, not exhaustive terminology bindings.
# Evidence and the distinction between measurement and answer: docs/clinical-selections.md.
LISTS = {
    'AB': ['Prozeduren', SNOMED] + ['OPS ' + str(year) for year in range(2009, 2027)],
    'AD': ['Labor', 'laboratory'],
    'AR': ['Klinische Messwertkategorien', 'vital-signs', 'survey', 'social-history', 'exam',
           'imaging', 'procedure', 'therapy', 'activity'],
    'AS': ['Labor: Untersuchungscode', 'LOINC'],
    'AT': ['Klinische Untersuchungscodes', 'LOINC', SNOMED],
    'AU': ['Codierte Messwertantworten', 'LOINC', SNOMED],
    'AV': ['Medikamentencodes', 'PZN', SNOMED, 'RxNorm', 'CVX'],
    'AW': ['Wirkstoffcodes', 'ASK', SNOMED, 'RxNorm'],
    'AY': ['Impfstoffcodes', 'ATC 2026', SNOMED, 'RxNorm', 'CVX'],
    'AZ': ['Befund- und Dokumenttypen', 'LOINC', SNOMED],
    'BA': ['Plan- und Hilfsmittelcodes', SNOMED],
    'BB': ['Impfstatus', 'completed', 'entered-in-error', 'not-done'],
    'BC': ['Befundstatus', 'registered', 'partial', 'preliminary', 'final', 'amended',
           'corrected', 'appended', 'cancelled', 'entered-in-error', 'unknown'],
    'BG': ['Labor-Werttypen', 'Zahl', 'Text', 'Code', 'Komponenten', 'Fehlend'],
    'BD': ['Behandlungsplan: Absicht', 'proposal', 'plan', 'order', 'option'],
}

# Header-based addressing works for both templates and filled cases.
SELECTIONS = {
    'Laborbefund': {'Werttyp': 'BG', 'Codesystem': 'AS', 'Wertcodesystem': 'AU', 'Kategorie': 'AD'},
    'Klinische Dokumentation': {'Codesystem': 'AT', 'Wertcodesystem': 'AU', 'Kategorie': 'AR'},
    'Prozedur': {'Codesystem': 'AB', 'Zusatzcodesystem': 'AB'},
    'Medikation': {'Präparatcodesystem': 'AV', 'Wirkstoffcodesystem': 'AW'},
    'Impfung': {'Codesystem': 'AY', 'Status': 'BB'},
    'Befundbericht': {'Codesystem': 'AZ', 'Status': 'BC'},
    'DocumentReference': {'Dokumentcodesystem': 'AZ'},
    'Behandlungsplan': {'Codesystem': 'BA', 'Absicht': 'BD'},
    'Hilfsmittel': {'Codesystem': 'BA'},
}


def selection_ops(sheets):
    ops = []

    def op(*args):
        ops.append('\t'.join(map(str, args)))

    def put(sheet, cell, value):
        op('set', sheet, cell, base64.b64encode(value.encode()).decode())

    for col, values in LISTS.items():
        if col not in ('AB', 'AD') and col + '29' not in sheets['Codes']:
            op('copy', 'Codes', 'AA29:AA60', col + '29')
            op('width', 'Codes', column_number(col) - 1, 9000)
        for row in range(29, 61):
            put('Codes', col + str(row), values[row - 29] if row - 29 < len(values) else '')

    for sheet, selections in SELECTIONS.items():
        cells = sheets[sheet]
        headers = {v: k[:-1] for k, v in cells.items() if k[:-1].isalpha() and k.endswith('1')}
        last_row = max(1031, max(int(k.lstrip('ABCDEFGHIJKLMNOPQRSTUVWXYZ')) for k in cells))
        for header, source in selections.items():
            col = headers[header]
            op('validation', sheet, f'{col}2:{col}{last_row}',
               f'$Codes.${source}$30:${source}${28 + len(LISTS[source])}', 'true')
    return ops


def update(source, target):
    ops = selection_ops(read_sheets(source))
    # LibreOffice may recalculate automatic row heights while saving. Preserve
    # their displayed heights as well as the explicitly sized template rows.
    with ZipFile(source) as archive:
        rels = {r.attrib['Id']: r.attrib['Target'] for r in
                ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))}
        for sheet in ET.fromstring(archive.read('xl/workbook.xml')).findall('s:sheets/s:sheet', NS):
            path = rels[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
            path = path.lstrip('/') if path.startswith('/') else 'xl/' + path
            groups = []
            for row in ET.fromstring(archive.read(path)).findall('s:sheetData/s:row', NS):
                if 'ht' not in row.attrib:
                    continue
                number, height = int(row.attrib['r']), row.attrib['ht']
                if groups and groups[-1][1] + 1 == number and groups[-1][2] == height:
                    groups[-1][1] = number
                else:
                    groups.append([number, number, height])
            for first, last, height in groups:
                ops.append(f'rowHeight\t{sheet.attrib["name"]}\tA{first}:A{last}\t{height}')
    apply_workbook_edits(Path(source), ops, Path(target))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    update(*sys.argv[1:])
