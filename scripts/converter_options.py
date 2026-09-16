"""Visible, documented options for template copies; names match ConverterOptions."""
OPTIONS = [
 ('SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER', 'false', ['Diagnose verweist auf den zugehörigen Kontakt (Condition.encounter).']),
 ('SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION', 'true', ['Kontakt verweist auf seine Diagnosen (Encounter.diagnosis).']),
 ('SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER', 'false', ['Prozedur verweist auf den Kontakt (Procedure.encounter).']),
 ('SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION', 'true', ['Kontakt verweist auf Prozeduren in Encounter.diagnosis.']),
 ('ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER', 'false', ['Fehlende Diagnose des Unterkontakts aus dem übergeordneten Kontakt ergänzen.', 'Bei false wird fehlendes Wissen als Data Absent Reason gekennzeichnet.']),
 ('VALIDATE_STRICT', 'true', ['Excel-Eingaben vor der Konvertierung streng prüfen und bei Fehlern abbrechen.', 'Dies ersetzt nicht die anschließende FHIR-Validierung.']),
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
    'VALIDATE_STRICT': 'true',
}


def lines(overrides=None):
    overrides = overrides or {'VALIDATE_STRICT': 'true'}
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
