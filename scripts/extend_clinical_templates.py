#!/usr/bin/env python3
"""Extend existing templates through UNO, retaining existing cells and styles.
Usage: extend_clinical_templates.py INPUT.xlsx OUTPUT.xlsx
"""
import base64
import re
from pathlib import Path
import sys
from clinical_events import SCHEMAS, PERSON_EXTRA, DOCUMENT_EXTRA
from clinical_import import OBS_EXTRA, PROCEDURE_EXTRA, MEDICATION_EXTRA
from synthea_to_excel import apply_workbook_edits, column_name, column_number
from workbook_xml import read_sheets
from clinical_selections import LISTS, SELECTIONS


def extend(source, target):
    sheets = read_sheets(source)
    ops = []
    def op(*args): ops.append('\t'.join(map(str, args)))
    def put(sheet, cell, value): op('set', sheet, cell, base64.b64encode(value.encode()).decode())
    lists = {
        'AC': ['Werttypen', 'Zahl', 'Text', 'Code', 'Ja/Nein', 'Komponenten', 'Fehlend'],
        'AE': ['Prozedurstatus', 'preparation', 'in-progress', 'not-done', 'on-hold', 'stopped', 'completed', 'entered-in-error', 'unknown'],
        'AF': ['Messwertstatus', 'registered', 'preliminary', 'final', 'amended', 'corrected', 'cancelled', 'entered-in-error', 'unknown'],
        'AG': ['Medikationsstatus', 'active', 'on-hold', 'cancelled', 'completed', 'entered-in-error', 'stopped', 'draft', 'unknown', 'in-progress', 'not-done'],
        'AQ': ['Dokumentstatus', 'current', 'superseded', 'entered-in-error'],
        'AI': ['Allergietyp', 'allergy', 'intolerance'],
        'AJ': ['Allergiekategorie', 'food', 'medication', 'environment', 'biologic'],
        'AK': ['Kritikalität', 'low', 'high', 'unable-to-assess'],
        'AL': ['Allergieverlauf', 'active', 'inactive', 'resolved'],
        'AM': ['Allergieverifikation', 'unconfirmed', 'presumed', 'confirmed', 'refuted', 'entered-in-error'],
        'AN': ['Ja/Nein', 'true', 'false'],
        'AO': ['Gerätestatus', 'active', 'inactive', 'entered-in-error', 'unknown'],
        'AP': ['Planstatus', 'draft', 'active', 'on-hold', 'revoked', 'completed', 'entered-in-error', 'unknown'],
        'AH': ['Verordnungsabsicht', 'proposal', 'plan', 'order', 'original-order', 'reflex-order', 'filler-order', 'instance-order', 'option'],
    }
    lists.update(LISTS)
    for col, values in lists.items():
        # Extend the style of the existing centralized selection lists.
        op('copy', 'Codes', 'AA29:AA60', col+'29')
        op('clear', 'Codes', col+'29:'+col+'60')
        if column_number(col) >= column_number('AR'):
            op('width', 'Codes', column_number(col)-1, 9000)
        for i, value in enumerate(values, 29): put('Codes', col+str(i), value)
    for sheet, extra in {'Person': PERSON_EXTRA, 'DocumentReference': DOCUMENT_EXTRA, 'Prozedur': PROCEDURE_EXTRA, 'Laborbefund': OBS_EXTRA,
                         'Klinische Dokumentation': OBS_EXTRA, 'Medikation': MEDICATION_EXTRA}.items():
        cells = sheets[sheet]
        if extra[0] in [v for k,v in cells.items() if k == k.rstrip('0123456789')+'1']:
            raise ValueError('Template already extended: '+sheet)
        hint = next(k[:-1] for k,v in cells.items() if k.endswith('1') and k[:-1].isalpha() and v=='Erklärung/Ausfüllhilfe')
        first = column_number(hint)
        op('insert', sheet, first-1, len(extra))
        for i, name in enumerate(extra, first):
            col = column_name(i)
            op('copy', sheet, 'C1:C1031', col+'1')
            op('clear', sheet, col+'1:'+col+'1031')
            op('text', sheet, col+'1:'+col+'1031')
            put(sheet, col+'1', name)
            op('width', sheet, i-1, 5200 if 'system' in name else 4000)
            selection = ('AC' if name=='Werttyp' else
                         'AD' if name=='Kategorie' and sheet!='Prozedur' else
                         ('AE' if sheet=='Prozedur' else 'AG' if sheet=='Medikation' else 'AQ' if sheet=='DocumentReference' else 'AF') if name=='Status' else
                         'AH' if name=='Absicht' else None)
            selection = SELECTIONS.get(sheet, {}).get(name, selection)
            if selection:
                end = 28 + len(lists[selection])
                op('validation', sheet, col+'2:'+col+'1031', '$Codes.$'+selection+'$30:$'+selection+'$'+str(end), 'true')
        hint = column_name(first+len(extra))
        explanations = {
            'Person': ['Straße, Postleitzahl, Ort, Bundesland und Land übernehmen eine strukturierte Anschrift.',
                       'Land ist der ISO-Ländercode, z.B. US oder DE. US-Adressen werden nicht zu deutschen Adressen umgedeutet.',
                       'Straße, Postleitzahl und Ort werden auch bei fehlendem Land übernommen.',
                       'Sterbezeitpunkt nur bei bekanntem Tod ausfüllen; leer bedeutet keine Angabe.',
                       'Einwilligungen nur ausfüllen, wenn sie tatsächlich dokumentiert sind.'],
            'DocumentReference': ['Dokumenttext enthält den eingebetteten Klartext; damit bleibt die Excel-Datei eigenständig nutzbar.',
                                  'Alternativ eine Dateipfad-Angabe mit Embed verwenden. Dokumenttext hat Vorrang.',
                                  'Lange Texte bleiben einzeilig angezeigt und können in der Bearbeitungsleiste gelesen werden.',
                                  'Dokumentcode und Dokumentcodesystem beschreiben den Dokumenttyp.',
                                  'Ausgabezeitpunkt ist das Dokumentdatum; current = aktuell, superseded = ersetzt.'],
            'Prozedur': ['Eine Zeile ist eine Prozedur; Originalcode und Codesystem gehören zusammen.',
                         'Dokumentationszeitpunkt enthält hier den Durchführungsbeginn; Ende ergänzt einen Zeitraum.',
                         'Zusatzcode ist optional. Kategorie ist ein SNOMED-Code; ohne Angabe wird nichts erfunden.',
                         'Status: completed = abgeschlossen, in-progress = laufend, not-done = nicht durchgeführt.',
                         'Fehlender Zeitpunkt kann ausdrücklich als !dar:unknown eingetragen werden.'],
            'Medikation': ['Eine Zeile ist eine Verordnung, Verabreichung oder Medikationsangabe.',
                           'Medikamentencode mit Codesystem bezeichnet das Originalpräparat; PZN/ATC bleiben für vorhandene Fälle nutzbar.',
                           'Bei Originalcodes ist Einzeldosis die Menge je Gabe; Anzahl Dosen pro Tag ist die Häufigkeit.',
                           'Wirkstoffcode beschreibt einen Inhaltsstoff. !dar:unknown bedeutet ausdrücklich unbekannt.',
                           'Zeitstempel ist Verordnungs-/Gabezeit; Ende ergänzt bei Verabreichungen einen Zeitraum.',
                           'Dosierungstext kann zusätzliche Hinweise enthalten; status active = aktiv, completed = abgeschlossen.'],
        }.get(sheet, ['Werttyp bestimmt die Bedeutung von Wert/Messwert: Zahl, Text, Code, Ja/Nein oder Komponenten.',
                     'Für Code-Werte zusätzlich Wertcode und Wertcodesystem ausfüllen; Wert enthält den lesbaren Text.',
                     'Untersuchung ID verbindet Komponenten mit ihrer Hauptzeile; Komponente von enthält diese ID.',
                     'Hauptzeile vor Komponenten eintragen. Komponenten erhalten keinen eigenen Messwertdatensatz.',
                     'Einheit ist die Anzeige, Einheitencode der UCUM-Code. Keine Einheit aus dem Text erraten.',
                     'Werttyp Fehlend verlangt !dar:unknown oder einen anderen DAR im Wertfeld.',
                     'Kategorie trennt Labor, Vitalwerte und Fragebögen; status final = endgültig, preliminary = vorläufig.'])
        op('clear', sheet, hint+'2:'+hint+'25')
        for i, text in enumerate(explanations,2):put(sheet,hint+str(i),text)
    for sheet, columns in SCHEMAS.items():
        op('copySheet', sheet, 'Prozedur')
        op('clear', sheet, 'A1:Z1031')
        for i, name in enumerate(columns + ['Erklärung/Ausfüllhilfe'], 1):
            col = column_name(i)
            op('copy', sheet, 'C1:C1031', col+'1')
            op('text', sheet, col+'1:'+col+'1031')
            put(sheet, col+'1', name)
            op('width', sheet, i-1, 6000 if name in ['Bezeichner','Beschreibung','Erklärung/Ausfüllhilfe'] else 4500)
            selection = {'Typ':'AI','Kategorie':'AJ','Kritikalität':'AK',
                         'Klinischer Status':'AL','Verifikationsstatus':'AM','Primärquelle':'AN',
                         'Absicht':'AH'}.get(name)
            if name=='Status': selection = 'AO' if sheet=='Hilfsmittel' else 'AP' if sheet=='Behandlungsplan' else 'AF' if sheet=='Befundbericht' else 'AE'
            selection = SELECTIONS.get(sheet, {}).get(name, selection)
            if selection:
                op('validation',sheet,col+'2:'+col+'1031', '$Codes.$'+selection+'$30:$'+selection+'$'+str(28+len(lists[selection])), 'true')
        hint=column_name(len(columns)+1)
        notes=['Eine Zeile ist ein klinischer Eintrag. Eintrag ID muss innerhalb des Patienten eindeutig sein.',
               'Code und Codesystem gehören zusammen; Bezeichner ist der lesbare Originaltext.',
               'Patient-ID und Fall-Nr verknüpfen den Eintrag mit Person und Kontakt.',
               'Zeitpunkt ist der Ereigniszeitpunkt; bei Allergien der Dokumentationszeitpunkt.',
               'Ergebnis- und Aktivitätscodes werden durch Semikolon getrennt, ohne Leerzeichen.',
               'Befund-Ergebnisse verweisen auf Untersuchung ID im Labor- oder Messwertblatt.',
               'Fehlende Zeitpunkte ausdrücklich mit !dar:unknown kennzeichnen; leere Felder bleiben leer.']
        for i,note in enumerate(notes,2):put(sheet,hint+str(i),note)
    # Historical base templates still have D=Anschrift. Migrate their examples
    # after inserting the structured columns, then remove the obsolete column.
    if sheets['Person'].get('D1') == 'Anschrift':
        for cell, value in sheets['Person'].items():
            if not re.fullmatch(r'D[0-9]+', cell) or cell == 'D1' or not value:
                continue
            address = re.fullmatch(r'(.+),\s*(\d{5})\s+(.+)', value)
            if address is None:
                raise ValueError('Address requires explicit migration: ' + value)
            for col, text in zip(('N', 'O', 'P', 'R'), (*address.groups(), 'DE')):
                put('Person', col + cell[1:], text)
        op('removeColumns', 'Person', 3, 1)
    apply_workbook_edits(Path(source),ops,Path(target))


if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit(__doc__)
    extend(*sys.argv[1:])
