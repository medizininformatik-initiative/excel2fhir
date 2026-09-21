# Deutsche Synthea-Lesetexte

Der Import verwendet eine feste, versionierte Tabelle für deutsche klinische
Bezeichnungen, Textwerte und die Satzbausteine der Synthea-Dokumente. Die
Fallgenerierung braucht weder einen Terminologieserver noch ein Übersetzungsmodell
oder einen Netzzugang. Verbesserungen erfolgen direkt an der Tabelle.

## Bestand und Herkunft

`scripts/mappings/synthea-german-texts.json` folgt dem vollständigen vorhandenen
Coderegister zum Synthea-Commit `d9d07a6eef91ee5144293b42ab64224d84d124f8`:

- 2.342 System/Code-Paare, davon 2.278 mit mindestens einem Quellbezeichner.
- 2.426 Übersetzungszeilen für codierte Originaltexte; identische Texte können
  bei mehreren Codes vorkommen. Das sind 2.416 unterschiedliche Originalstrings.
- 144 zusätzliche uncodierte Texte und 35 feste Dokumentbausteine.
- 64 Registereinträge ohne Originalbezeichner, überwiegend technische Werte,
  bleiben ohne erfundene Bezeichnung im Inventar.

Jede Übersetzungszeile enthält `original`, `de`, `source` und `review`. Die
Zuordnung steht unter dem jeweiligen `system` und `code`. Der SHA-256 des
Quellregisters verhindert unbemerkten Versionsversatz. Die Tabelle enthält
projektinterne Lesetexte für Testdaten, keine amtliche deutsche SNOMED-/LOINC-
Distribution und keine validierte Übersetzung medizinischer Fragebögen.

Die Quellen wurden tatsächlich geprüft und wie folgt verwendet:

