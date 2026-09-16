# excel2fhir

Mit `excel2fhir` erzeugen Sie synthetische FHIR-R4-Testdaten für den deutschen
MII-Kerndatensatz (KDS). Starten Sie mit Synthea oder mit einer eigenen Excel-Datei.
Der bestehende CSV→FHIR-Weg bleibt ebenfalls verfügbar.

| Ausgangspunkt | Anleitung |
| --- | --- |
| Neue Patienten mit Synthea erzeugen | [Synthea → Excel → FHIR](docs/synthea-workflow.md) |
| Viele stationäre Fälle im Zeitraum 2020–2026 | [Krankenhausbeispiel](examples/synthea-hospital/README.md) |
| Excel-Vorlage ausfüllen oder vorhandene Excel-/CSV-Daten konvertieren | [Excel und CSV verwenden](docs/converter-usage.md) |
| Vorhandene Synthea-Bundles importieren oder ohne Docker arbeiten | [Manueller Ablauf](docs/synthea-manual.md) |

## Synthea ausprobieren

Voraussetzung: Docker mit Compose, mindestens 8 GB für Docker und Internet für
den ersten Build. Große Patientenverläufe benötigen mehr Speicher. Nach dem
Checkout im Projektverzeichnis starten:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

Java, Python und LibreOffice sind im Image enthalten. Der fertige Lauf arbeitet
ohne Netzwerk und benötigt keine externen Medikamentenkataloge.

- **FHIR-Dateien:** `outputSynthea/run-…/fhir/`
- **Excel-Dateien:** `outputSynthea/run-…/cases/<Patient-ID>/Fall.xlsx`
- **Einstellungen:** Synthea-Argumente in `compose.synthea.yml`, Converter-Optionen
  in `outputSynthea/converter-options.config`. Beide Dateien sind bereits vorhanden.

Jeder Lauf bekommt einen eigenen Ordner. Die Excel-Dateien können Sie ansehen,
bearbeiten und anschließend [erneut konvertieren](docs/synthea-workflow.md#excel-bearbeiten).
Standardmäßig entsteht die Lebensgeschichte eines erwachsenen Patienten;
Synthea kann zusätzlich verstorbene Patienten ausgeben.

## Ergebnisse beurteilen

Der Workflow prüft den Import, vergleicht die übernommenen Inhalte mit Synthea
und validiert das erzeugte FHIR. **`NOT_CHECKED`** bedeutet, dass Teile der
Terminologieprüfung nicht ausführbar waren. Die Dateien liegen trotzdem vor;
der Prozess liefert dafür Exitcode 1. **`FAILED`** bezeichnet einen unvollständigen
Lauf. Einzelheiten stehen in `workflow.json` und `cases/summary.json`.

Die Daten enthalten ausdrücklich synthetische deutsche Ergänzungen und
näherungsweise Codezuordnungen. Nicht jede Synthea-Eigenschaft wird übernommen;
Auslassungen werden berichtet. Vollständige KDS-/Terminologiekonformität wird
nicht pauschal zugesichert. [Importumfang und Grenzen](docs/synthea-clinical-import.md).

Im eingebundenen Synthea-Generator bestehen bekannte Sicherheitsbefunde in
Abhängigkeiten. Ihre Bereinigung wird in [Ticket #55](https://github.com/medizininformatik-initiative/excel2fhir/issues/55)
bearbeitet; der vollständige Workflow hat noch keine abgeschlossene Sicherheitsfreigabe.
Das bisherige Converter-Image enthält diesen Generator nicht.

## Excel ohne Synthea ausprobieren

Das Repository enthält die [Vorlage](FHIR_Testdatengenerator_Vorlage.xlsx) und
eine [ausgefüllte Demo](FHIR_Testdatengenerator_Interpolar_Demo.xlsx). Eine Datei
kann mehrere Patienten enthalten. Mit Docker:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f /app/input/FHIR_Testdatengenerator_Interpolar_Demo.xlsx
```

FHIR liegt unter `outputGlobal/`, Zwischen-CSV unter `outputLocal/`.
Diese Ordner sind für Konverterausgaben reserviert und können beim nächsten
Aufruf geleert werden. [Eigene Dateien, Optionen und CSV-Einstieg](docs/converter-usage.md).

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
