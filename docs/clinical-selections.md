# Auswahllisten nach Eingabezweck

Die zentrale Gesamtliste für klinische Codesysteme wurde durch fachlich
abgegrenzte Bereiche im Blatt **Codes** ersetzt. Identische Auswahlen teilen sich
einen Bereich; SNOMED CT darf in mehreren unterschiedlichen Bereichen vorkommen.
Die Auswahl betrifft die konkrete Spalte, nicht pauschal das ganze Tabellenblatt.

| Eingabe | Auswahl | Bereich im Blatt Codes |
| --- | --- | --- |
| Laborbefund: Codesystem der Untersuchung | LOINC | AS30 |
| Klinische Dokumentation: Codesystem und Zusatzcodesystem der Untersuchung | LOINC, SNOMED CT | AT30:AT31 |
| Beide Messwertblätter: Wertcodesystem | LOINC, SNOMED CT | AU30:AU31 |
| Prozedur: Codesystem und Zusatzcodesystem | SNOMED CT, OPS 2009–2026 | AB30:AB48 |
| Medikation: Präparatcodesystem | PZN, SNOMED CT, RxNorm, CVX | AV30:AV33 |
| Medikation: Wirkstoffcodesystem | ASK, UNII, SNOMED CT, RxNorm | AW30:AW33 |
| Medikation: Wirkstoffcode | Alle 232 UNII-Einzel- und Kombinationswerte des gepinnten Synthea-Mappings | BH30:BH267 (232 Codes plus sechs Fehlgründe) |
| Impfung: Codesystem | ATC 2026, SNOMED CT, RxNorm, CVX | AY30:AY33 |
| Befundbericht und DocumentReference: Typcodesystem | LOINC, SNOMED CT | AZ30:AZ31 |
| Behandlungsplan und Hilfsmittel: Codesystem | SNOMED CT | BA30 |
| Laborbefund: Kategorie | laboratory | AD30 |
| Klinische Dokumentation: Kategorie | vital-signs, survey, social-history, exam, imaging, procedure, therapy, activity | AR30:AR37 |
| Impfung: Status | completed, entered-in-error, not-done | BB30:BB32 |
| Befundbericht: Status | registered, partial, preliminary, final, amended, corrected, appended, cancelled, entered-in-error, unknown | BC30:BC39 |
| Behandlungsplan: Absicht | proposal, plan, order, option | BD30:BD33 |

Die Diagnose-Codesystemauswahl mit SNOMED CT und ICD-10-GM bleibt erhalten. PZN und ATC
werden weiterhin in ihren eigenen Medikationsspalten eingegeben; die
Präparatcode-Auswahl bietet nur Systeme an, die dieser Konverterpfad unterstützt.
ASK gehört zur Wirkstoffauswahl, nicht zur Originalpräparat-Auswahl. Die Statusliste richtet sich nach dem Medikationstyp derselben Zeile.

Die Wirkstoffcode-Liste wird aus dem öffentlichen Medikamentenmapping erzeugt.
Mehrere Wirkstoffe bleiben semikolongetrennt in einer Auswahlzelle. Der Synthea-Import
füllt den passenden Wert und das System UNII; die Liste ist eine Eingabehilfe und
keine vollständige Wertemengenbindung für alle manuell erfassten Medikamente.

## Grundlage und Grenzen

Geprüft wurden die tatsächlich eingebundenen lokalen Pakete aus
`src/main/resources/fhir/` und die aktuellen Konverter:

- KDS Laborbefund 2026.0.3, `mii-pr-labor-laboruntersuchung`: Kategorie
  `laboratory`, bevorzugte LOINC-Bindung für den Untersuchungscode. Die Eingabeliste
  beschränkt sich hier auf LOINC. Das ist eine bewusste Vorlagenauswahl, keine
  Behauptung, das Profil verbiete grundsätzlich alle anderen Systeme.
  Codierte Antworten sind eine eigene Eingabe und können auch SNOMED CT verwenden.
- KDS Basis 2026.0.1, `mii-pr-prozedur-procedure`: Codings für OPS und SNOMED CT.
- KDS Medikation 2026.0.1, `mii-pr-medikation-medication`: Unterscheidung zwischen
  Medikamenten- und Inhaltsstoffcodings; zusätzlich berücksichtigte R4-/Synthea-
  Originalcodes müssen im vorhandenen Konverter unterstützt sein.
