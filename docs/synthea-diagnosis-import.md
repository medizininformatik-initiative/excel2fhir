# Synthea-Diagnosen über Excel importieren

Der erste Importumfang umfasst einen Patienten mit seinen Kontakten und Diagnosen.
Andere Ressourcen und nicht darstellbare Eigenschaften werden mit Quell-ID und
Feldpfad im Verlustbericht ausgewiesen. Das Ergebnis ist ein technischer Diagnosefall,
noch keine vollständig übernommene klinische Geschichte.

## Voraussetzungen und Aufruf

Python 3, ein JDK mit Java-Source-Launcher und LibreOffice müssen installiert sein.
Unter macOS wird `/Applications/LibreOffice.app` verwendet; unter Linux werden
`libreoffice`/`soffice` und UNO-JARs unter `/usr/share/java` erwartet. Der Linux-Pfad
ist vorbereitet, aber der Durchlauf wurde bisher nur unter macOS geprüft.

```sh
python3 scripts/synthea_to_excel.py /path/to/synthea-patient.json /path/to/case.xlsx
```

Der Importer kopiert die bestehende Standardvorlage und bearbeitet die Kopie mit
LibreOffice/UNO. Er startet dafür eine isolierte, nach dem Import wieder beendete
LibreOffice-Instanz mit temporärem Profil. Eine bestehende Office-Sitzung bleibt
unberührt. Vorhandene Ausgabedateien werden nicht überschrieben.

Ausgaben:

- `case.xlsx`: eigenständig bearbeitbare und konvertierbare Excel-Datei.
- `case.loss.json`: Quellhash, Zuordnung der Kontakte und Diagnosezeilen sowie
  ausgelassene Ressourcen/Eigenschaften und bekannte Generatorergänzungen.

Die Beispieldaten und Einwilligungswerte der Vorlage werden in der Kopie entfernt.
Es werden keine Einwilligungen erfunden. Die zentralen Auswahllisten und die
Vorlagenformatierung bleiben erhalten. Der Import setzt die bestehenden Optionen
für direkte Condition→Encounter-Referenzen; es werden keine neuen CLI-Schalter
oder Konvertierungsoptionen eingeführt.

Der vorbereitete Eingabebereich umfasst höchstens 1.030 Zeilen je Tabelle. Größere
Fälle werden vor der Dateierstellung abgelehnt. Erwartet wird genau ein Patient
pro Synthea-R4-Bundle. Defekte Patient-/Fallreferenzen, nicht unterstützte
System-/Versionskombinationen und fehlende Diagnosen führen ebenfalls zum Abbruch.
Es wird keine leere administrative Hülle als erfolgreicher Fall ausgegeben.

## Rückkonvertierung

```sh
mvn -q compile exec:java \
  -Dexec.mainClass=de.uni_leipzig.imise.Excel2FhirMain \
  -Dexec.args="-f /path/to/case.xlsx -o /path/to/fhir -t /path/to/csv"
```

Danach werden Synthea und der Importer nicht mehr benötigt. Fachliche Änderungen
können direkt in Excel vorgenommen werden. Eine automatische Terminologiezuordnung
findet nicht statt. Ein vorhandenes zusätzliches ICD-10-GM-Coding kann übernommen
werden; seine Version muss ausdrücklich angegeben sein.

Synthea-Notfallkontakte (`EMER`) werden nach MII KDS Basis 2026.0.1 als `AMB`
mit Aufnahmegrund-Extension, Unterelement `VierteStelle`, Code `7` übernommen.
Beide Angaben stehen ausdrücklich in Excel. Der Bericht enthält die ursprüngliche
Klasse und die Überleitung pro Quellkontakt unter `encounterMappings`.
Stationäre Kontakte bleiben `IMP`; eine Aufnahme oder Verknüpfung mit einem
anderen Kontakt wird nicht erfunden. Priorität und Aufnahmeanlass werden nicht
allein aus `EMER` ergänzt. Der Rückvergleich prüft die Notfall-Extension ebenfalls.

