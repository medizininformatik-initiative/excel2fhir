# excel2fhir

Mit `excel2fhir` erzeugen Sie synthetische FHIR-R4-Testdaten für den deutschen
MII-Kerndatensatz (KDS). Sie können Patienten mit Synthea erzeugen, die
Excel-Vorlage ausfüllen oder CSV-Dateien konvertieren.

| Ausgangspunkt | Anleitung |
| --- | --- |
| Neue Patienten mit Synthea erzeugen | [Synthea → Excel → FHIR](docs/synthea-workflow.md) |
| Viele stationäre Fälle im Zeitraum 2020–2026 | [Krankenhausbeispiel](examples/synthea-hospital/README.md) |
| Excel-Vorlage ausfüllen oder vorhandene Excel-/CSV-Daten konvertieren | [Excel und CSV verwenden](docs/converter-usage.md) |
| Vorhandene Synthea-Bundles importieren oder ohne Docker arbeiten | [Import und lokale Alternative](docs/synthea-manual.md) |

## Mit Docker KDS-FHIR aus Synthea erzeugen

Voraussetzung: Docker mit Compose, mindestens 8 GB für Docker und Internet für
den ersten Build. Große Patientenverläufe benötigen mehr Speicher. Nach dem
Checkout im Projektverzeichnis starten:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

Java, Python und LibreOffice sind im Image enthalten. Der fertige Lauf arbeitet
ohne Netzwerk und benötigt keine externen Medikamentenkataloge.

- **FHIR-Dateien:** `outputGlobal/run-…-synthea/fhir/`
- **Excel-Dateien:** `outputGlobal/run-…-synthea/excel/Fall-<Patient-ID>.xlsx`
- **Einstellungen:** Synthea-Argumente in `compose.synthea.yml`, Converter-Optionen
  in `outputGlobal/converter-options.config` (wird beim ersten Start angelegt).

Jeder Lauf bekommt einen eigenen Ordner. Die Excel-Dateien können Sie ansehen,
bearbeiten und anschließend [erneut konvertieren](docs/synthea-workflow.md#excel-bearbeiten).
Standardmäßig entsteht die Lebensgeschichte eines erwachsenen Patienten;
Synthea kann zusätzlich verstorbene Patienten ausgeben.

## Ergebnisse beurteilen

Der Workflow prüft den Import und vergleicht die übernommenen Inhalte mit Synthea.
Ein erfolgreicher Standardlauf erhält **`NOT_VALIDATED`** und Exitcode 0:
Die FHIR-Profil- und Terminologieprüfung ist optional und wird mit `-v` aktiviert
([Aufruf mit Validierung](docs/synthea-workflow.md#fhir-validierung)). So lassen sich
auch bewusst unvollständige oder profilwidrige FHIR-Testdaten erzeugen.
**`NOT_CHECKED`** bezeichnet Lücken einer angeforderten Terminologieprüfung
(Exitcode 1), **`FAILED`** einen fehlgeschlagenen Lauf. Der kurze Status steht in
`status.txt`; Einzelheiten unter `details/reports/`.

Die Daten enthalten ausdrücklich synthetische deutsche Ergänzungen und
näherungsweise Codezuordnungen. Nicht jede Synthea-Eigenschaft wird übernommen;
Auslassungen werden berichtet. Vollständige KDS-/Terminologiekonformität wird
nicht pauschal zugesichert. [Importumfang und Grenzen](docs/synthea-clinical-import.md).

Im eingebundenen Synthea-Generator bestehen bekannte Sicherheitsbefunde in
Abhängigkeiten. Ihre Bereinigung wird in [Ticket #55](https://github.com/medizininformatik-initiative/excel2fhir/issues/55)
bearbeitet; der vollständige Workflow hat noch keine abgeschlossene Sicherheitsfreigabe.
Das Image für die Excel-/CSV-Konvertierung enthält nur den Converter und seine
Abhängigkeiten.

## KDS-FHIR aus Excel oder CSV erzeugen

Das Repository enthält die [Vorlage](FHIR_Testdatengenerator_Vorlage.xlsx) und
eine [ausgefüllte Demo](FHIR_Testdatengenerator_Interpolar_Demo.xlsx). Eine Datei
kann mehrere Patienten enthalten. Mit Docker:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f FHIR_Testdatengenerator_Interpolar_Demo.xlsx
```

FHIR liegt unter `outputGlobal/run-…-excel-to-fhir/fhir/`, Zwischen-CSV unter
`details/csv/` desselben Laufs. JSON und NDJSON werden gemeinsam erzeugt.
Jeder Start legt einen neuen Lauf an; ohne Eingabeparameter wird `input/` gelesen. [Eigene Dateien, Optionen und CSV-Einstieg](docs/converter-usage.md).

## Entwicklung und fachliche Details

Für die Java-Entwicklung: JDK 17 und Maven 3.x. Der lokale Synthea-Import benötigt
zusätzlich Python und LibreOffice; die [manuelle Anleitung](docs/synthea-manual.md)
erklärt den Aufbau.

```sh
mvn test package
python3 -m unittest discover -s scripts/tests -v
```

- [Architektur, Skripte und Mappingdateien](docs/architecture.md)
- [Aufenthalte und OP-/Konsilkontakte](docs/synthea-movements.md)
- [Kontakt-Eingabeprüfungen](docs/contact-input-checks.md)
- [Importbilanz](docs/import-report.md) und [FHIR-Validierung](docs/fhir-validation.md)
- [Lizenz](LICENSE)
