# Synthea starten und KDS-FHIR erzeugen

Voraussetzung: Docker mit Compose und ausreichend Arbeitsspeicher (für den
Einstieg mindestens 8 GB für Docker; große Lebensverläufe benötigen mehr).
Java, Python und LibreOffice müssen nicht
separat installiert werden. Der erste Build benötigt Internet; der fertige
Komplettlauf läuft ohne Netzwerkzugriff und ohne Pharmindex oder externe Kataloge.

## Starten

Im frisch ausgecheckten Projektverzeichnis:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

Der erste Start baut die Werkzeuge einschließlich der festgelegten Synthea-Version.
Weitere Starts verwenden den Build-Cache; Änderungen am Projekt werden mitgebaut. Standardmäßig erzeugt Synthea
einen erwachsenen Patienten; zusätzlich können verstorbene Patienten entstehen.
Die vollständige Historie wird über unsere Excel-Vorlage nach FHIR konvertiert.

## Ergebnisse finden

Jeder Start legt einen neuen Ordner unter `outputGlobal/run-…-synthea/` an:

- **`fhir/`**: finale JSON-Bundles und `patients.ndjson` mit einem Patienten-Bundle pro Zeile.
- **`excel/Fall-<Patient-ID>.xlsx`**: Arbeitsmappen zum Ansehen oder Bearbeiten.
- **`status.txt`**: kurzer Status mit den wichtigsten Pfaden.
- **`details/reports/`**: Zusammenfassung und Werkzeugstände.
- **`details/cases/`**: Einzelberichte, CSV und Konverterprotokolle.
- **`details/converter-options.config`**: unveränderte Optionskopie dieses Laufs.
- **`details/sources/` und `details/logs/`**: originale Synthea-Ausgabe und Generatorprotokoll.

Das Datum und die Uhrzeit im Laufnamen sind UTC (`Z`). Gleichzeitige Starts
bekommen zusätzliche Nummern. Vorherige Läufe bleiben erhalten. Der Projektordner
liegt im Container unter `/workspace`; relative Pfade stimmen mit dem lokalen
Aufruf überein. Es wird nichts auf einen FHIR-Server hochgeladen.

Ein erfolgreicher Standardlauf erhält `NOT_VALIDATED` und Exitcode 0. Import
und Rückvergleich sind abgeschlossen, die optionale FHIR-Prüfung ist deaktiviert.
Bei `FAILED` ist der Lauf unvollständig; der zentrale FHIR-Ordner enthält dann
nur die erfolgreich abgeglichenen Patienten.

## FHIR-Validierung

Mit `-v` vor `--` aktivieren Sie die FHIR-Profil- und Terminologieprüfung:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea -v -- \
  -p 1 -a 30-80 -s 20260912 -cs 20260912 -r 20260912 -e 20260912