- FHIR R4 4.0.1: `ValueSet-immunization-status`,
  `CodeSystem-diagnostic-report-status` und `ValueSet-care-plan-intent` bestimmen
  die drei korrigierten Status-/Absicht-Auswahlen. Die bisherigen Verweise auf
  Prozedurstatus, Messwertstatus bzw. die größere Verordnungsabsicht-Liste passten
  nicht zu diesen Ressourcen.

Die Listen sind eine Eingabehilfe. Sie validieren weder einzelne Terminologiecodes
noch sämtliche ressourcenabhängigen Beziehungen. Der Konverterumfang und die
vorhandenen klinischen Daten werden durch diese Änderung nicht erweitert.

## Pflege

`scripts/clinical_selections.py INPUT.xlsx OUTPUT.xlsx` aktualisiert die
Auswahlbereiche und Verknüpfungen mit LibreOffice/UNO in einer vorhandenen Kopie.
Das Ziel darf noch nicht existieren. Daten und Eingabeblatt-Layout bleiben erhalten;
automatische Zeilenhöhen werden beim Speichern auf ihrer bisherigen Höhe fixiert,
damit LibreOffice sie nicht anhand lokal ersetzter Schriften neu berechnet.
Neue Auswahlbereiche übernehmen die vorhandene Formatierung und erhalten genug
Breite für die Codesystembezeichnungen. Ausgangspunkt für neue Fälle ist die aktuelle Projektvorlage. Das einmalige
historische Erweiterungsskript ist nach dem Medikationsumbau entfernt.

Medikationsstatus ist zusätzlich nach Medikationstyp getrennt: Verordnung in
AG30:AG37, Verabreichung in BJ30:BJ36, Medikationsaussage in BI30:BI37.
Die Statusauswahl in Spalte L verwendet den Medikationstyp derselben Zeile.

Laborbefunde haben eine eigene Werttypenliste BG30:BG34 mit Zahl, Text, Code,
Komponenten und Fehlend. Ja/Nein bleibt nur für klinische Dokumentation verfügbar.

Untersuchungscode, Codesystem, Zusatzcode und Zusatzcodesystem stehen in den
Messwertblättern unmittelbar zusammen. Beide Codierungen beschreiben denselben
Begriff unter Observation.code (auch bei Komponenten). Ergebniscode/-system
bleiben unter Observation.valueCodeableConcept getrennt. Labor-Zusatzcodierung
verwendet die Liste AT30:AT31; das primäre Laborfeld bleibt LOINC.

Konvertierungsoptionen stehen als erstes Blatt vollständig und mit gelben
Optionszeilen in beiden Vorlagen. Auskommentierte Zeilen verwenden den
dokumentierten Standard; sie bedeuten nicht automatisch false. Auch generierte
Fälle behalten die Erläuterungen und alle Optionen. Der Import aktiviert nur
seine fünf bisherigen Einstellungen an deren vorhandener Stelle. Veraltete
Optionsnamen aus früheren Vorlagen wurden durch die tatsächlichen Enum-Namen
ersetzt. Ein Regressionstest gleicht die Liste mit sämtlichen Java-Optionen ab.

## Feldprüfung der Vorlagen (September 2026)

Alle Eingabeblätter werden über `clinical_selections.py` anhand ihrer bestehenden
Spaltenüberschriften verknüpft. Alte Zellprüfungen werden vorher entfernt,
auch außerhalb der befüllten Zeilen. Überschriften, Ausfüllhilfen, Freitext,
Patienten-/Kontakt-IDs und Ortsnamen erhalten keine fremden Auswahllisten.
Der Import erneuert die Verknüpfungen bis zur letzten tatsächlich erzeugten Zeile.

