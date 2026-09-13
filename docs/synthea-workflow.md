# Vollständiger Synthea-Workflow

Der Workflow verarbeitet vorhandene Synthea-R4-Patientenbundles zu deutschen
Excel-Dateien, CSV, FHIR und Prüfberichten. Er erzeugt keine neue Synthea-Population.
Pro JSON-Datei wird ein Patient erwartet; Dateien ohne Patient werden mit Grund
in der Zusammenfassung aufgeführt. Die bisher unterstützten Ressourcen und
Eigenschaften bleiben maßgeblich, siehe [Importumfang](synthea-clinical-import.md).

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

Das Ausgabeverzeichnis darf noch nicht existieren. Die Pfade zur Vorlage und zum
JAR werden relativ zum Skript bestimmt, deshalb funktioniert der Aufruf auch aus
einem anderen Arbeitsverzeichnis. Der Workflow verwendet acht GB als maximale
Java-Heapgröße für die vollständige Validierung; große Lebensverläufe können
mehrere Minuten benötigen. LibreOffice benötigt zusätzlich Speicher. Für die Ausgabe von Zeitpunkten benutzt
die gesamte Pipeline (Python, LibreOffice und Java) einheitlich `Europe/Berlin`. Damit hängt die
Darstellung der Kontaktzeiten nicht von der Zeitzone des Hosts ab; Zeitpunkte
mit explizitem Offset bezeichnen weiterhin denselben Zeitpunkt.

## Container

Das bestehende `docker/Dockerfile` dient weiter der Excel→FHIR-Konvertierung.
`docker/synthea.Dockerfile` enthält zusätzlich den Synthea-Import, Python,
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
Terminologietest. Das gesamte Image wird zusätzlich mit Trivy geprüft.
