"""Visible, documented options for template copies; names match ConverterOptions."""
OPTIONS = [
 ('SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER', 'false', ['Diagnose verweist auf den zugehörigen Kontakt (Condition.encounter).']),
 ('SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION', 'true', ['Kontakt verweist auf seine Diagnosen (Encounter.diagnosis).']),
 ('SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER', 'false', ['Prozedur verweist auf den Kontakt (Procedure.encounter).']),
 ('SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION', 'true', ['Kontakt verweist auf Prozeduren in Encounter.diagnosis.']),
 ('ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER', 'false', ['Fehlende Diagnose des Unterkontakts aus dem übergeordneten Kontakt ergänzen.', 'Bei false erfolgt keine automatische Diagnoseübernahme.']),
 ('CHECK_INPUT_CONSISTENCY', 'true', ['Excel-Eingaben vor der Konvertierung auf Konsistenz prüfen und bei Fehlern abbrechen.', 'Tabellenstruktur und Optionen werden immer geprüft; FHIR-Validierung wird separat gesteuert.']),
]
for key,label in [('CONSENT','Consent'),('CONDITION','Diagnosen'),('ENCOUNTER_LEVEL_2','Abteilungskontakte'),
                  ('ENCOUNTER_LEVEL_3','Versorgungsstellenkontakte'),('MEDICATION_REQUEST','Verordnungen'),
                  ('MEDICATION_ADMINISTRATION','Verabreichungen'),('MEDICATION_STATEMENT','Medikationsaussagen'),
                  ('OBSERVATION_LABORATORY','Laborbefunde'),('OBSERVATION_VITAL_SIGNS','klinische Messwerte'),
                  ('PROCEDURE','Prozeduren'),('DOCUMENT_REFERENCE','Dokumentverweise')]:
    OPTIONS.append(('START_ID_'+key, '1', ['Startwert des automatisch erzeugten ID-Zählers für '+label+'.']))
OPTIONS += [
 ('PID_PREFIX', '', ['Präfix vor jeder Patienten-ID; Standard ist eine leere Zeichenfolge.']),
 ('PID_SUFFIX', '', ['Suffix nach jeder Patienten-ID; Standard ist eine leere Zeichenfolge.']),
 ('PID_LAST_NUMBER_INCREASE_INITIAL_OFFSET', '0', ['Anfangsversatz für die letzte Zahl in der Patienten-ID.', 'Nur verwenden, wenn die ID eine passende ganzzahlige Nummer enthält.']),
 ('PID_LAST_NUMBER_INCREASE_LOOP_OFFSET', '0', ['Versatz der letzten ID-Zahl je zusätzlichem Durchlauf.', 'Versatz so wählen, dass keine doppelten Patienten-IDs entstehen.']),
 ('PID_LAST_NUMBER_INCREASE_LOOP_COUNT', '0', ['Zusätzliche Durchläufe zur Vervielfachung des Datensatzes.', '0 bedeutet keine zusätzlichen Durchläufe; passende ID-Versätze festlegen.']),
]


def lines(overrides=None):
    overrides = overrides or {'CHECK_INPUT_CONSISTENCY': 'true'}
    result = ['# Konvertierungsoptionen', '# Gelbe Zeilen enthalten die eigentlichen Einstellungen.',
              '# # am Zeilenanfang: auskommentiert, der Default gilt.',
              '# Auskommentiert bedeutet nicht automatisch false oder ausgeschaltet.',
              '# Zum Ändern # vor der gewünschten Option entfernen und den Wert setzen.',
              '# true = ja; false = nein. Nur Spalte A wird als Konfiguration gelesen.',
              '# Referenzen in beide Richtungen können Zyklen bilden; zielsystemabhängig prüfen.', '']
    for name, default, description in OPTIONS:
        result.extend('# '+line for line in description)
        result.append('# Default: '+(default if default else '(leer)'))
        result.append(('' if name in overrides else '# ') + name + ' = ' + overrides.get(name, default))
        result.append('')
    return result


def workflow_defaults():
    return {name: default for name, default, _ in OPTIONS}


def resolve_config(path=None, patients=None):
    import json
    from pathlib import Path
    import subprocess
    root = Path(__file__).resolve().parents[1]
    request = {'text': Path(path).read_text(encoding='utf-8') if path is not None else '', 'defaults': {}}
    if path is not None:
        request['name'] = Path(path).stem
    if patients is not None:
        request['patients'] = patients
    result = subprocess.run(['java', '-cp', str(root / 'target/excel2fhir.jar'),
                             str(root / 'scripts/WorkflowOptions.java')],
                            input=json.dumps(request), capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Converter options could not be checked. '
                           'Rebuild the converter. ' + result.stderr.strip())
    resolved = json.loads(result.stdout)
    if resolved['errors']:
        raise ValueError('Invalid converter options:\n' + '\n'.join(resolved['errors']))
    return resolved


def property_line(name, value):
    # Preserve literal Properties values when the Excel options sheet is exported.
    value = str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    value = value.replace(' ', '\\u0020').replace(',', '\\u002c').replace(chr(34), '\\u0022')
    return name + ' =' + (' ' + value if value else '')


def selected_configs(files=(), patients=None):
    result = []
    names = set()
    for path in files or [None]:
        resolved = resolve_config(path, patients)
        name = resolved.get('name', 'Konvertierungsoptionen')
        if name.lower() in names:
            raise ValueError('Option sets have the same output name: ' + name)
        names.add(name.lower())
        result.append({'name': name, 'path': str(path) if path is not None else None, **resolved})
    return result
