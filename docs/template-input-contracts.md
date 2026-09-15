# Eingabespalten und offene Inkonsistenzen der Vorlagen

Geprüft wurden die Überschriften, Ausfüllhilfen und Auswahllisten beider ausgelieferten Arbeitsmappen sowie ihre Verwendung in den Konvertern. Die folgende Liste dokumentiert konkrete Befunde; sie ist keine vollständige fachliche Prüfung aller Eingabekombinationen.

## Person: Anschrift bereinigt

Die frühere Freitextspalte `Anschrift` ist entfernt. Anschriften werden ausschließlich in `Straße`, `Postleitzahl`, `Ort`, `Bundesland` und `Land` erfasst. Vorhandene Beispieladressen wurden in die strukturierten Felder übertragen. Alle anderen Person-Spalten rechts von der früheren Spalte D rücken um eine Spalte nach links; die übrigen Blätter bleiben unverändert. Nach der anschließenden Entfernung von Krankenkasse steht die Ausfüllhilfe in R.

Der Konverter übernimmt vorhandene Adressbestandteile auch ohne Land. Ein leeres Land bedeutet unbekannt, nicht automatisch DE. Eine vollständig leere Adresse bekommt Data Absent Reason unknown. Bei Land DE werden ausgeschriebene Bundesländer im FHIR in ISO-Codes umgewandelt. Unbekannte Länder oder Adressbestandteile werden nicht erfunden. Alte Mappen mit `Anschrift` passen nicht mehr zum aktuellen Schema und müssen auf die neuen Spalten migriert werden.

## Person: Krankenkassenfeld entfernt

Das frühere Feld `Krankenkasse` ist entfernt. Es erzeugte fälschlich einen Eintrag
in `Patient.generalPractitioner`. Es gibt dafür keinen Ersatz als Hausarztangabe
und noch keine neue Versicherungsressource. Einwilligungsspalten und strukturierte
Anschrift bleiben erhalten; sie rücken um eine Spalte nach links.

## Medikation: eindeutiger Eingabevertrag

Die Medikation enthält jetzt 20 Datenspalten statt 24. Präparatbezeichnung,
Präparatcode und Präparatcodesystem ersetzen die konkurrierenden Produktfelder.
ATC-Code mit ausdrücklich eingegebener ATC-Version bleibt zusätzlich erhalten.
Wirkstoffcode und Wirkstoffcodesystem sind unabhängig davon. Mehrere Wirkstoffe
werden mit Semikolon getrennt angegeben; alle verwenden das ausgewählte Codesystem.
UNII ist neben ASK, SNOMED CT und RxNorm unterstützt. Pro Wirkstoff entsteht ein
eigenes FHIR-Ingredient; mehrere Wirkstoffe sind keine alternativen Codings. Ein unbekannter
Wirkstoff benötigt ausdrücklich `!dar:unknown` mit einem Codesystem. Die
Darreichungsform wird als Text übernommen. Aus der Einzeldosis entsteht keine
Produkt- oder Wirkstoffstärke.

| Medikationstyp | Dokumentationszeitpunkt | Beginn / Ende |
|---|---|---|
| Verordnung | authoredOn, optional | leer lassen |
| Verabreichung | leer lassen | effectiveDateTime oder effectivePeriod |
| Medikationsaussage | dateAsserted, optional | effectiveDateTime oder effectivePeriod |

Beginn ist bei Verabreichung und Medikationsaussage erforderlich; unbekannte
Pflichtzeitpunkte werden mit `!dar:unknown` ausdrücklich bezeichnet. Unpassende
Zeitfelder, ungültige Medikationstypen und nicht passende Statuswerte werden als
Eingabefehler gemeldet. Absicht gilt nur für Verordnungen. Leerer Status bedeutet
weiterhin active bei Verordnung/Medikationsaussage und completed bei Verabreichung;
leere Verordnungsabsicht bedeutet order. Diese Vorgaben stehen in der Ausfüllhilfe.

