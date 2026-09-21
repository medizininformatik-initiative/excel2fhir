# Vorhandene Synthea-Daten importieren und lokal arbeiten

Der Import vorhandener Synthea-R4-Patientenbundles erzeugt deutsche
Excel-Dateien, CSV, FHIR und Prüfberichte. Die Erzeugung einer neuen Population
mit Synthea gelingt am einfachsten über den [Docker-Komplettlauf](synthea-workflow.md).
Docker ist auch für den Import der empfohlene Einstieg; die lokale Installation
ist weiter unten als Alternative beschrieben. Alle Befehle werden im Projektverzeichnis ausgeführt.
Pro JSON-Datei wird ein Patient erwartet; Dateien ohne Patient werden mit Grund
in der Zusammenfassung aufgeführt. Die unterstützten Ressourcen und Eigenschaften
beschreibt der [Importumfang](synthea-clinical-import.md).

## Mit Docker starten (empfohlen)

`docker/Dockerfile` baut das Image für die Excel-/CSV-Konvertierung.
`docker/synthea.Dockerfile` enthält den Synthea-Import, Python,
LibreOffice und die freien projektinternen Mappings. Es benötigt keine lokal
installierte Office- oder Java-Umgebung und keine MMI-Daten.

```sh
docker build -f docker/synthea.Dockerfile -t excel2fhir-synthea .
mkdir -p /absoluter/pfad/ergebnisse
docker run --rm \
  -v /absoluter/pfad/synthea/fhir:/input:ro \
  -v /absoluter/pfad/ergebnisse:/output \
  excel2fhir-synthea -i /input -o /output
```

Für den Einstieg mindestens 8 GB Docker-Arbeitsspeicher bereitstellen; große
Patientenverläufe benötigen mehr. Die Beispielpfade durch eigene absolute Pfade ersetzen.
Die Quelle wird schreibgeschützt eingebunden. Unter Linux können die erzeugten
Dateien dem Containerbenutzer root gehören; bei Bedarf den Container mit der
üblichen Docker-Option `--user` unter einer schreibberechtigten UID ausführen.
Der Container enthält ausschließlich die öffentliche Produktschicht. Ein echter
lokaler MMI-Katalog ist kein Bestandteil dieses Auslieferungswegs.

## Ergebnisse und Status

`details/reports/environment.json` enthält Werkzeugversionen und SHA-256-Prüfsummen des JARs,
der Vorlage, der Skripte und Mappingdateien. Jeder Fall enthält zusätzlich die
Quellprüfsumme, Verlustbericht, CSV, Importberichte und bei aktivierter FHIR-Prüfung Validierungsberichte
und `conversion.log` unter `details/cases/`. Bearbeitbare Arbeitsmappen liegen
unter `excel/`, die gewählten FHIR-Formate unter
`fhir/`, nach Eingabedateien und Varianten geordnet. `details/reports/summary.json` wird nach jedem Fall aktualisiert und enthält
auch fehlgeschlagene Fälle. Ein Fehler in einer Quelldatei verhindert nicht die
Bearbeitung der übrigen Dateien.

| Gesamtstatus | Exitcode | Bedeutung |
| --- | --- | --- |
| `NOT_VALIDATED` | 0 | Standardlauf: Import und Rückvergleich bestanden; FHIR-Validierung deaktiviert. |
| `COMPLETE` | 0 | Import, Rückvergleich und angeforderte FHIR-Validierung bestanden; Warnungen/gezielte Ausnahmen können vorhanden sein. |
| `NOT_CHECKED` | 1 | Import und Rückvergleich bestanden, aber FHIR-Prüfungen waren nicht vollständig ausführbar. |
| `FAILED` | 1 | Mindestens ein Fall oder Rückvergleich scheiterte, oder es gab keine Patientenbundles. |
| `RUNNING` | noch offen | Lauf läuft oder wurde vor dem Abschluss unterbrochen. |

Die FHIR-Profil- und Terminologieprüfung aktivieren Sie beim Import mit `-v`:

```sh
docker run --rm \
  -v /absoluter/pfad/synthea/fhir:/input:ro \
  -v /absoluter/pfad/ergebnisse:/output \
  excel2fhir-synthea -i /input -o /output -v
```

Lokal lautet der Aufruf `python3 scripts/run_synthea_cases.py -i /pfad/synthea/fhir -v`.
Die Validierungsauswahl gilt für den jeweiligen Aufruf. Die Konvertierungsoptionen
bestimmen die erzeugten Daten.
Die [Importbilanz](import-report.md) und die
[Validator-Dokumentation](fhir-validation.md) erklären die Detailberichte.

Reproduzierbarkeit bedeutet hier nachvollziehbare Eingaben, deterministische
Mappings und dokumentierte Werkzeugstände. Ein späterer Docker-Neubau kann neue
Debian-Paketstände enthalten. Für identische Wiederholungen dasselbe gebaute
Image aufbewahren und dessen Image-ID zusammen mit den Ergebnissen sichern.
Die fachliche Plausibilität sowie die menschliche Excel-Oberfläche bleiben eigene
Reviewaufgaben. Fehlende Terminologien werden nicht durch den Container ersetzt.

Die CI führt zwei kleine, referenzerhaltend aus tatsächlichen Synthea-Quellen
extrahierte Fälle durch dieses Container-Image. Sie prüft Import und Rückvergleich
mit ausdrücklich aktivierter FHIR-Validierung und bewahrt Excel-Dateien sowie
Berichte als Artefakte auf. `NOT_CHECKED` bleibt dabei im Bericht und im
Prozess-Exitcode sichtbar. Zusätzlich erzeugt die CI mit dem Compose-Einstieg
im Standardmodus eine neue Population, prüft `NOT_VALIDATED` und Exitcode 0
und führt sie offline durch den gesamten Ablauf. Beide Images werden
mit Trivy geprüft.

