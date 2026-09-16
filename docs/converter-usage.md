# Eigene Excel- und CSV-Dateien konvertieren

Die [Vorlage](../FHIR_Testdatengenerator_Vorlage.xlsx) und die
[Demo](../FHIR_Testdatengenerator_Interpolar_Demo.xlsx) enthalten die vorgesehenen
Spalten, Auswahllisten und Ausfüllhilfen. Blattnamen und Spaltenüberschriften
beibehalten. Eine Arbeitsmappe darf mehrere Patienten enthalten. Die
**Patient-ID** verbindet die Blätter; **Fall-Nr** ordnet klinische Angaben einem
Kontakt zu. [Aufenthalte und zusätzliche Kontakte eingeben](synthea-movements.md).

## Mit Docker

Die eigene Arbeitsmappe im Projektverzeichnis ablegen, hier `MeineDaten.xlsx`:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f /app/input/MeineDaten.xlsx
```

Der Projektordner ist im Container schreibgeschützt unter `/app/input` sichtbar.
Die Ausgabe steht auf dem Host unter `outputGlobal/`, die Zwischen-CSV unter
`outputLocal/`. Ohne `-f` verwendet der Converter die Standardvorlage.

**Die allgemeinen Converter können ihre Ausgabe- und Zwischenordner leeren.**
Dort keine Eingabedateien oder andere aufzubewahrende Dateien ablegen. Vor einem
weiteren Lauf Ergebnisse sichern oder eigene Ausgabeordner wählen:

```sh
docker compose -f docker/docker-compose.yml run --rm excel2fhir \
  -f /app/input/MeineDaten.xlsx \
  -o /app/outputGlobal/lauf-02 -t /app/outputLocal/lauf-02
```

Für ein JSON-Bundle je Patient zusätzlich `-p 1` angeben. `-v` schaltet die
FHIR-Validierung ein; `-r JSON,NDJSON` erzeugt beide Ausgabeformate.

## Mit Java

Voraussetzung: JDK 17 und Maven 3.x zum Bauen. Die Excel-Konvertierung selbst
benötigt weder Python noch LibreOffice.

```sh
mvn test package
java -jar target/excel2fhir.jar \
  -f MeineDaten.xlsx -o /pfad/neue-fhir-ausgabe -t /pfad/neue-csv-ausgabe
```

| Option | Zweck |
| --- | --- |
| `-f DATEI` | Eine Excel-Datei konvertieren; hat Vorrang vor `-i`. |
| `-i ORDNER` | Excel-Dateien eines Verzeichnisses konvertieren. |
| `-o ORDNER` | FHIR-Ausgabeordner. |
| `-t ORDNER` | Zwischen-CSV aus Excel. |
| `-p ANZAHL` | Maximale Patientenzahl je Bundle; ohne Angabe alle. |
| `-r FORMATE` | `JSON` (Default), `XML`, `NDJSON`, `JSONGZIP`, `JSONBZ2`; mehrere mit Komma. |
| `-v` | FHIR prüfen und Validierungsberichte schreiben. |
| `-vll STUFE` | Ausführlichkeit des Validierungslogs; der vollständige Bericht bleibt erhalten. |
| `--help` | Alle CLI-Optionen anzeigen. |

Ohne explizite Ausgabeordner entstehen beim Aufruf mit einer Datei `outputGlobal/`
und `outputLocal/` neben der Arbeitsmappe.

## Konvertierungsoptionen

Die fachlichen Optionen stehen im Blatt **Konvertierungsoptionen**. Nur Spalte A
wird gelesen. `#` am Zeilenanfang kommentiert eine Angabe aus; dann gilt der
beschriebene Default. `false` schaltet eine boolesche Option ausdrücklich aus.

Die Optionen steuern insbesondere Referenzrichtungen, ID-Zähler, Präfix/Suffix,
Patientenkopien und die strenge Excel-Prüfung. `VALIDATE_STRICT=false` schaltet
weder sämtliche CSV-Eingabeprüfungen noch die separat mit `-v` aktivierte
FHIR-Validierung aus. Ungültige Optionswerte werden vor der Erzeugung gemeldet.

Die Datei `outputSynthea/converter-options.config` gilt für die automatische
Erzeugung aus Synthea. Beim direkten Konvertieren einer Excel-Datei zählt deren
Optionsblatt; die zentrale Textdatei wird dafür nicht zusätzlich eingelesen.

## Vorhandene CSV verwenden

CSV-Dateien müssen den Tabellen und Spalten der Vorlage entsprechen. Ein
funktionierendes Beispiel liefert der Excel-Export in `outputLocal/`. Die
Optionsdatei `…_Konvertierungsoptionen.csv` enthält Properties-Text aus Spalte A,
keine gewöhnliche CSV-Tabelle. Sie gehört zu den Eingabedateien.

CSV-Dateien konvertieren Sie mit folgendem Aufruf:

```sh
java -cp target/excel2fhir.jar de.uni_leipzig.life.csv2fhir.Main \
  -i /pfad/csv-eingaben -o Fall
```

Bei diesem **CSV-Aufruf ist `-o` der gemeinsame Dateipräfix**, kein Pfad.
`-o Fall` liest beispielsweise `Fall_Person.csv` und `Fall_Fall.csv`;
die FHIR-Dateien und Berichte entstehen im selben Verzeichnis. Vorhandene
gleichnamige Ergebnisse werden überschrieben. Für einen neuen Lauf eine Kopie
der CSV-Eingaben in einem neuen Ordner verwenden. `-v` kann ebenfalls ergänzt
werden. Die CSV-Eingabe benötigt weder Excel noch LibreOffice.

## Berichte und Fehler

Jede CSV→FHIR-Konvertierung schreibt eine `*.import.json`. Mit `-v` kommt eine
`*.validation.json` hinzu. Ein unvollständiger Import, Validierungsfehler oder
nicht ausführbare Terminologieprüfungen führen zu Exitcode 1.

Die FHIR-Validierung **erhält alle konvertierten Ressourcen**, auch bei Fehlern.
Eine vorhandene Bundle-Datei allein ist deshalb keine Erfolgsmeldung. Bekannte
Eingabefehler werden vor der FHIR-Erzeugung gesammelt. Die Details erklären
[Importbilanz](import-report.md), [Eingabeprüfungen](contact-input-checks.md) und
[FHIR-Validierung](fhir-validation.md).
