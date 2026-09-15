# Synthea starten und KDS-FHIR erzeugen

Voraussetzung: Docker mit Compose und ausreichend Arbeitsspeicher (für den
Einstieg mindestens 8 GB für Docker). Java, Python und LibreOffice müssen nicht
separat installiert werden. Der erste Build benötigt Internet; der fertige
Komplettlauf läuft ohne Netzwerkzugriff und ohne Pharmindex oder externe Kataloge.

## Starten

Im frisch ausgecheckten Projektverzeichnis:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

Der erste Start baut die Werkzeuge einschließlich der festgelegten Synthea-Version.
Weitere Starts verwenden das bereits gebaute Image. Standardmäßig erzeugt Synthea
einen erwachsenen Patienten; zusätzlich können verstorbene Patienten entstehen.
Die vollständige Historie wird über unsere Excel-Vorlage nach FHIR konvertiert.

## Ergebnisse finden

Jeder Start legt einen neuen Ordner unter `outputSynthea/run-…/` an:

- **`fhir/`**: FHIR-Bundles der vollständig importierten und abgeglichenen Patienten.
- **`cases/<Patient-ID>/Fall.xlsx`**: befüllte Excel-Datei zum Ansehen oder Bearbeiten.
- **`cases/<Patient-ID>/fhir/`**: FHIR-, Import- und Validierungsberichte des Patienten.
- **`cases/summary.json`**: Übersicht, einschließlich fehlgeschlagener Patienten.
- **`synthea/` und `synthea.log`**: unveränderte Synthea-Ausgabe und Generatorprotokoll.

Vorherige Läufe und manuell bearbeitete Excel-Dateien werden nicht überschrieben.

`NOT_CHECKED` bedeutet: Import und Rückvergleich haben funktioniert, aber Teile
der FHIR-Prüfung waren wegen fehlender Terminologien nicht ausführbar. Die Dateien
sind vorhanden; Exitcode 1 signalisiert diese Einschränkung. Das ist keine
Bestätigung vollständiger KDS-Konformität. Bei `FAILED` ist der Lauf unvollständig;
der zentrale FHIR-Ordner enthält dann nur die erfolgreich abgeglichenen Patienten.

## Synthea einstellen

In `compose.synthea.yml` stehen die normalen Synthea-Argumente in `command`:
`-p` ist die Patientenzahl, `-a` der Altersbereich, `-s` und `-cs` sind die Seeds,
`-r` und `-e` die Simulationsdaten im Format `JJJJMMTT`.
Zum Ausprobieren muss nichts geändert werden. Für wiederholbare Vergleiche Seeds
und Daten beibehalten. Die Exportform und die vollständige Historie setzt der
Workflow passend zum Converter; dafür sind keine Einstellungen nötig.

Die Mappings passen zum mitgelieferten Synthea-Stand. Ein Austausch gegen eine
andere Version oder zusätzliche Module braucht einen erneuten Mappingreview.

## Excel bearbeiten oder vorhandene Daten verwenden

Das Blatt [Fall](synthea-movements.md) erklärt primäre Aufenthalte und zusätzliche
OP-/Konsilkontakte. Die [Eingabeprüfung](contact-input-checks.md) sammelt Fehler vor
der FHIR-Erzeugung. Kontaktbeginn und Prozedurbeginn sind getrennte Eingaben.

Für vorhandene Synthea-Bundles, die erneute Konvertierung einer bearbeiteten
Excel-Datei und einen Aufbau ohne Docker siehe [manuelle Anleitung](synthea-manual.md).
Der bisherige Excel→FHIR- und CSV→FHIR-Einstieg bleibt unverändert.
