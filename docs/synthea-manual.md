# Synthea-Workflow manuell ausführen

Der Import vorhandener Synthea-R4-Patientenbundles erzeugt deutsche
Excel-Dateien, CSV, FHIR und Prüfberichte. Die Erzeugung einer neuen Population
mit Synthea ist weiter unten beschrieben.
Pro JSON-Datei wird ein Patient erwartet; Dateien ohne Patient werden mit Grund
in der Zusammenfassung aufgeführt. Die unterstützten Ressourcen und Eigenschaften
beschreibt der [Importumfang](synthea-clinical-import.md).

## Lokal

Voraussetzungen: Python 3.10 oder neuer, JDK 17 oder neuer, LibreOffice mit
Java/UNO-Unterstützung und Maven zum Bauen. Python benötigt für diesen Workflow
keine zusätzlichen Pakete. Unter Debian/Ubuntu heißen die wesentlichen Pakete
`python3`, `openjdk-17-jdk-headless`, `libreoffice-calc` und
`libreoffice-java-common`. Unter macOS wird LibreOffice unter
`/Applications/LibreOffice.app` erkannt.

```sh
mvn test package
python3 scripts/run_synthea_cases.py /pfad/synthea/fhir /pfad/neue-ausgabe
```

Das Ausgabeverzeichnis muss neu sein oder darf nur `converter-options.config`
enthalten. Fehlt diese Datei, wird sie mit kommentierten Workflow-Defaults angelegt.
Vorhandene Angaben werden geprüft und in die erzeugten Excel-Dateien übernommen. Die Pfade zur Vorlage und zum
JAR werden relativ zum Skript bestimmt, deshalb funktioniert der Aufruf auch aus
einem anderen Arbeitsverzeichnis. Der Workflow erlaubt Java bis zur Hälfte des verfügbaren Arbeitsspeichers
als Heap für die vollständige Validierung. Im Container zählt der für Docker
bereitgestellte Speicher. Große Lebensverläufe können mehr als 8 GB Docker-RAM
und deutlich längere Laufzeiten erfordern. LibreOffice benötigt zusätzlich Speicher. Für die Ausgabe von Zeitpunkten benutzt
die gesamte Pipeline (Python, LibreOffice und Java) einheitlich `Europe/Berlin`. Damit hängt die
Darstellung der Kontaktzeiten nicht von der Zeitzone des Hosts ab; Zeitpunkte
mit explizitem Offset bezeichnen weiterhin denselben Zeitpunkt.

## Container

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
  excel2fhir-synthea /input /output/lauf-01
