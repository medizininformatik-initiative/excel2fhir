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
SYNTHEA_OVERRIDES = {
    'SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER': 'true',
    'SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION': 'false',
    'SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER': 'true',
    'SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION': 'false',
    'CHECK_INPUT_CONSISTENCY': 'true',
}


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


CONFIG_NAME = 'converter-options.config'


def workflow_defaults():
    return {name: SYNTHEA_OVERRIDES.get(name, default) for name, default, _ in OPTIONS}


def config_text():
    result = ['# Konvertierungsoptionen für Synthea → Excel → FHIR',
              '# Vorhandene Dateien werden nicht überschrieben.',
              '# Aktive Angaben überschreiben die Workflow-Defaults.',
              '# Fehlende oder auskommentierte Angaben verwenden den Workflow-Default.',
              '# true = ja; false = nein.', '']
    for name, default, description in OPTIONS:
        value = SYNTHEA_OVERRIDES.get(name, default)
        result.extend('# ' + line for line in description)
        result.extend(['# Workflow-Default: ' + (value or '(leer)'), name + ' =' + (' ' + value if value else ''), ''])
    return '\n'.join(result).rstrip() + '\n'


def ensure_config(directory):
    from pathlib import Path
    path = Path(directory) / CONFIG_NAME
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open('x', encoding='utf-8') as file:
            file.write(config_text())
    except FileExistsError:
        pass
    return path


def resolve_config(path, patients=None):
    import json
    from pathlib import Path
    import subprocess
    root = Path(__file__).resolve().parents[1]
    request = {'text': Path(path).read_text(encoding='utf-8'), 'defaults': workflow_defaults()}
    if patients is not None:
        request['patients'] = patients
    result = subprocess.run(['java', '-cp', str(root / 'target/excel2fhir.jar'),
                             str(root / 'scripts/WorkflowOptions.java')],
                            input=json.dumps(request), capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Konvertierungsoptionen konnten nicht geprüft werden. '
                           'Converter neu bauen. ' + result.stderr.strip())
    resolved = json.loads(result.stdout)
    if resolved['errors']:
        raise ValueError('Ungültige Konvertierungsoptionen:\n' + '\n'.join(resolved['errors']))
    return resolved


def property_line(name, value):
    # Preserve literal Properties values when the Excel options sheet is exported.
    value = str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    value = value.replace(' ', '\\u0020').replace(',', '\\u002c').replace(chr(34), '\\u0022')
    return name + ' =' + (' ' + value if value else '')
