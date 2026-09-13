# Eingabespalten und offene Inkonsistenzen der Vorlagen

Geprüft wurden die Überschriften, Ausfüllhilfen und Auswahllisten beider ausgelieferten Arbeitsmappen sowie ihre Verwendung in den Konvertern. Die folgende Liste dokumentiert konkrete Befunde; sie ist keine vollständige fachliche Prüfung aller Eingabekombinationen.

## Person: Anschrift bereinigt

Die frühere Freitextspalte `Anschrift` ist entfernt. Anschriften werden ausschließlich in `Straße`, `Postleitzahl`, `Ort`, `Bundesland` und `Land` erfasst. Vorhandene Beispieladressen wurden in die strukturierten Felder übertragen. Alle anderen Person-Spalten rechts von der früheren Spalte D rücken um eine Spalte nach links; die übrigen Blätter bleiben unverändert. Die Ausfüllhilfe steht jetzt in S.

Der Konverter übernimmt vorhandene Adressbestandteile auch ohne Land. Ein leeres Land bedeutet unbekannt, nicht automatisch DE. Eine vollständig leere Adresse bekommt Data Absent Reason unknown. Bei Land DE werden ausgeschriebene Bundesländer im FHIR in ISO-Codes umgewandelt. Unbekannte Länder oder Adressbestandteile werden nicht erfunden. Alte Mappen mit `Anschrift` passen nicht mehr zum aktuellen Schema und müssen auf die neuen Spalten migriert werden.

## Bestätigte weitere Befunde – noch nicht umgebaut

| Priorität | Blatt / Felder | Tatsächliche Wirkung | Empfohlene Bereinigung |
|---|---|---|---|
| Hoch | Person: Krankenkasse | `PatientConverter.parseHealthProvider()` schreibt den Text nach `Patient.generalPractitioner.display`. Eine Krankenkasse ist kein behandelnder Leistungserbringer. | Zweck klären: Versicherungsangabe korrekt modellieren oder Feld entfernen. Nicht einfach zu Hausarzt umbenennen und bestehende Kassenwerte damit umdeuten. |
| Hoch | Medikation: Medikamentencode/Codesystem gegenüber PZN Code/ATC Code; Wirkstoffcode gegenüber ASK | Sobald Medikamentencode vorhanden ist, werden PZN/ATC und der ASK-Zweig nicht verwendet. Die Felder sind gleichzeitig sichtbar, haben aber eine versteckte Vorrangregel. | Präparatecodierungen und Wirkstoffcodierung ausdrücklich trennen und eine eindeutige Eingabelogik festlegen. PZN, ATC und Wirkstoff sind fachlich nicht austauschbar. |
| Hoch | Medikation: Therapiestart, Therapieende | Die Methode `convertPeriod()` liest diese Spalten, wird aber nirgends aufgerufen. Tatsächlich zählen Zeitstempel und bei MedicationAdministration das zusätzliche Ende. | Unwirksame Felder entfernen oder bewusst anschließen; vorher festlegen, welcher Zeitpunkt Verordnung, Gabe oder dokumentierten Therapiezeitraum meint. |
| Hoch | Medikation: Medikationsplanart | Enum/Feld vorhanden, keine aktive Verwendung im Konverter. | Entfernen, sofern kein konkretes Zielfeld benötigt wird. |
| Hoch | Laborbefund: Werttyp Ja/Nein | Die gemeinsame Werttypenliste bietet Ja/Nein an; der Konverter erzeugt dann valueBoolean. Das deklarierte KDS-Laborprofil erlaubt dort Quantity, CodeableConcept, Range oder Ratio. | Blattbezogene Werttypen bzw. eine fachlich definierte codierte Abbildung verwenden. Nicht stillschweigend beliebige Codes erfinden. |
| Mittel | Medikation: Status | Gemeinsame Liste für MedicationRequest, MedicationAdministration und MedicationStatement. Nicht alle Statuswerte gelten für jeden Ressourcentyp; der Konverter verwendet jeweils dessen Enum. | Auswahl nach Medikationstyp einschränken und unpassende Kombinationen früh erklären. |
| Mittel | Prozedur: Dokumentationszeitpunkt | Wird als `Procedure.performed[x]`, also Durchführungsbeginn, geschrieben. Der Name suggeriert wie bei Condition einen Dokumentationszeitpunkt. | Eindeutig in Durchführungsbeginn umbenennen; einen separaten Dokumentationszeitpunkt nur bei tatsächlichem Bedarf einführen. |
| Mittel | Laborbefund und Klinische Dokumentation: LOINC plus Codesystem | In Klinische Dokumentation kann unter der Überschrift LOINC auch ein SNOMED-Code stehen. Im Labor ist dagegen nur LOINC auswählbar, sodass Codesystem dort keine echte Auswahl bietet. | Klinische Untersuchungscodes allgemein benennen; für Labor zwischen festem LOINC-System und einer bewusst einheitlichen Eingabekonvention entscheiden. |
| Mittel | Medikation: Dosierungstext / Ausfüllhilfe | Die Hilfe spricht von zusätzlichen Hinweisen. Tatsächlich werden bei Freitext oder unvollständiger strukturierter Dosierung inzwischen alle importierten Fakten in Dosage.text zusammengeführt, damit DosageDE eingehalten wird. | Hilfe auf das aktuelle Verhalten abstimmen und die kombinierte Textdarstellung menschlich reviewen. |

## Bewusste Alternativen, keine pauschal zu löschenden Doppelungen

- `DocumentReference`: Dokumenttext und Dateipfad/Embed sind unterschiedliche Quellen. Dokumenttext hat Vorrang; Dateipfad/Embed werden dann nicht für den Inhalt verwendet. Das ist dokumentiert, könnte aber eine Prüfung auf gleichzeitig ausgefüllte Quellen vertragen.
- Diagnose: Code und Zusatzcode ermöglichen mehrere Codierungen desselben Befunds. SNOMED und ICD-10-GM sind nicht allein wegen zweier Codefelder redundant.
- Diagnose: Dokumentationszeitpunkt, Beginn und Ende entsprechen recordedDate, onset und abatement. Sie beschreiben unterschiedliche Sachverhalte.
- Messwerte: Einheit ist der Lesetext, Einheitencode der maschinenlesbare UCUM-Code. Ebenso sind Untersuchungscode und Wertcode unterschiedliche Konzepte.
- Fall: Einrichtung, Abteilung und Versorgungsstelle sind beabsichtigte Kontaktebenen. Die Identifikatoren und Elternbezüge sind keine doppelten Fallnummern.

Die nächsten Änderungen sollten zuerst die fachlich falsche Krankenkassenabbildung und die versteckten bzw. wirkungslosen Medikationsfelder behandeln. Die Anschriftbereinigung entfernt diese anderen Felder ausdrücklich noch nicht.

## Prüfung der Anschriftbereinigung

47 Java-Tests und 39 Python-Tests bestanden. Drei Synthea-Beispiele wurden mit der bereinigten Vorlage neu erzeugt und vollständig zurückkonvertiert. Die 20.844 FHIR-Ressourcen sind inhaltlich identisch zum zuvor validierten Stand. Deshalb wurden die unveränderten FHIR-Bundles nicht nochmals einer vollständigen Terminologieprüfung unterzogen. Vorlagenstile, Zeilenhöhen, verschobene Spaltenbreiten und Auswahllisten wurden gegen die vorherigen Dateien geprüft; die neuen Adressbereiche beider Mappen wurden visuell kontrolliert.