| Blatt | Geprüfte Eingaben und Fehlgrund-Verhalten |
| --- | --- |
| Person | Geschlecht einschließlich divers in jeder Zeile; Einwilligungen ja/nein. Keine DAR-Auswahl für Identität, Adresse oder Geburtsdatum. |
| Fall | Klasse, Fachabteilung, Kontaktebene, Kontaktart und Aufnahmegrund; Station, Zimmer und Bett sind freie Ortsnamen. Nur Aufnahmegrund hat Fehlgründe. |
| Diagnose | Getrennte System-, Status-, Typ-, Code- und Zeitpunktlisten. Keine numerischen Fehlergründe beim Status. |
| Prozedur | OPS/SNOMED, Prozedurstatus, freie Codes und Zeitpunkte mit expliziten Fehlgründen. |
| Medikation | Status abhängig vom Medikationstyp derselben Zeile; Wirkstoffliste separat. Fehlgründe bei Codes, Ereigniszeiten und Einzeldosis, nicht bei Häufigkeit oder Status. |
| Laborbefund | LOINC, Zusatz-/Antwortsysteme, Laborwerttypen und Messwertstatus; Fehlgrund in Messwert nur zusammen mit Werttyp Fehlend. |
| Klinische Dokumentation | Untersuchungs-/Antwortsysteme, Kategorien, Werttypen einschließlich Ja/Nein, Messwertstatus; Bezeichner und Textwerte bleiben frei. |
| DocumentReference | Embed ja/nein, Dokumentstatus, Dokumentcodesystem; Fehlgrund nur beim Dokumentcode, nicht beim Dateipfad oder Ausgabezeitpunkt. |
| Impfung | Impfstatus, Primärquelle true/false, Codesystem; Fehlgründe bei Code und Impfzeitpunkt. |
| Befundbericht | Eigener Berichtstatus, Codesystem; Fehlgründe bei Code und Untersuchungszeitpunkt, nicht beim Ausgabezeitpunkt. |
| Behandlungsplan | Planstatus und Planabsicht, Codesystem; Fehlgründe bei Code und Gültigkeitszeitraum. |
| Hilfsmittel | Gerätestatus und Codesystem; Fehlgrund nur beim Code. |
| Konvertierungsoptionen | Vorhandene Optionen und deren dokumentierte Vorgaben unverändert. |

Die deutsch beschrifteten Fehlgründe stehen zentral in
`src/main/resources/workbook-absent-reasons.json`; Java und Python verwenden dieselbe
Zuordnung. Alle Fehlgrund-Auswahlen tragen den Zusatz ` (Data Absent Reason)`,
z.B. `Unbekannt (Data Absent Reason)`. Frühere Eingaben ohne Zusatz bleiben lesbar;
neu erzeugte und aktualisierte Dateien verwenden die markierte Darstellung.
Die sechs allgemeinen Gründe sind Unbekannt, Erfragt aber unbekannt,
Noch nicht bekannt, Nicht erhoben, Auskunft verweigert und Aus Datenschutzgründen
verborgen. Codefelder bieten zusätzlich fehlende Quellsystemunterstützung und einen
im Zielprofil nicht zulässigen Wert an. Messwerte haben eine eigene Auswahl mit
Nicht zutreffend, technischen Messfehlern und Untersuchung nicht durchgeführt.
`as-text` wird nicht angeboten: Eine Notiz in Excel erzeugt nicht automatisch die
FHIR-Ressourcennarrative, auf die dieser Fehlgrund ausdrücklich verweist.

Das sind bewusst passende Eingabehilfen, keine vollständigen ValueSets und keine
Behauptung, DAR erfülle jede Profilpflicht. Insbesondere bleiben die Bedingungen
für Diagnosestatus, `mad-1` für Verabreichungsdosierung sowie Pflichtzeiten bestehen.
FHIR-Statuswerte wie `unknown` sind echte Statuscodes und werden nicht in DAR
umgewandelt. Bestehende CSV-/Excel-Eingaben mit `!dar:<code>` bleiben unterstützt;
absichtlich ungültige Testcodes bleiben frei eingebbar. Zahlen-, Datums-, Wirkstoff-
und Codevorschläge sperren deshalb keine abweichenden manuellen Eingaben.

Grundlagen: eingebundene KDS-Pakete und die jeweiligen Konverter sowie
[FHIR R4 DataAbsentReason](https://hl7.org/fhir/R4/codesystem-data-absent-reason.html)
und [primitive Erweiterungen](https://hl7.org/fhir/R4/extensibility.html#primitives).
Ein leeres Feld wird nicht allein durch diese Listen zum Fehlwert. Bereits
bestehende, dokumentierte Standardwerte etwa für Medikationsstatus bleiben erhalten.
