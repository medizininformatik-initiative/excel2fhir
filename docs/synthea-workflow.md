# Excel mit Synthea befüllen

Synthea erzeugt Patientengeschichten. Der Import überträgt sie in die
Excel-Vorlage und startet den gemeinsamen Excel-zu-FHIR-Konverter. Dessen
[Optionen, Ausgabeformate und Prüfungen](converter-usage.md) gelten auch hier.
Die Arbeitsmappen stehen anschließend zur manuellen Bearbeitung bereit.

## Starten

Voraussetzung: Docker mit Compose. Für den Generator mindestens 8 GB
Docker-Arbeitsspeicher bereitstellen; große Lebensverläufe benötigen mehr.
Der erste Build benötigt Internet. Java, Python und LibreOffice sind im Image enthalten.

Im Projektverzeichnis:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

Die Vorgaben in `compose.synthea.yml` erzeugen die vollständige Geschichte eines
erwachsenen Patienten. Synthea kann zusätzlich verstorbene Patienten ausgeben.
Der fertige Container arbeitet offline.

## Konverter und Datenerzeugung einstellen

**Vor `--` stehen die Konverterparameter, danach die Synthea-Parameter.**

```sh
docker compose -f compose.synthea.yml run --build --rm synthea \
  --converter-options optionen/DIZ-A.config \
  --converter-options optionen/DIZ-B.config \
  -r XML -p 1 -- \
  -p 5 -a 30-80 -s 20260912 -cs 20260912 -r 20260912 -e 20260912
```

Dieser Aufruf erzeugt mit Synthea fünf Patienten und konvertiert die ausgefüllten
Excel-Dateien für zwei DIZ in XML. Vor `--` bedeutet `-p 1` ein Patient pro Bundle;
nach `--` bedeutet `-p 5` fünf von Synthea zu erzeugende Patienten.

Die gemeinsamen Konverterparameter sind:

| Parameter | Bedeutung |
| --- | --- |
| `--converter-options DATEI` | Externer Optionssatz; für mehrere Varianten wiederholen. |
| `-r FORMATE` | Ausgabeformate, etwa `JSON`, `NDJSON`, `XML` oder `JSON,NDJSON`. |
| `-p ANZAHL` | Maximale Patientenanzahl pro Bundle bei der Konvertierung. |
| `-v` | FHIR-Profil- und Terminologieprüfung aktivieren. |
| `-vll STUFE` / `-l LAYOUT` | Validierungslog und Konverterprotokoll einstellen. |
| `-o ORDNER` | Ausgabe-Wurzel; Standard `outputGlobal/`. |
| `-t ORDNER` | Separater Wurzelordner für Zwischen-CSV. |

Die erzeugte Arbeitsmappe enthält die gemeinsamen Converter-Defaults.
Externe Optionsdateien bestimmen die Varianten für den Aufruf. Dieselben
Dateien können Sie auch bei einer manuellen Excel- oder CSV-Konvertierung verwenden.

Native Synthea-Parameter sind beispielsweise `-a` für den Altersbereich,
`-s` und `-cs` für Seeds sowie `-r` und `-e` für Simulationsdaten im Format
`JJJJMMTT`. `--exporter.years_of_history=7` begrenzt die exportierte Historie.
Für wiederholbare Fälle dieselben Seeds und Simulationsdaten verwenden.

## Ergebnisse

Jeder Aufruf erzeugt einen Lauf unter `outputGlobal/run-…-synthea/`:

- `excel/Fall-<Patient-ID>.xlsx`: die ausgefüllten Arbeitsmappen.
- `fhir/`: die gewählten Formate, nach Optionsvarianten und Eingabedateien geordnet.
- `details/options-input/`: Kopien ausdrücklich gewählter Optionsdateien.
- `details/cases/`: Konverterläufe mit wirksamen Optionen, Importberichten und Rückvergleich.
- `details/reports/`: Gesamtbericht und Werkzeugstände.
- `details/sources/` und `details/logs/`: Synthea-Quelldaten und Protokolle.
- `status.txt`: Gesamtstatus und Ausgabepfade.

`NOT_VALIDATED` mit Exitcode 0 bedeutet: Import und Rückvergleich abgeschlossen,
FHIR-Validierung deaktiviert. Mit aktivierter Prüfung bedeutet `COMPLETE`
erfolgreicher Abschluss; `NOT_CHECKED` kennzeichnet Terminologielücken und
liefert Exitcode 1. `FAILED` kennzeichnet einen fehlgeschlagenen Schritt.
Die Berichte benennen Quelle, Variante und Ursache; erzeugte Dateien bleiben zur Prüfung erhalten.

## FHIR-Validierung

Die Prüfung aktivieren Sie mit `-v` vor `--`:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea -v -- \
  -p 1 -a 30-80 -s 20260912 -cs 20260912 -r 20260912 -e 20260912
```

Profile und Terminologien benötigen zusätzlichen Arbeitsspeicher und Laufzeit.
[Details zu Berichten und Prüfgrenzen](fhir-validation.md).

## Excel bearbeiten

Eine erzeugte Arbeitsmappe öffnen, die Datenblätter bearbeiten und anschließend
mit dem Excel-Einstieg konvertieren:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f outputGlobal/run-BEISPIEL-synthea/excel/Fall-PATIENT.xlsx \
  --converter-options optionen/DIZ-A.config
```

Das Optionsblatt der Arbeitsmappe oder die ausdrücklich gewählten externen
Optionsdateien bestimmen die FHIR-Darstellung.

## Weitere Informationen

- [Vorhandene Synthea-Bundles und lokaler Aufbau](synthea-manual.md)
- [Krankenhausbeispiel 2020–2026](../examples/synthea-hospital/README.md)
- [Importumfang, Mappings und Ergänzungen](synthea-clinical-import.md)
- [Kontaktzeiten](synthea-movements.md)

Unter Linux verwendet der Komplettlauf den Eigentümer des eingebundenen
Ausgabeordners. Die Ergebnisse bleiben für diesen Benutzer bearbeitbar.
Bei Speichermangel Docker mehr RAM zuweisen oder die Historie begrenzen.

Die Abhängigkeiten des eingebundenen Synthea-Generators haben bekannte
Sicherheitsbefunde; deren Bereinigung ist in
[Ticket #55](https://github.com/medizininformatik-initiative/excel2fhir/issues/55) erfasst.