```

Für die wiederholte Verwendung kann `"-v"` in `compose.synthea.yml` vor `"--"`
in die `command`-Liste aufgenommen werden. Die Prüfung benötigt zusätzlichen
Arbeitsspeicher und Laufzeit. `COMPLETE` bedeutet, dass Import, Rückvergleich
und die angeforderte Prüfung abgeschlossen sind. `NOT_CHECKED` bezeichnet
Lücken der Terminologieprüfung und führt zu Exitcode 1. Details stehen in den
Validierungsberichten unter `details/cases/`.

## Konvertierungsoptionen einstellen

**`outputGlobal/converter-options.config`** wird beim ersten Start mit
kommentierten Workflow-Defaults angelegt. Bei Bedarf vor dem ersten Start die
gewünschten Werte in dieser Textdatei bearbeiten und den normalen Startbefehl
ausführen. Der Workflow liest die Datei automatisch. Eine vorhandene Datei wird
nicht überschrieben; fehlt sie, wird sie beim Start automatisch erzeugt.
Für fehlende oder auskommentierte Angaben gelten die Workflow-Defaults.

Beispiel für eine eigene Patienten-ID-Kennung:

```properties
PID_PREFIX = demo-
```

Der Workflow prüft die Optionen vor dem Synthea-Start. Jeder Lauf speichert eine
Kopie seiner Optionsdatei; die tatsächlich wirksamen Werte stehen zusätzlich im
Blatt **Konvertierungsoptionen** jeder erzeugten Excel-Datei. Dort lassen sie sich
für eine spätere manuelle Konvertierung ändern. Eine nachträgliche Änderung der
zentralen Textdatei verändert keine bereits erzeugten Dateien.

Bei der Konvertierung vorhandener Synthea-Bundles mit `run_synthea_cases.py` gilt
dieselbe Konvention in der Ausgabe-Wurzel. Jeder Start erzeugt einen eigenen
Laufordner mit der Endung `synthea-import`.

## Synthea einstellen

In `compose.synthea.yml` stehen die normalen Synthea-Argumente in `command`:
`-p` ist die Patientenzahl, `-a` der Altersbereich, `-s` und `-cs` sind die Seeds,
`-r` und `-e` die Simulationsdaten im Format `JJJJMMTT`. Die nativen Argumente
stehen nach `--`, damit sie von den Workflow-Optionen getrennt sind. `-o` vor
`--` ist die Ausgabe-Wurzel, standardmäßig `outputGlobal/`.
Der Workflow startet mit den voreingestellten Werten. Für wiederholbare Vergleiche Seeds
und Daten beibehalten. Die Exportform setzt der Workflow passend zum Converter. Ohne ausdrückliche
Angabe wird die vollständige Historie exportiert; mit dem normalen Synthea-Argument
`--exporter.years_of_history=7` lässt sich der Rückblick begrenzen. Ältere, weiterhin
relevante Diagnosen und Medikationen können samt Bezugskontakten erhalten bleiben.

Für einen Bestand mit vielen stationären Fällen und ambulanten Kontakten siehe
das [ausführbare Krankenhausbeispiel 2020–2026](../examples/synthea-hospital/README.md).

Die Mappings passen zum mitgelieferten Synthea-Stand. Ein Austausch gegen eine
andere Version oder zusätzliche Module braucht einen erneuten Mappingreview.

## Excel bearbeiten

Eine erzeugte `Fall-<Patient-ID>.xlsx` öffnen, prüfen und bei Bedarf ändern. Die Hinweise
stehen rechts auf den Eingabeblättern; Auswahlen unterscheiden fehlende Werte
durch den Zusatz **(Data Absent Reason)** von echten Angaben. Eine leere optionale
Spalte ist nicht automatisch ein Fehler: Ein OP-Kontakt kann etwa ohne eigenes
Ende eingetragen werden, eine Prozedur kann SNOMED statt OPS verwenden.

Die bearbeitete Datei anschließend direkt konvertieren, beispielsweise:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  -f outputGlobal/run-20260918-203000Z-synthea/excel/Fall-PATIENT.xlsx
```

Den Beispielpfad durch den tatsächlichen Dateinamen ersetzen. Es entsteht ein
neuer `outputGlobal/run-…-excel-to-fhir/` mit JSON und NDJSON. Die Arbeitsmappe
bleibt erhalten. Für die Konvertierung gelten die Einstellungen ihres Optionsblatts.

## Weitere Eingaben

Das Blatt [Fall](synthea-movements.md) erklärt primäre Aufenthalte und zusätzliche
OP-/Konsilkontakte. Die [Eingabeprüfung](contact-input-checks.md) sammelt Fehler vor
der FHIR-Erzeugung. Kontaktbeginn und Prozedurbeginn sind getrennte Eingaben.

Für vorhandene Synthea-Bundles, die erneute Konvertierung einer bearbeiteten
Excel-Datei und einen Aufbau ohne Docker siehe [manuelle Anleitung](synthea-manual.md).
Eigene Excel- und CSV-Dateien können Sie [direkt konvertieren](converter-usage.md).

## Wenn ein Lauf nicht fertig wird

- **Docker läuft nicht:** Docker starten und denselben Befehl erneut ausführen.
- **Exitcode 1 bei `NOT_CHECKED`:** Dateien sind vorhanden; die Grenzen der
  Terminologieprüfung stehen im Validierungsbericht.
- **`FAILED`:** `details/reports/summary.json` bzw. `details/reports/workflow.json` nennt den betroffenen
  Schritt. Bei einem Konvertierungsfehler steht das Detail im zugehörigen
  `conversion.log`, beim Generator in `synthea.log`.
- **Speichermangel / Exitcode 137:** Docker mehr RAM bereitstellen oder mit
  kürzerer Historie beginnen. Auch ein einzelner langer Patientenverlauf kann
  viel Speicher und Validierungszeit brauchen. Ein neuer Start erzeugt einen
  neuen Laufordner; abgebrochene Läufe werden nicht automatisch fortgesetzt.

Die Sicherheitsbefunde des eingebundenen Synthea-Generators werden separat in
[Ticket #55](https://github.com/medizininformatik-initiative/excel2fhir/issues/55)
bearbeitet. Der Offline-Lauf ist keine abgeschlossene Sicherheitsfreigabe.

Unter Linux gehören die Ergebnisse dem Eigentümer des eingebundenen
Ausgabeordners. Der Container übernimmt dessen Benutzer- und Gruppen-ID
automatisch, sodass Berichte lesbar und Excel-Dateien bearbeitbar bleiben.