Die Statusauswahl verweist abhängig vom Medikationstyp auf die sichtbaren Listen
im Codes-Blatt. Die gemeinsame Java-Eingabeprüfung schützt auch direkt eingelesene
CSV-Dateien. Einzeldosis, Dosiereinheit und ganzzahlige Dosen pro Tag ergeben ohne
Freitext eine strukturierte Dosierung. Teilangaben, nicht ganzzahlige Häufigkeiten
und mit Freitext kombinierte Angaben bleiben vollständig im Dosierungstext.
Verabreichungen erhalten die Tageshäufigkeit als Text, weil ihr Dosierungstyp kein
entsprechendes Timing-Feld besitzt. Eine vorhandene Einzeldosis bleibt dabei
strukturiert, auch ohne Einheit (fehlende Einheit als DAR unknown). Reiner
Dosierungstext ohne Dosis wird wegen FHIR-Regel mad-1 als Eingabefehler erkannt;
bei unbekannter Dosis ist ausdrücklich !dar:unknown einzutragen.

Die vorhandenen Vorlagen wurden mit LibreOffice/UNO aus ihren Originalen geändert.
Alte, zuvor ignorierte Therapiestart-/Therapieende-Werte bleiben als ausdrücklich
benannte alte Therapieeinträge im Dosierungstext erhalten. Die ATC-Version wurde
aus der vorherigen tatsächlichen FHIR-Ausgabe übernommen (Vorlage 2019, Demo 2025),
nicht neu aus dem Ereignisdatum abgeleitet. Das ist eine Migration des bisherigen
Verhaltens, keine fachliche Bestätigung historischer Beispielpräparate.

Medication-IDs berücksichtigen alle Produktangaben einschließlich ATC-Version,
Darreichungsform und Wirkstoffsystem. Dadurch können sich IDs gegenüber alten
Ausgaben ändern; alle zugehörigen Referenzen werden gemeinsam erzeugt.
Alte Arbeitsmappen müssen auf das neue Schema angepasst werden. Das einmalige
historische Erweiterungsskript ist entfernt; Ausgangspunkt ist die aktuelle Vorlage.

## Weitere bereinigte Vorlagenkonflikte

- Labor: eigene Werttypenauswahl ohne Ja/Nein. Solche Eingaben werden vorab und im Konverter abgewiesen. Entsprechende Synthea-Quellen werden ausdrücklich im Verlustbericht ausgewiesen; es werden keine Ersatzcodes erfunden.
- Prozedur: Durchführungsbeginn benennt das tatsächliche FHIR-Zielfeld performed[x].
- Klinische Dokumentation: Untersuchungscode bezeichnet sowohl LOINC- als auch SNOMED-Codes.
- Labor behält die einheitliche Codesystemspalte mit festem Auswahlwert LOINC.

## Bewusste Alternativen, keine pauschal zu löschenden Doppelungen

- `DocumentReference`: Dokumenttext und Dateipfad/Embed sind unterschiedliche Quellen. Dokumenttext hat Vorrang; Dateipfad/Embed werden dann nicht für den Inhalt verwendet. Das ist dokumentiert, könnte aber eine Prüfung auf gleichzeitig ausgefüllte Quellen vertragen.
- Diagnose: Code und Zusatzcode ermöglichen mehrere Codierungen desselben Befunds. SNOMED und ICD-10-GM sind nicht allein wegen zweier Codefelder redundant.
- Diagnose: Dokumentationszeitpunkt, Beginn und Ende entsprechen recordedDate, onset und abatement. Sie beschreiben unterschiedliche Sachverhalte.
- Messwerte: Einheit ist der Lesetext, Einheitencode der maschinenlesbare UCUM-Code. Ebenso sind Untersuchungscode und Wertcode unterschiedliche Konzepte.
- Fall: Kontakte entstehen nur anhand der fachlichen Angaben; technische Identifikatoren und Elternbezüge werden automatisch erzeugt. Sekundärkontakte laufen parallel zum primären Aufenthalt.

Als Nächstes werden die versteckten bzw. wirkungslosen Medikationsfelder und die übrigen bestätigten Vorlagenkonflikte bereinigt. Die Krankenkassenabbildung ist bereits entfernt.

## Prüfung der Anschriftbereinigung

47 Java-Tests und 39 Python-Tests bestanden. Drei Synthea-Beispiele wurden mit der bereinigten Vorlage neu erzeugt und vollständig zurückkonvertiert. Die 20.844 FHIR-Ressourcen sind inhaltlich identisch zum zuvor validierten Stand. Deshalb wurden die unveränderten FHIR-Bundles nicht nochmals einer vollständigen Terminologieprüfung unterzogen. Vorlagenstile, Zeilenhöhen, verschobene Spaltenbreiten und Auswahllisten wurden gegen die vorherigen Dateien geprüft; die neuen Adressbereiche beider Mappen wurden visuell kontrolliert.
