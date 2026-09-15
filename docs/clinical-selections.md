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
| Medikation: Wirkstoffcode | Alle 232 UNII-Einzel- und Kombinationswerte des gepinnten Synthea-Mappings | BH30:BH261 |
| Impfung: Codesystem | ATC 2026, SNOMED CT, RxNorm, CVX | AY30:AY33 |
| Befundbericht und DocumentReference: Typcodesystem | LOINC, SNOMED CT | AZ30:AZ31 |
| Behandlungsplan und Hilfsmittel: Codesystem | SNOMED CT | BA30 |
| Laborbefund: Kategorie | laboratory | AD30 |
| Klinische Dokumentation: Kategorie | vital-signs, survey, social-history, exam, imaging, procedure, therapy, activity | AR30:AR37 |
| Impfung: Status | completed, entered-in-error, not-done | BB30:BB32 |
| Befundbericht: Status | registered, partial, preliminary, final, amended, corrected, appended, cancelled, entered-in-error, unknown | BC30:BC39 |
| Behandlungsplan: Absicht | proposal, plan, order, option | BD30:BD33 |

Die Diagnoseauswahl mit SNOMED CT und ICD-10-GM bleibt unverändert. PZN und ATC
werden weiterhin in ihren eigenen Medikationsspalten eingegeben; die neue
Originalcode-Auswahl bietet nur Systeme an, die dieser Konverterpfad unterstützt.
ASK gehört zur Wirkstoffauswahl, nicht zur Originalpräparat-Auswahl. Die gemeinsam
für mehrere Medikationstypen verwendete Statusspalte bleibt eine gemeinsame Liste;
die Wahl muss weiterhin zum Medikationstyp passen.

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
AG30:AG37, Verabreichung in BH30:BH36, Medikationsaussage in BI30:BI37.
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
