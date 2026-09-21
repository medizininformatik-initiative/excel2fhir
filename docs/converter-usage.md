# Excel und CSV konvertieren

Die [Vorlage](../FHIR_Testdatengenerator_Vorlage.xlsx) und die
[Demo](../FHIR_Testdatengenerator_Interpolar_Demo.xlsx) enthalten die vorgesehenen
Spalten und Auswahllisten. Blattnamen und Spaltenüberschriften beibehalten.
Die Patient-ID verbindet die Blätter; Fall-Nr ordnet Angaben einem Kontakt zu.
Eine Arbeitsmappe darf mehrere Patienten enthalten.

## Mit Docker starten (empfohlen)

Alle Befehle werden im Projektverzeichnis ausgeführt. Für die standardmäßige
FHIR-Prüfung mindestens 8 GB Docker-Arbeitsspeicher bereitstellen; große
Datensätze benötigen mehr. Das Image erlaubt Java die Hälfte davon als Heap. Ohne Eingabeparameter
liest der Converter Excel-Dateien aus `input/`. Alternativ eine Datei auswählen:

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
  fhir/                 JSON-Bundles und patients.ndjson
  status.txt            Kurzer Gesamtstatus
  details/
    csv/                Aus Excel extrahierte Eingaben
    reports/            Import- und Validierungsberichte
    logs/               Konverterprotokoll
    pending/            Bei Fehlern: unvollständige Ausgaben zur Diagnose
```

`Z` bezeichnet UTC; 20:30 UTC entspricht im deutschen Sommer 22:30 Uhr.
Bei gleichzeitigen Starts erhält ein weiterer Lauf eine zusätzliche Nummer.
Frühere Ergebnisse und Eingaben werden nicht gelöscht.

JSON und NDJSON entstehen standardmäßig zusammen. JSON gruppiert die Patienten
eines Datensatzes in einem Bundle; `-p 1` erzeugt einzelne Patienten-Bundles.
`patients.ndjson` enthält unabhängig davon **ein vollständiges Patienten-Bundle
pro Zeile**, über alle Eingaben gesammelt. Es ist kein nach Ressourcentypen
aufgeteilter FHIR-Bulk-Export. Die beiden Formate enthalten dieselben Patientendaten. Bei mehreren Eingaben
werden die JSON-Dateien je Eingabe in Unterordnern abgelegt, damit gleiche
Dateinamen sich nicht überschreiben. NDJSON bleibt eine gemeinsame Datei.

## Parameter

| Option | Bedeutung |
| --- | --- |
| `-f DATEI` | Eine Excel-Datei; nicht zusammen mit `-i`. |
| `-i ORDNER` | Eingabeordner, standardmäßig `input/`. |
| `-o ORDNER` | Wurzel für neue Laufordner, standardmäßig `outputGlobal/`. |
| `-t ORDNER` | Optionaler separater CSV-Wurzelordner für Excel; darin entsteht ebenfalls ein neuer Laufordner. Normalerweise unnötig. |
| `-p ANZAHL` | Maximale Patientenanzahl je JSON-Bundle; standardmäßig alle eines Datensatzes. |
| `-r FORMATE` | Standard `JSON,NDJSON`; explizit auch `XML`, `JSONGZIP`, `JSONBZ2`, `ZIPJSON`. |
| `-v` / `--no-validate-bundles` | FHIR-Prüfung explizit einschalten / ausschalten; standardmäßig eingeschaltet. |
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
`csv-to-fhir`. `-o` bezeichnet jetzt ebenfalls die Ausgabe-Wurzel.
Die gemeinsame Präfixangabe entfällt: `Fall_Person.csv` erkennt den Datensatz
`Fall_`; weitere Tabellen wie `Fall_Fall.csv` werden zugeordnet. Mehrere
Datensätze in einem Ordner werden gemeinsam verarbeitet. Mehrdeutige Varianten
wie `Fall_Person.csv` und `Fall-Person.csv` werden abgelehnt.

## Optionen und Fehler

Die fachlichen Optionen stehen im Excel-Blatt **Konvertierungsoptionen** bzw.
in der zugehörigen CSV-Datei. Beim direkten Excel-/CSV-Aufruf wird keine
Synthea-Optionsdatei zusätzlich eingelesen. `CHECK_INPUT_CONSISTENCY=true`
(Standard) prüft Excel-Eingabedaten vor der Konvertierung auf Konsistenz. Mit
`false` entfallen diese zusätzlichen Prüfungen; Tabellenstruktur und
Konvertierungsoptionen werden weiterhin geprüft. Die FHIR-Prüfung wird separat
über `--validate-bundles` / `-v` gesteuert.

Die Option hieß zuvor `VALIDATE_STRICT`. In eigenen Vorlagen und Optionsdateien
muss der Schlüssel durch `CHECK_INPUT_CONSISTENCY` ersetzt werden.

Ein unvollständiger Import bleibt unter `details/pending/`; der direkte
Excel-/CSV-Lauf veröffentlicht dann keine finalen Dateien. FHIR-Prüffehler
lassen die vollständig importierten Daten erhalten, führen aber zu `FAILED`
und Exitcode 1. `NOT_CHECKED` bedeutet, dass Teile der Terminologieprüfung nicht
ausführbar waren, ebenfalls mit Exitcode 1. Eine explizit abgewählte Prüfung
wird als `NOT_VALIDATED` ausgewiesen; ein vollständiger Import liefert dann 0.
Eine vorhandene Datei allein ist keine Erfolgsmeldung: `status.txt` beachten.

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