```

Docker muss ausreichend Arbeitsspeicher für Java und LibreOffice bereitstellen.
Die Quelle wird schreibgeschützt eingebunden. Unter Linux können die erzeugten
Dateien dem Containerbenutzer root gehören; bei Bedarf den Container mit der
üblichen Docker-Option `--user` unter einer schreibberechtigten UID ausführen.
Der Container enthält ausschließlich die öffentliche Produktschicht. Ein echter
lokaler MMI-Katalog ist kein Bestandteil dieses Auslieferungswegs.

## Ergebnisse und Status

`environment.json` enthält Werkzeugversionen und SHA-256-Prüfsummen des JARs,
der Vorlage, der Skripte und Mappingdateien. Jeder Fall enthält zusätzlich die
Quellprüfsumme, `Fall.xlsx`, `Fall.loss.json`, CSV, FHIR, Import-/Validierungsbericht
und `conversion.log`. `summary.json` wird nach jedem Fall aktualisiert und enthält
auch fehlgeschlagene Fälle. Ein Fehler in einer Quelldatei verhindert nicht die
Bearbeitung der übrigen Dateien.

| Gesamtstatus | Exitcode | Bedeutung |
| --- | --- | --- |
| `COMPLETE` | 0 | Import und Rückvergleich bestanden; Validierung ohne Fehler oder ungeprüfte Terminologien, Warnungen/gezielte Ausnahmen können vorhanden sein. |
| `NOT_CHECKED` | 1 | Import und Rückvergleich bestanden, aber FHIR-Prüfungen waren nicht vollständig ausführbar. |
| `FAILED` | 1 | Mindestens ein Fall oder Rückvergleich scheiterte, oder es gab keine Patientenbundles. |
| `RUNNING` | noch offen | Lauf läuft oder wurde vor dem Abschluss unterbrochen. |

Ein `NOT_CHECKED` wird ausdrücklich nicht als erfolgreicher Volltest umgedeutet.
Fehlende lokale Referenzziele sind für diesen geschlossenen Export ein Fehler.
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
und bewahrt Excel-Dateien sowie Berichte als Artefakte auf. Ein bekanntes
`NOT_CHECKED` darf diese technische Regression bestehen lassen, bleibt aber im
Bericht und im Prozess-Exitcode sichtbar. Das ist kein bestandener vollständiger
Terminologietest. Zusätzlich erzeugt die CI mit dem Compose-Einstieg eine neue Population und
führt sie ohne Netzwerkzugriff durch den gesamten Ablauf. Beide Images werden
mit Trivy geprüft.

## Auch Synthea lokal bauen und starten

Im Projektverzeichnis nach `mvn test package`:

```sh
git clone git@github.com:synthetichealth/synthea.git ../synthea-kds
git -C ../synthea-kds checkout --detach "$(cat scripts/synthea-version.txt)"
(cd ../synthea-kds && ./gradlew --no-daemon shadowJar)
cp ../synthea-kds/build/libs/synthea-with-dependencies.jar target/synthea.jar
git -C ../synthea-kds rev-parse HEAD > target/synthea-revision.txt
python3 scripts/run_synthea_workflow.py outputSynthea \
  -p 1 -a 30-80 -s 20260912 -cs 20260912 -r 20260912 -e 20260912
```

Der zusätzliche Checkout ist nur für den lokalen Aufbau erforderlich. Das
fertige Docker-Image enthält bereits den gepinnten Generator und dessen
Lizenzhinweise. Neue Synthea-Versionen nicht ohne Mappingreview unterschieben.
Die Zeitstempel in den Ausgabeordnernamen unterscheiden Läufe; Seeds und
Simulationsdaten bestimmen die reproduzierbaren Quelldaten.

## Bearbeitete Excel-Datei erneut konvertieren

Nicht erneut aus Synthea erzeugen, sondern die bearbeitete Datei direkt übergeben:

```sh
java -XX:MaxRAMPercentage=50 -Duser.timezone=Europe/Berlin -jar target/excel2fhir.jar -v \
  -f /pfad/Fall.xlsx -t /pfad/neue-csv-ausgabe -o /pfad/neue-fhir-ausgabe
```

Mit dem Importer-Image können Sie Excel-Dateien ohne lokale Java-Installation konvertieren:

```sh
docker build -f docker/synthea.Dockerfile -t excel2fhir-synthea .
docker run --rm --network none --entrypoint java \
  -v /absoluter/pfad/zur/excel-datei:/input:ro \
  -v /absoluter/pfad/zu/neuen-ergebnissen:/output \
  excel2fhir-synthea -XX:MaxRAMPercentage=50 -Duser.timezone=Europe/Berlin -jar /app/target/excel2fhir.jar -v \
  -f /input/Fall.xlsx -t /output/csv -o /output/fhir
```

CSV-Dateien konvertieren Sie über den
[CSV-Einstieg](converter-usage.md#vorhandene-csv-verwenden). Ausgabeordner bewusst neu wählen,
da die allgemeinen Konverter vorhandene Ausgabeordner leeren können.


## Welche Skripte muss ich selbst starten?

Nur `run_synthea_workflow.py` für den Komplettlauf oder `run_synthea_cases.py`
für vorhandene Quellen. Die übrigen Schritte laufen automatisch. Aufgaben,
Mappingdateien und technische Grenzen erklärt die [Architekturübersicht](architecture.md).
