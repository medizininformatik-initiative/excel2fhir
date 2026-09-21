# Excel und CSV konvertieren

Die [Vorlage](../FHIR_Testdatengenerator_Vorlage.xlsx) und die
[Demo](../FHIR_Testdatengenerator_Interpolar_Demo.xlsx) enthalten die vorgesehenen
Spalten und Auswahllisten. Blattnamen und Spaltenüberschriften beibehalten.
Die Patient-ID verbindet die Blätter; Fall-Nr ordnet Angaben einem Kontakt zu.
Eine Arbeitsmappe darf mehrere Patienten enthalten.

## Mit Docker starten (empfohlen)

Alle Befehle werden im Projektverzeichnis ausgeführt. Der Converter liest
standardmäßig Excel-Dateien aus `input/`. Alternativ eine Datei auswählen:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f FHIR_Testdatengenerator_Interpolar_Demo.xlsx
```

Der Projektordner ist im Container unter `/workspace` eingebunden und das
Arbeitsverzeichnis. Relative Pfade sind deshalb dieselben wie beim lokalen Aufruf.
Die Eingaben sind schreibgeschützt; `outputGlobal/` ist beschreibbar.

Für einen ganzen Ordner `-i /pfad/excel` verwenden. Ohne `-f` oder `-i` wird
`input/` im aktuellen Arbeitsverzeichnis gelesen. Fehlen passende Dateien,
endet der Aufruf mit einer Fehlermeldung. Die Vorlage wird nur mit einem
expliziten `-f FHIR_Testdatengenerator_Vorlage.xlsx` verwendet.

## Ergebnisse

Jeder Start erzeugt einen neuen Ordner, beispielsweise:

```text
outputGlobal/run-20260918-203000Z-excel-to-fhir/
  fhir/
    Konvertierungsoptionen/  JSON-Bundles und patients.ndjson
  status.txt            Kurzer Gesamtstatus
  details/
    csv/                Aus Excel extrahierte Eingaben
    reports/            Import- und Validierungsberichte je Variante
    options/            Wirksame Konvertierungsoptionen je Variante
    logs/               Konverterprotokoll
    pending/            Bei Fehlern: unvollständige Ausgaben zur Diagnose
```

`Z` bezeichnet UTC; 20:30 UTC entspricht im deutschen Sommer 22:30 Uhr.
Bei gleichzeitigen Starts erhält ein weiterer Lauf eine zusätzliche Nummer.
Ergebnisse vorheriger Läufe bleiben erhalten.

JSON und NDJSON entstehen standardmäßig zusammen. JSON gruppiert die Patienten
eines Datensatzes in einem Bundle; `-p 1` erzeugt einzelne Patienten-Bundles.
`patients.ndjson` enthält unabhängig davon **ein vollständiges Patienten-Bundle
pro Zeile** für den jeweiligen Datensatz und Optionssatz. Es ist kein nach Ressourcentypen
aufgeteilter FHIR-Bulk-Export. Die beiden Formate enthalten dieselben Patientendaten. Bei mehreren Eingaben
werden die JSON-Dateien je Eingabe in Unterordnern abgelegt, damit gleiche
Dateinamen sich nicht überschreiben. Jeder dieser Ordner enthält seine eigene NDJSON-Datei.

## Parameter

| Option | Bedeutung |
| --- | --- |
| `-f DATEI` | Eine Excel-Datei; nicht zusammen mit `-i`. |
| `-i ORDNER` | Eingabeordner, standardmäßig `input/`. |
| `-o ORDNER` | Wurzel für neue Laufordner, standardmäßig `outputGlobal/`. |
| `-t ORDNER` | Optionaler separater CSV-Wurzelordner für Excel; darin entsteht ebenfalls ein neuer Laufordner. Normalerweise unnötig. |
| `-p ANZAHL` | Maximale Patientenanzahl je JSON-Bundle; standardmäßig alle eines Datensatzes. |
| `-r FORMATE` | Standard `JSON,NDJSON`; explizit auch `XML`, `JSONGZIP`, `JSONBZ2`, `ZIPJSON`. |
| `-v` / `--validate-bundles` | Optionale FHIR-Profil- und Terminologieprüfung aktivieren; standardmäßig deaktiviert. |
| `--converter-options DATEI` | Externe Optionsdatei verwenden; für mehrere Varianten wiederholen. |
| `-vll STUFE` | Ausführlichkeit des Validierungslogs. |
| `--help` | Hilfe zum jeweiligen Einstieg. |

Die lokalen Defaults beziehen sich auf das **aktuelle Arbeitsverzeichnis**,
nicht den Speicherort der Eingabe oder des JARs. Ein eigenes Ausgabeziel lässt
sich mit `-o /pfad/ergebnisse` wählen. Im Container muss es zusätzlich beschreibbar
eingebunden sein; für die üblichen Starts genügt die Compose-Konvention.

## CSV verwenden

CSV-Dateien müssen den Tabellen und Spalten der Vorlage entsprechen. Beispiele
liefert `details/csv/` eines Excel-Laufs. Die Datei `…_Konvertierungsoptionen.csv`
enthält Properties-Text aus dem Optionsblatt, keine normale CSV-Tabelle.

```sh
docker compose -f docker/docker-compose.yml run --build --rm --entrypoint java excel2fhir \
  -cp /app/excel2fhir.jar de.uni_leipzig.life.csv2fhir.Main -i input
