"""Presentation labels for explicit missing inputs; clinical source data stays unchanged."""
import json
from pathlib import Path

LABELS = json.loads((Path(__file__).resolve().parents[1] /
                    'src/main/resources/workbook-absent-reasons.json').read_text())
CODES = {label: '!dar:' + code for code, label in LABELS.items()}
# Read previously generated workbooks as well; new output always uses the suffix.
CODES.update({label.removesuffix(' (Data Absent Reason)'): '!dar:' + code
              for code, label in LABELS.items()})

# Only fields whose converters explicitly interpret missing values. Never translate
# arbitrary names, identifiers, narratives or ordinary status codes.
FIELDS = {
    'Fall': {'Aufnahmegrund (4. Stelle)'},
    'Diagnose': {'Code', 'Zusatzcode', 'Dokumentationszeitpunkt', 'Beginn', 'Ende',
                 'Klinischer Status', 'Verifikationsstatus'},
    'Prozedur': {'Prozedurencode', 'Zusatzcode', 'Durchführungsbeginn', 'Ende'},
    'Medikation': {'Präparatcode', 'ATC-Code', 'Wirkstoffcode', 'Dokumentationszeitpunkt',
                   'Beginn', 'Ende', 'Einzeldosis'},
    'Laborbefund': {'LOINC', 'Zusatzcode', 'Zeitstempel (Abnahme)', 'Wertcode'},
    'Klinische Dokumentation': {'Untersuchungscode', 'Zusatzcode', 'Zeitstempel', 'Wertcode'},
    'Impfung': {'Code', 'Zeitpunkt'},
    'Befundbericht': {'Code', 'Zeitpunkt'},
    'Behandlungsplan': {'Code', 'Zeitpunkt', 'Ende'},
    'DocumentReference': {'Dokumentcode'},
}


def missing_field(sheet, header, row):
    return header in FIELDS.get(sheet, ()) or (sheet in ('Laborbefund', 'Klinische Dokumentation')
            and header in ('Messwert', 'Wert') and row.get('Werttyp') == 'Fehlend')


def display(sheet, header, value, row):
    if missing_field(sheet, header, row) and isinstance(value, str):
        token = CODES.get(value, value)
        if token.startswith('!dar:'):
            return LABELS.get(token[5:], value)
    return value


def canonical(sheet, row):
    return {header: CODES.get(value, value) if missing_field(sheet, header, row) else value
            for header, value in row.items()}
