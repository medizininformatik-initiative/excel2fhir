# excel2fhir

Mit `excel2fhir` erzeugen Sie FHIR-R4-Testdaten für den deutschen
MII-Kerndatensatz (KDS). Sie beschreiben Patienten und Fälle in einer
Excel-Vorlage und wählen über die **Converter Options** die gewünschte
FHIR-Darstellung. Derselbe Fall lässt sich damit in mehreren DIZ-Varianten erzeugen.

Technischer Kern ist die CSV-zu-FHIR-Konvertierung. Der Excel-Einstieg liest
die Arbeitsmappe als CSV ein und verwendet diesen gemeinsamen Konverter.

## Excel ausfüllen und FHIR erzeugen

1. Die [Excel-Vorlage](FHIR_Testdatengenerator_Vorlage.xlsx) kopieren und ausfüllen.
   Die [Demo](FHIR_Testdatengenerator_Interpolar_Demo.xlsx) zeigt ausgefüllte Fälle.
2. Die gewünschten Converter Options im Optionsblatt eintragen oder eine externe
   Optionsdatei auswählen.
3. Im Projektverzeichnis mit Docker und Compose starten:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f input/MeinFall.xlsx
```

Der erste Build benötigt Internet. Java und der Converter sind im Image enthalten.
Standardmäßig liest der Converter Excel-Dateien aus `input/`. Jeder Aufruf
legt einen eigenen Lauf unter `outputGlobal/run-…-excel-to-fhir/` an.

- **FHIR:** `fhir/`, nach Optionsvarianten und gegebenenfalls Eingabedateien geordnet.
- **Wirksame Optionen:** `details/options/`.
- **Berichte:** `details/reports/`.
- **Gesamtstatus:** `status.txt`.

Standardmäßig entstehen JSON und NDJSON. Mit `-r JSON`, `-r NDJSON` oder
beispielsweise `-r XML` wählen Sie das Ausgabeformat. `-p 1` erzeugt
JSON-Bundles mit jeweils einem Patienten.

[Vorlage, Optionen, Formate und vollständige Bedienung](docs/converter-usage.md)

## Einen Fall für mehrere DIZ konvertieren

Jedes Optionsblatt bildet eine Variante. Externe Optionsdateien können Sie
für viele Falldateien wiederverwenden:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f input/MeinFall.xlsx \
  --converter-options optionen/DIZ-A.config \
  --converter-options optionen/DIZ-B.config
```

Die ausgewählten Dateien bestimmen die Varianten dieses Aufrufs. Jede Variante
bekommt eigene Ausgaben und eine Kopie ihrer wirksamen Optionen. Ausgelassene
Werte verwenden die gemeinsamen Converter-Defaults.

## Eingaben prüfen und FHIR validieren

`CHECK_INPUT_CONSISTENCY` steuert die Konsistenzprüfung der Excel-Eingaben.
Die FHIR-Profil- und Terminologieprüfung aktivieren Sie zusätzlich mit `-v`.
Ein erfolgreicher Standardlauf erhält `NOT_VALIDATED` und Exitcode 0.
Bei angeforderter FHIR-Prüfung beschreibt `COMPLETE` den erfolgreichen Abschluss,
`NOT_CHECKED` eine unvollständig ausführbare Prüfung und `FAILED` einen Fehler.
Einzelheiten stehen in `status.txt` und den Berichten.

[Eingabeprüfungen](docs/contact-input-checks.md) ·
[Importbilanz](docs/import-report.md) · [FHIR-Validierung](docs/fhir-validation.md)

## CSV direkt verwenden

CSV-Dateien entsprechen den Tabellen und Spalten der Excel-Vorlage. Für die
Konvertierung gelten dieselben Converter Options, Formate und Validierungsregeln.

[CSV-Aufruf und Dateizuordnung](docs/converter-usage.md#csv-verwenden)

## Excel automatisch mit Synthea befüllen

Synthea ist eine zusätzliche Quelle für die Inhalte der Excel-Datenblätter.
Der Import überträgt die Patientengeschichte in die Vorlage; anschließend
verarbeitet der gemeinsame Excel-Konverter die Dateien mit den gewählten Optionen.
Die erzeugten Arbeitsmappen können Sie bearbeiten und erneut konvertieren.

- [Patienten erzeugen und Excel befüllen](docs/synthea-workflow.md)
- [Vorhandene Synthea-Bundles importieren](docs/synthea-manual.md)
- [Krankenhausbeispiel](examples/synthea-hospital/README.md)
- [Übernommene Inhalte und synthetische Ergänzungen](docs/synthea-clinical-import.md)

## Entwicklung

Für die Java-Entwicklung: JDK 17 und Maven 3.x.

```sh
mvn test package
python3 -m unittest discover -s scripts/tests -v
```

[Architektur](docs/architecture.md) · [Lokale Ausführung](docs/converter-usage.md#alternative-ohne-docker) · [Lizenz](LICENSE)