```

Die CSV-Eingaben dafür unter `input/` ablegen.

Auch hier sind `input/` und `outputGlobal/` die Defaults. Der Laufname endet auf
`csv-to-fhir`. `-o` bezeichnet die Ausgabe-Wurzel.
Der Converter erkennt Präfixe automatisch: `Fall_Person.csv` gehört zum Datensatz
`Fall_`; weitere Tabellen wie `Fall_Fall.csv` werden zugeordnet. Mehrere
Datensätze in einem Ordner werden gemeinsam verarbeitet. Mehrdeutige Varianten
wie `Fall_Person.csv` und `Fall-Person.csv` werden abgelehnt.

## Optionen und Fehler

Die Datenblätter beschreiben den Fall. Die Converter Options bestimmen seine
FHIR-Darstellung. Jedes Blatt, dessen Name **Konvertierungsoptionen** enthält,
bildet einen eigenen Optionssatz. Beispielsweise erzeugen
`Konvertierungsoptionen_A` und `Konvertierungsoptionen_B` zwei Varianten
derselben Fälle. Bei CSV stehen die Optionssätze in den zugehörigen Dateien,
etwa `Fall_Konvertierungsoptionen_A.csv`.

Externe Optionsdateien lassen sich für verschiedene Falldateien wiederverwenden:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f input/MeinFall.xlsx \
  --converter-options optionen/DIZ-A.config \
  --converter-options optionen/DIZ-B.config
```

Mit `--converter-options` bestimmen ausschließlich die angegebenen Dateien die
Optionssätze dieses Aufrufs. Jede Datei enthält Properties-Text, beispielsweise:

```properties
SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER = true
SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION = false
```

Für ausgelassene Werte gelten die gemeinsamen Converter-Defaults. Die Auswahl
funktioniert ebenso beim CSV-Einstieg. Ein Aufruf ohne Optionsblatt oder externe
Datei verwendet den Optionssatz `default`.

Jede Variante erhält unter `fhir/` einen eigenen Ordner. Dessen Name entspricht
dem Blattnamen oder dem Namen der externen Datei ohne Endung. Leerzeichen und
Sonderzeichen werden durch `_` ersetzt; die Namen innerhalb eines Datensatzes
müssen eindeutig sein. So können Varianten dieselben Patienten-IDs verwenden.
Bei mehreren Eingabedatensätzen werden zusätzliche Datensatzordner angelegt.
`details/options/` enthält für jede Variante sämtliche wirksamen Optionswerte,
einschließlich der Defaults. Die zugehörigen Import- und Validierungsberichte
stehen unter `details/reports/`.

`CHECK_INPUT_CONSISTENCY=true` (Standard) prüft
Excel-Eingabedaten vor der Konvertierung auf Konsistenz. Mit `false` beschränkt
sich diese Vorprüfung auf Tabellenstruktur und Konvertierungsoptionen.
Die optionale FHIR-Prüfung aktivieren Sie mit `--validate-bundles` / `-v`, zum Beispiel:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f input/MeinFall.xlsx -v
```

Für die FHIR-Prüfung mindestens 8 GB Docker-Arbeitsspeicher bereitstellen;
große Datensätze benötigen mehr. Java kann die Hälfte des verfügbaren Speichers
als Heap nutzen.

Ein unvollständiger Import bleibt unter `details/pending/`; der direkte
Excel-/CSV-Lauf veröffentlicht dann keine finalen Dateien. FHIR-Prüffehler
lassen die vollständig importierten Daten erhalten, führen aber zu `FAILED`
und Exitcode 1. `NOT_CHECKED` bedeutet, dass Teile der Terminologieprüfung nicht
ausführbar waren, ebenfalls mit Exitcode 1. Bei der standardmäßigen Konvertierung ist die FHIR-Prüfung deaktiviert:
Der Status lautet `NOT_VALIDATED`, ein vollständiger Import liefert Exitcode 0.
Den Gesamtstatus des Laufs zeigt `status.txt`.

Details: [Importbilanz](import-report.md), [Eingabeprüfungen](contact-input-checks.md),
[FHIR-Validierung](fhir-validation.md).

## Alternative ohne Docker

Lokal mit JDK 17 und Maven 3.x:

```sh
mvn test package
java -jar target/excel2fhir.jar -f MeineDaten.xlsx
```

CSV-Dateien mit dem lokal gebauten JAR konvertieren:

```sh
java -cp target/excel2fhir.jar de.uni_leipzig.life.csv2fhir.Main -i /pfad/csv
```