| Herkunft | Verwendung |
| --- | --- |
| Redaktionelle Übersetzungen | 699 codierte Textvarianten korrigiert bzw. direkt übersetzt, außerdem Symptome und Satzbausteine |
| Festes Arzneimittelwörterbuch | 521 codierte Textvarianten mit deutschen Wirkstoffbezeichnungen und Darreichungsformen; Zahlen und Produktnamen erhalten |
| [Wikidata](https://query.wikidata.org/sparql) | Exakter SNOMED-Code-Abgleich über P5806: 35 Treffer, davon 24 geeignete Begriffe für 32 Textvarianten übernommen; strukturierte Daten CC0 |
| [OPUS-MT Englisch–Deutsch](https://huggingface.co/Helsinki-NLP/opus-mt-en-de) | Einmalige lokale Entwurfserstellung mit `opus-2020-02-26`; 1.174 codierte Varianten bleiben ausdrücklich als maschinelle Entwürfe mit ausstehendem fachlichem Review markiert |

OPUS-MT stammt von der Helsinki-NLP-Gruppe. Der verwendete
[Modellstand](https://object.pouta.csc.fi/OPUS-MT-models/en-de/opus-2020-02-26.zip)
steht unter CC BY 4.0; die Lizenz liegt unter `third-party/opus-mt/LICENSE.txt`.
Redaktionelle Änderungen sind in der Tabelle als solche gekennzeichnet.
Das Modell, seine Python-Umgebung und die temporären Arbeitsdateien werden nicht
mitgeliefert und sind keine Projektabhängigkeiten.

Der öffentliche deutsche MeSH-Download war durch die Zugriffsprüfung des Anbieters
blockiert. Eine deutsche LOINC-Distribution und eine deutsche SNOMED-Edition
lagen nicht vor. Aus diesen Quellen wurde nichts als amtlicher Text übernommen.
Die vorhandenen ICD-10-GM-Zieltexte wurden nicht als vermeintlich identische
Übersetzungen der nur näherungsweise zugeordneten SNOMED-Konzepte verwendet.

## Vorher und nachher

| Feld / Baustein | Vorher | Nachher |
| --- | --- | --- |
| Diagnose | `Tubal pregnancy` | `Eileiterschwangerschaft` |
| Prozedur | `Medication Reconciliation (procedure)` | `Medikationsabgleich` |
| Laborbefund | `Glucose [Mass/volume] in Blood` | `Glucose [Masse/Volumen] im Blut` |
| Medikament | `Acetaminophen 325 MG Oral Tablet` | `Paracetamol 325 mg Tablette zum Einnehmen` |
| Codierte Antwort | `Yes` | `Ja` |
| Dokument | `# Chief Complaint` | `# Beschwerden` |
| Dokument | `Patient quit smoking at age 16.` | `Rauchstopp im Alter von 16 Jahren.` |

Excel-Blätter, Spaltenköpfe, Formeln, Auswahlwerte, Verknüpfungen und Formatierung
werden durch diese Änderung nicht umgebaut. Die bestehenden Vorlagen werden
kopiert und über LibreOffice/UNO befüllt. Übersetzt werden ausschließlich die
dafür vorgesehenen klinischen Bezeichner-, Textwert- und Dokumenttextspalten.
FHIR-Codes, Statuswerte, IDs, Referenzen, Zeitpunkte und numerische Messwerte
gehen nicht durch die Textersetzung.

Adressen in der Observation mit LOINC `56799-0` verwenden dieselbe synthetische
deutsche Straßenadresse wie `Patient.address`. Patientenspezifische US-Adressen
sind keine Einträge der Übersetzungstabelle. Dieser Schritt ersetzt synthetische
Personendaten und ist keine sprachliche Übersetzung.

Namen, Alter und Rauchstoppalter in Dokumenten bleiben Parameter. Die vorhandene
Erzählstruktur und die klinischen Listen werden übersetzt. Eine US-Versicherung
wie Medicare bleibt als Eigenname erhalten und wird als „Krankenversicherung laut
Quelle“ beschriftet. US-Schulabschlüsse und ethnische Quellkategorien werden
deutsch beschrieben, ohne daraus deutsche Versicherungs- oder Bildungsdaten
zu erfinden. Die Deutschland-Anpassung dieser Sachverhalte bleibt eine eigene
fachliche Aufgabe.

## Prüfung und Ergänzung

`germanTexts` im Verlustbericht enthält Tabellenversion, Tabellenhash, Anzahl
übersetzter Vorkommen und sämtliche unbekannten Textfragmente unter `missing`.
Unbekannte Eingaben bleiben im Original erhalten und werden dort ausdrücklich
aufgeführt. Ein unbekannter Code wird nicht allein wegen eines ähnlich lautenden
Textes einer anderen Terminologie zugeordnet.

`documentIdentityChanges` dokumentiert weiterhin die vorgelagerte Namensersetzung;
sein `targetTextSha256` bezieht sich auf diesen Zwischenschritt. Die anschließende
Übersetzung ist separat unter `germanTexts` dokumentiert. Der Rückvergleich prüft
den vollständigen finalen deutschen Dokumenttext gegen die FHIR-Ausgabe.

Prüfung:

```sh
python3 -m unittest discover -s scripts/tests -v
mvn test
python3 scripts/run_synthea_cases.py -i SYNTHEA_FHIR_DIRECTORY -o OUTPUT_DIRECTORY
```

Die Tests prüfen Registerabdeckung, Herkunftsangaben, sämtliche Zahlen und
Produktnamen in Arzneimittelbezeichnungen, Verneinung, Seitenangaben,
Dokumentparameter und den Schutz technischer Spalten. Die 18 vorhandenen Fälle
mit 3.574 Dokumenten wurden vollständig vorbereitet: keine fehlenden
Textzuordnungen in den erfassten Feldern. Abdeckung ist keine medizinische
Qualitätsgarantie: Die Rohübersetzung enthielt unter anderem eine vertauschte
Ja-Antwort und falsche Begriffe für Herzstillstand und Eileiterschwangerschaft.
Diese Fehler sind korrigiert und durch Regressionstests abgesichert.

Alle 39 Python- und 39 Java-Tests sowie drei vollständige erneute
Excel→CSV→FHIR-Rückvergleiche (Corinne, George, Tad) bestanden. Gegenüber den
vorherigen Beispielen änderten sich ausschließlich die bezeichneten Textspalten;
Codes-Blatt und technische Zellwerte blieben erhalten. Gerenderte Labor- und
Diagnoseansichten zeigen die deutschen Texte. Die enge Parameter-Spalte im
Laborblatt schneidet längere Bezeichnungen sichtbar ab und bleibt Layoutreview.

## Späterer Nutzerreview

1. Lesbarkeit und medizinischer Ton in den gefüllten Excel-Blättern und Dokumenten.
2. Die als `pending-medical-review` markierten Übersetzungen, insbesondere
   spezialisierte Prozeduren, Geräte und Fragebogentexte. Auch redaktionelle
   Korrekturen stellen keine ärztliche Freigabe dar.
3. Lange deutsche Bezeichnungen bei den bestehenden Spaltenbreiten und die
   weiterhin sehr umfangreichen Dokumente in einzelnen Zellen.
4. Deutsche Anzeige der bisher technischen Status-/Kategoriewerte in den
   Auswahllisten; dabei Codes-Blatt, Links und Java-Einleselogik gemeinsam ändern.
5. Weitere Deutschland-Anpassung der US-Versicherungs-, Sozial- und Bildungsdaten.

Neue Begriffe oder bessere Übersetzungen werden an der bestehenden Tabelle
ergänzt. Ein späterer Terminologieabgleich kann einzelne Zeilen ersetzen, ohne
den Import oder die Fallgenerierung neu aufzubauen.
