# Krankenhausbeispiel für 2020–2026

Dieses Beispiel erzeugt zehn Patienten mit vielen stationären Aufenthalten und
behält ihre ambulanten Kontakte. Es ist eine bewusst ausgewählte Demonstration,
keine statistisch repräsentative Stichprobe und kein Abbild eines einzelnen realen
Krankenhauses. Die stationären Kontakte stammen aus Synthea, nicht aus nachträglicher
Umklassifizierung ambulanter Kontakte.

Voraussetzung: Docker/Compose. Alle Befehle im Projektverzeichnis ausführen.
Die Zielordner `hospital-pool`, `hospital-selection` und `hospital-results` dürfen
noch nicht bestehen, damit keine Patienten aus früheren Läufen dazugemischt werden.
Für einen weiteren Lauf andere Ordnernamen verwenden.

## 1. Kandidaten erzeugen

Synthea soll 60 ältere Patienten mit Herzoperationen erzeugen. Verstorbene können
zusätzlich ausgegeben werden. Das `-k`-Modul verlangt einen Eingriff irgendwann
im Leben; erst der nächste Schritt prüft die stationären Fälle im Zielzeitraum.

```sh
docker compose -f compose.synthea.yml run --build --rm --entrypoint java synthea \
  -Xmx4g -Duser.timezone=Europe/Berlin -jar /app/target/synthea.jar \
  -p 60 -a 60-85 -s 20260917 -cs 20260916 -r 20270101 -e 20270101 \
  -k must_have_cardiac_surgery.json \
  --exporter.baseDirectory=/output/hospital-pool \
  --exporter.years_of_history=7 \
  --exporter.fhir.export=true --exporter.fhir_stu3.export=false \
  --exporter.fhir_dstu2.export=false --exporter.fhir.bulk_data=false \
  --exporter.use_uuid_filenames=true \
  --exporter.hospital.fhir.export=false --exporter.practitioner.fhir.export=false
```

Die Simulation reicht hier bewusst bis Anfang 2027, um 2026 abzudecken; die
Daten nach dem heutigen Datum sind genauso erfunden wie der übrige Verlauf.
Syntheas Historienfilter rechnet mit **365 Tagen je Jahr**, nicht mit Kalenderjahren.
Sieben Jahre bis 01.01.2027 ergeben daher ungefähr **03.01.2020–01.01.2027**;
dies ist kein tagexakter Export vom 01.01.2020 bis 31.12.2026.
Fortbestehende Diagnosen, Medikationen und Behandlungspläne sowie ihre ursprünglichen
Bezugskontakte können älter sein. Geburtsdaten bleiben selbstverständlich erhalten.
Der Filter ist keine vollständige Entfernung aller älteren Zeitangaben.

## 2. Zehn Fälle auswählen

```sh
docker compose -f compose.synthea.yml run --rm \
  -v "$PWD/examples/synthea-hospital:/recipes:ro" \
  --entrypoint python3 synthea /recipes/select_cases.py \
  /output/hospital-pool/fhir /output/hospital-selection
```

Das Rezept wählt sechs Patienten mit mindestens zwei stationären Aufenthalten,
zwei mit einem und zwei ohne stationären Aufenthalt **im Zeitraum 2020–2026**.
Es zählt originale `IMP`-Einrichtungskontakte; ergänzte Abteilungs- oder Bettkontakte
zählen nicht als weitere stationäre Fälle. Innerhalb der Gruppen wird reproduzierbar
sortiert. Alle Quelldateien werden unverändert kopiert, einschließlich ambulanter
Kontakte, virtueller Kontakte und gegebenenfalls häuslicher Versorgung.
`hospital-selection/selection.json` enthält Auswahl, Fallzahlen und Quellprüfsummen.
Reichen die Kandidaten nicht aus, einen größeren Pool oder einen anderen Seed erzeugen.
Patientenzahl und Altersbereich stehen im ersten Befehl; die kleine Beispielauswahl
ist in `select_cases.py` direkt lesbar festgelegt.

## 3. Excel und FHIR erzeugen

```sh
docker compose -f compose.synthea.yml run --rm --entrypoint python3 synthea \
  /app/scripts/run_synthea_cases.py \
  /output/hospital-selection/fhir /output/hospital-results
```

Danach liegen unter `outputSynthea/hospital-results/<Patient-ID>/` jeweils
`Fall.xlsx`, CSV, FHIR und die Prüfberichte. `summary.json` fasst alle Patienten
zusammen. Bei `NOT_CHECKED` sind Import und Rückvergleich erfolgreich, während
Terminologieprüfungen unvollständig bleiben; der aktuelle Prozess liefert dafür
Exitcode 1. Bei `FAILED` die konkreten Fehler prüfen.

Es gibt weiterhin eine Excel-Datei pro Synthea-Patient. Der ursprüngliche
Excel-/CSV-Konverter kann weiterhin mehrere Patienten in einer Eingabe verarbeiten.
Die ambulanten Endzeitpunkte werden hier unverändert aus Synthea übernommen;
andere Krankenhaus-Abschlussregeln sind noch nicht umgesetzt.

Für andere Szenarien Syntheas Alter, Seeds und passende Keep-Module verwenden.
Nur `-p` zu erhöhen garantiert keinen höheren stationären Anteil. Zusätzliche
Synthea-Module erfordern einen erneuten Mapping-Abgleich.