## Bearbeitete Excel-Datei erneut konvertieren

Die bearbeitete Datei direkt mit dem Converter ausführen. Liegt sie beispielsweise
unter `input/MeinFall.xlsx`, genügt:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f input/MeinFall.xlsx
```

Die Eingabedatei bleibt erhalten. Ergebnisse entstehen in einem neuen
`outputGlobal/run-…-excel-to-fhir/`.
Weitere Pfade und Optionen stehen unter [Excel und CSV konvertieren](converter-usage.md).

Alternativ mit einem bereits lokal gebauten JAR:

```sh
java -XX:MaxRAMPercentage=50 -Duser.timezone=Europe/Berlin -jar target/excel2fhir.jar \
  -f input/MeinFall.xlsx
```

CSV-Dateien konvertieren Sie über den
[CSV-Einstieg](converter-usage.md#csv-verwenden). Auch dort entsteht bei jedem
Start ein neuer Laufordner; vorherige Ergebnisse bleiben erhalten.


## Alternative ohne Docker

Voraussetzungen: Python 3.10 oder neuer, JDK 17 oder neuer, LibreOffice mit
Java/UNO-Unterstützung und Maven zum Bauen. Python benötigt für diesen Workflow
keine zusätzlichen Pakete. Unter Debian/Ubuntu heißen die wesentlichen Pakete
`python3`, `openjdk-17-jdk-headless`, `libreoffice-calc` und
`libreoffice-java-common`. Unter macOS wird LibreOffice unter
`/Applications/LibreOffice.app` erkannt.

```sh
mvn test package
python3 scripts/run_synthea_cases.py -i /pfad/synthea/fhir
```

Standardmäßig wird `input/` gelesen; `-f` wählt eine einzelne Quelldatei.
Jeder Aufruf legt einen Laufordner unter `outputGlobal/` an.
`-o /pfad/ergebnisse` wählt die Ausgabe-Wurzel.
Die [gemeinsamen Konverterparameter](converter-usage.md#parameter) gelten ebenso:
`--converter-options DATEI` wählt externe Optionssätze, `-r` die Formate,
`-p` die Patientenanzahl pro Bundle und `-v` die FHIR-Validierung.

```sh
python3 scripts/run_synthea_cases.py -i /pfad/synthea/fhir \
  --converter-options optionen/DIZ-A.config -r NDJSON -p 1
```

Die erzeugten Excel-Dateien enthalten die gemeinsamen Converter-Defaults.
Die Optionsdateien bestimmen die Varianten des Aufrufs. Jeder Konverterlauf
speichert seine wirksamen Optionen unter `details/options/`; beim Synthea-Import
liegen diese Konverterläufe unter `details/cases/`.
Java kann bis zur Hälfte des verfügbaren Speichers als Heap nutzen.
Große Lebensverläufe und die optionale FHIR-Validierung benötigen mehr Speicher.
Für die Ausgabe von Zeitpunkten benutzt
die gesamte Pipeline (Python, LibreOffice und Java) einheitlich `Europe/Berlin`. Damit hängt die
Darstellung der Kontaktzeiten nicht von der Zeitzone des Hosts ab; Zeitpunkte
mit explizitem Offset bezeichnen weiterhin denselben Zeitpunkt.

## Auch Synthea lokal bauen und starten

Der Generator wird derzeit aus dem [Fork astruebi/synthea](https://github.com/astruebi/synthea)
gebaut. Der feste Commit in `scripts/synthea-version.txt` enthält die
Sicherheitsupdates aus [Upstream-PR #1712](https://github.com/synthetichealth/synthea/pull/1712).
Nach dessen Übernahme können Repository und Commit gemeinsam auf den geprüften
Upstream-Stand umgestellt werden; bis dahin bleibt der Fork fest gepinnt.

Im Projektverzeichnis nach `mvn test package`:

```sh
git clone git@github.com:astruebi/synthea.git ../synthea-kds
git -C ../synthea-kds checkout --detach "$(cat scripts/synthea-version.txt)"
(cd ../synthea-kds && ./gradlew --no-daemon shadowJar)
cp ../synthea-kds/build/libs/synthea-with-dependencies.jar target/synthea.jar
git -C ../synthea-kds rev-parse HEAD > target/synthea-revision.txt
python3 scripts/run_synthea_workflow.py -- \
  -p 1 -a 30-80 -s 20260912 -cs 20260912 -r 20260912 -e 20260912
```

Der zusätzliche Checkout ist nur für den lokalen Aufbau erforderlich. Das
fertige Docker-Image enthält bereits den gepinnten Generator und dessen
Lizenzhinweise. Neue Synthea-Versionen nicht ohne Mappingreview unterschieben.
Die Zeitstempel in den Ausgabeordnernamen unterscheiden Läufe; Seeds und
Simulationsdaten bestimmen die reproduzierbaren Quelldaten.

## Welche Skripte muss ich selbst starten?

Nur `run_synthea_workflow.py` für den Komplettlauf oder `run_synthea_cases.py`
für vorhandene Quellen. Die übrigen Schritte laufen automatisch. Aufgaben,
Mappingdateien und technische Grenzen erklärt die [Architekturübersicht](architecture.md).