Die Ausfüllhilfen bleiben auch in der importierten Kopie erhalten. Die Auswahl
für Aufnahmegrund und DAR verweist auf Codes!AA30:AA50; Klassen auf Codes!B4:B13.
Unbekannte Kontaktklassen führen zum Abbruch.

Patientenadressen werden derzeit ausgelassen, weil der bestehende Patientenkonverter
das Land fest auf DE setzt. Der Verlustbericht nennt dies und die dort anschließend
automatisch erzeugten Fehlwertangaben. Geburtsdatum und Kontaktdaten verwenden die
bestehende Datumsverarbeitung; Kontaktzeitpunkte werden dafür in die lokale Zeitzone
umgerechnet. Diagnosezeitangaben behalten FHIR-Präzision und Offset.

## Prüfung

```sh
python3 -m unittest discover -s scripts/tests -v
mvn test
python3 scripts/check_synthea_roundtrip.py \
  /path/to/synthea-patient.json /path/to/generated-bundle.json /path/to/case.loss.json
```

Der Rückvergleich prüft Anzahl, Codes/Versionen, Diagnosezeiten, Statuswerte,
Kontaktklassen, Kontaktzeitpunkte und auflösbare Patient-/Fallbezüge. Die Prüfung
bezieht sich auf den vereinbarten Importumfang, nicht auf alle Synthea-Eigenschaften.

Erprobter Ausgangsstand: Synthea `d9d07a6eef91ee5144293b42ab64224d84d124f8`,
Seed/Clinician-Seed 20260911, Referenz-/Enddatum 20260911, vollständige Historie.
Ein tatsächlich generierter Patient wurde über Excel konvertiert: 76 Conditions,
96 Encounters, ein Patient; die geprüften Werte und Referenzen stimmen überein.
Alle vier Notfallkontakte enthalten nach dem Rückweg die Aufnahmegrund-Kennzeichnung.

Technischer Informationserhalt ist kein Nachweis fachlicher Kodiergültigkeit.
Synthea kann z.B. auch einen SNOMED-Situationscode als Condition liefern. Die
optionale FHIR-Validierung (`-v`) ist separat zu betrachten; der bestehende Validator
filtert einige Terminologiemeldungen. Die normalen Konvertertests ersetzen keine
vollständige Prüfung gegen eine konkrete SNOMED-Ausgabe.

### Profilprüfung

Die Notfallabbildung `AMB` plus Aufnahmegrund `7` wurde gegen das aktuelle KDS-
Profil separat geprüft. Ebenso wurden DAR für `recordedDate` und `onsetDateTime`
positiv sowie fehlendes `recordedDate` und widersprüchliche Status-/Abatement-
Kombinationen negativ geprüft. `recordedDate` hat Mindestkardinalität 1, Onset und
Abatement sind optional. Fehlende Pflichtangaben werden nicht automatisch ersetzt.

Die vollständige Terminologieprüfung der Synthea-SNOMED-Codings benötigt einen
passenden Terminologiebestand. Der bestehende Generator lässt bei `-v` abgelehnte
Ressourcen weg. Eine solche Ausgabe darf nicht als vollständig übernommener
Diagnosefall gelten; der Rückvergleich erkennt die Verluste. Für vollständige
Profilvalidierung mit dem aktuellen Paketbestand wurde ein Java-Heap von 4 GB geprüft.

Beim geprüften Gesamtdurchlauf fehlte die vom Diagnose-ValueSet angeforderte
SNOMED-Ausgabe `http://snomed.info/sct/900000000000207008/version/20250701`.
Die 152 Fehlermeldungen betrafen ausschließlich diese Terminologieprüfung;
die Ausgabe mit `-v` enthielt deshalb nur den Patienten und 96 Kontakte, keine
der 76 Diagnosen. Der Rückvergleich wies diese unvollständige Ausgabe wie erwartet
zurück. Das belegt keine ungültigen SNOMED-Codes, sondern eine fehlende
Validierungsgrundlage. Der normale Rückweg ohne `-v` erhält alle 76 Diagnosen.
