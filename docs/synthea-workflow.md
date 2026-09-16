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

Jeder Start legt einen neuen Ordner unter `outputSynthea/run-…/` an:

- **`fhir/`**: FHIR-Bundles der vollständig importierten und abgeglichenen Patienten.
- **`cases/<Patient-ID>/Fall.xlsx`**: befüllte Excel-Datei zum Ansehen oder Bearbeiten.
- **`cases/<Patient-ID>/fhir/`**: FHIR-, Import- und Validierungsberichte des Patienten.
- **`cases/summary.json`**: Übersicht, einschließlich fehlgeschlagener Patienten.
- **`converter-options.config`**: unveränderte Kopie der für diesen Lauf verwendeten Optionen.
- **`synthea/` und `synthea.log`**: unveränderte Synthea-Ausgabe und Generatorprotokoll.

Vorherige Läufe und manuell bearbeitete Excel-Dateien werden nicht überschrieben.
Die Konsolenausgabe nennt Containerpfade unter `/output`; auf Ihrem Rechner
entspricht das `outputSynthea`. Es wird nichts auf einen FHIR-Server hochgeladen.

`NOT_CHECKED` bedeutet: Import und Rückvergleich haben funktioniert, aber Teile
der FHIR-Prüfung waren wegen fehlender Terminologien nicht ausführbar. Die Dateien
sind vorhanden; Exitcode 1 signalisiert diese Einschränkung. Das ist keine
Bestätigung vollständiger KDS-Konformität. Bei `FAILED` ist der Lauf unvollständig;
der zentrale FHIR-Ordner enthält dann nur die erfolgreich abgeglichenen Patienten.

## Konvertierungsoptionen einstellen

**`outputSynthea/converter-options.config`** liegt bereits nach dem Checkout mit
kommentierten Workflow-Defaults bereit. Bei Bedarf vor dem ersten Start die
gewünschten Werte in dieser Textdatei bearbeiten und den normalen Startbefehl
ausführen. Es ist kein zusätzlicher Parameter nötig. Eine vorhandene Datei wird
nicht überschrieben; fehlt sie, wird sie beim Start automatisch erzeugt.
Fehlende oder auskommentierte Angaben verwenden weiterhin die Workflow-Defaults.

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
dieselbe Konvention direkt im angegebenen Ausgabeordner. Dieser darf vorher nur
`converter-options.config` enthalten; fehlt sie, wird sie dort angelegt.

## Synthea einstellen

In `compose.synthea.yml` stehen die normalen Synthea-Argumente in `command`:
`-p` ist die Patientenzahl, `-a` der Altersbereich, `-s` und `-cs` sind die Seeds,
`-r` und `-e` die Simulationsdaten im Format `JJJJMMTT`.
Zum Ausprobieren muss nichts geändert werden. Für wiederholbare Vergleiche Seeds
und Daten beibehalten. Die Exportform setzt der Workflow passend zum Converter. Ohne ausdrückliche
Angabe wird die vollständige Historie exportiert; mit dem normalen Synthea-Argument
`--exporter.years_of_history=7` lässt sich der Rückblick begrenzen. Ältere, weiterhin
relevante Diagnosen und Medikationen können samt Bezugskontakten erhalten bleiben.

Für einen Bestand mit vielen stationären Fällen und ambulanten Kontakten siehe
das [ausführbare Krankenhausbeispiel 2020–2026](../examples/synthea-hospital/README.md).

Die Mappings passen zum mitgelieferten Synthea-Stand. Ein Austausch gegen eine
andere Version oder zusätzliche Module braucht einen erneuten Mappingreview.

## Excel bearbeiten

Eine erzeugte `Fall.xlsx` öffnen, prüfen und bei Bedarf ändern. Die Hinweise
stehen rechts auf den Eingabeblättern; Auswahlen unterscheiden fehlende Werte
durch den Zusatz **(Data Absent Reason)** von echten Angaben. Eine leere optionale
Spalte ist nicht automatisch ein Fehler: Ein OP-Kontakt kann etwa ohne eigenes
Ende eingetragen werden, eine Prozedur kann SNOMED statt OPS verwenden.

Für die erneute Konvertierung eine Kopie unter
`outputSynthea/review/Fall.xlsx` speichern. Mit dem bereits gebauten Image:

```sh
docker compose -f compose.synthea.yml run --rm --entrypoint java synthea \
  -XX:MaxRAMPercentage=50 -Duser.timezone=Europe/Berlin \
  -jar /app/target/excel2fhir.jar -v \
  -f /output/review/Fall.xlsx -t /output/review/csv -o /output/review/fhir
```

Die Ergebnisse liegen unter `outputSynthea/review/fhir/`. Bei Wiederholung werden
die dortigen Ergebnisse und `review/csv/` ersetzt; die Excel-Datei bleibt erhalten.
Dieser Schritt verwendet die Optionen im Excel-Blatt. Er startet Synthea nicht
erneut und vergleicht Ihre Änderungen nicht gegen die ursprüngliche Geschichte.

## Weitere Eingaben

Das Blatt [Fall](synthea-movements.md) erklärt primäre Aufenthalte und zusätzliche
OP-/Konsilkontakte. Die [Eingabeprüfung](contact-input-checks.md) sammelt Fehler vor
der FHIR-Erzeugung. Kontaktbeginn und Prozedurbeginn sind getrennte Eingaben.

Für vorhandene Synthea-Bundles, die erneute Konvertierung einer bearbeiteten
Excel-Datei und einen Aufbau ohne Docker siehe [manuelle Anleitung](synthea-manual.md).
Der bisherige Excel→FHIR- und CSV→FHIR-Einstieg bleibt unverändert.

## Wenn ein Lauf nicht fertig wird

- **Docker läuft nicht:** Docker starten und denselben Befehl erneut ausführen.
- **Exitcode 1 bei `NOT_CHECKED`:** Dateien sind vorhanden; die Grenzen der
  Terminologieprüfung stehen im Validierungsbericht.
- **`FAILED`:** `cases/summary.json` bzw. `workflow.json` nennt den betroffenen
  Schritt. Bei einem Konvertierungsfehler steht das Detail im zugehörigen
  `conversion.log`, beim Generator in `synthea.log`.
- **Speichermangel / Exitcode 137:** Docker mehr RAM bereitstellen oder mit
  kürzerer Historie beginnen. Auch ein einzelner langer Patientenverlauf kann
  viel Speicher und Validierungszeit brauchen. Ein neuer Start erzeugt einen
  neuen Laufordner; abgebrochene Läufe werden nicht automatisch fortgesetzt.

Die Sicherheitsbefunde des eingebundenen Synthea-Generators werden separat in
[Ticket #55](https://github.com/medizininformatik-initiative/excel2fhir/issues/55)
bearbeitet. Der Offline-Lauf ist keine abgeschlossene Sicherheitsfreigabe.
