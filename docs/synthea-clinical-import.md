# Klinische Synthea-Fälle über Excel

Der Import nutzt die vorhandenen Synthea-R4-Dateien und füllt Kopien der
bestehenden Excel-Vorlage. Die Datei bleibt die bearbeitbare Schnittstelle;
Excel→FHIR benötigt anschließend weder Synthea noch einen Terminologieserver.

## Bestand und Mapping

`scripts/mappings/synthea-source-code-registry.json` enthält 2.342 unterschiedliche
System-/Code-Paare des vorhandenen Gesamtinventars nach Ausschluss der
Modulvorlagen. Beim Aufbau wurden die Prüfsummen von 620 Synthea-Quelldateien
abgeglichen. Das Register umfasst auch Hilfstabellen, Exporterwerte und
Prädikate. Es ist keine Behauptung, dass alle Einträge primäre klinische Codes
sind oder im R4-Export erscheinen.

Reproduktion mit den vorhandenen externen Inventardateien:

```sh
python3 scripts/build_synthea_code_registry.py INVENTARVERZEICHNIS SYNTHEA_CHECKOUT AUSGABE.json
```

Die Diagnose-Tabelle v4 beurteilt weiterhin alle 333 produktiven Diagnosecodes:
321 ICD-10-GM-Zuordnungen, 10 dokumentierte Auslassungen reiner Sozialangaben/
organisatorischer Aufgaben und 2 erhaltene Konzepte ohne ICD-Ergänzung. Alle 495 Medikamentenkonzepte des festgelegten Inventars erhalten eine deutsche
ATC-2026-Zuordnung, öffentlich belegte UNII-Wirkstoffschlüssel und eine ausgewählte
echte deutsche PZN samt Präparatname und Form. Die Auswahl konkretisiert die
synthetische Geschichte und behauptet keine pharmazeutische Gleichwertigkeit. RxNorm bleibt ausschließlich
im Quellen-/Mappingbericht. Fehlende Zuordnungen stehen sichtbar im Präparattext;
Medikationsereignisse werden deshalb nicht ausgelassen. Impfstoffe erhalten eine
breitere ATC-2026-Klassifikation statt CVX, ohne behauptete Produktäquivalenz.
Die detaillierte Impfstoffbeschreibung und das Ereignis bleiben erhalten.
[Katalogformat, Quellen und Grenzen](medication-product-catalog.md).
Die Prozedurtabelle behandelt 429 Quellkonzepte: alle 428 Procedure-State-Konzepte
und die zusätzlich in generierten Fällen vorkommende kombinierte CT von Thorax,
Abdomen und Becken. 121 Konzepte besitzen ein einzelnes OPS-Ziel. Weitere
Zuordnungen entstehen durch regionale Aufteilungen und Chemotherapieblöcke.
OPS-Zeilen verwenden die offizielle Beschreibung des jeweiligen terminalen
OPS-2026-Codes. Die synthetischen Annahmen stehen im Mappingbericht.

Kombinierte CT-Untersuchungen werden in getrennte Zeilen je Körperregion
aufgeteilt. Radiochemotherapie erzeugt einzelne Bestrahlungsfraktionen und einen
Chemotherapieeintrag je Kontakt und Therapieblock. Dafür werden Behandlungstage
und unterschiedliche parenterale Zytostatika aus zugehörigen Verabreichungen
ermittelt. Wiederholte Gaben derselben Substanz erhöhen die Substanzzahl nicht.
Mindestens zwei volle Pausentage beginnen einen neuen Block. Ohne zuordenbare
Gaben wird eine intravenöse Substanz ausdrücklich synthetisch angenommen.
Bei längeren Blöcken mit 5-FU, ARA-C, Azacitidin oder Decitabin bleiben die
Quellereignisse erhalten, wenn die für die OPS-Abgrenzung benötigten Dosis- und
Infusionsangaben fehlen.
Einzelne Bestrahlungen und regionale CT-Zeilen behalten das gemeinsame
Quellzeitfenster. Der Chemotherapieblock umfasst das erste bis letzte Ereignis.
Diese Aufteilungen enthalten keinen SNOMED-Zusatzcode. Originalcodes und alle
zugehörigen Quell-IDs bleiben im Bericht und im unveränderten Quellbundle erhalten.

Naheliegende Konkretisierungen ergänzen unter anderem Füllungsmaterial beim Zahn,
Ganzkörperplethysmographie, Sechs-Minuten-Gehtest und den normothermen Einsatz
der Herz-Lungen-Maschine. Die Bedarfsabklärung wird ab einem belegten Alter von
65 Jahren als geriatrisches Minimalassessment in drei Bereichen konkretisiert.
Kurze psychologische Screenings werden nicht pauschal zur mindestens
60-minütigen Diagnostik umbenannt. Überweisungen, Routineuntersuchungen und
Maßnahmen ohne belastbare OPS-Zuordnung bleiben als SNOMED erhalten.

15 nichtprozedurale Quellkonzepte werden als passende synthetische Handlungen
konkretisiert. Die US-zahnärztliche Nachsorge verwendet einen internationalen
Oberbegriff. Das US-spezifische Comprehensive Metabolic Panel entfällt als
zusätzliche Prozedur; vorhandene Laborwerte und Befundberichte bleiben erhalten.
Jede Auslassung steht mit Ressourcen-ID im Verlustbericht. Details und Quellen
stehen in `scripts/mappings/synthea-procedures-ops-2026.json`. Der Laufbericht
verknüpft jede erzeugte Prozedur mit ihren Quell-IDs und ihrer Excel-Zeile.
[OPS-2026-Regeln zu Chemotherapie und Bestrahlung](https://klassifikationen.bfarm.de/ops/kode-suche/htmlops2026/block-8-52...8-54.htm)
und [Funktionstests](https://klassifikationen.bfarm.de/ops/kode-suche/htmlops2026/block-1-70...1-79.htm)
liegen diesen Konkretisierungen zugrunde.

Medikationsabgleich bleibt mit SNOMED erhalten: Ein passender eigenständiger
OPS 2026 wurde nicht gefunden. Auch der weitergehende Medikationsanalyse-OPS
liegt als [Vorschlag für 2027](https://multimedia.gsb.bund.de/BfArM/downloads/klassifikationen/ops/vorschlaege/vorschlaege2027/ops2027-059-medikationsanalysen.pdf) vor, nicht als gültiger Code.
Das KDS-Profil erlaubt OPS oder SNOMED; Routineleistungen benötigen daher
nicht automatisch einen OPS. Die SNOMED-Bindung verlangt Prozedurbegriffe.
Die Entscheidungen ersetzen keine vollständige Terminologievalidierung oder
menschliche Prüfung der synthetischen Szenarien.
Das Quellregister enthält ausschließlich Quellfakten. Aktuelle Entscheidungen
stehen in den jeweiligen Mappingtabellen und werden nicht im Register dupliziert.

## Ressourcen und Eigenschaften

| Ressource | Excel | Übernommener Umfang | Wesentliche verbleibende Details |
| --- | --- | --- | --- |
| Patient | Person | Synthetische deutsche Namen/Anschrift; Geburt, Geschlecht, Sterbezeitpunkt aus Quelle | Wohnhistorie, Kommunikation und weitere demografische Erweiterungen |
| Encounter | Fall | Klasse, Zeitraum, Patientbezug, explizite Notfallkennzeichnung | Einrichtung/Behandler, Gründe, Entlassungsdisposition |
| Condition | Diagnose | ICD-10-GM zuerst, SNOMED ergänzend bzw. begründeter Rückfall, drei Zeitangaben, Status, Kontakt | Weitere Synthea-Metadaten |
| Procedure | Prozedur | OPS mit Einzelbeschreibung bzw. SNOMED, Zusatzcode bei Einzelzuordnung, regionale Aufteilungen und Therapieblöcke, Zeitraum, Status, Kategorie, Patient/Kontakt | Gründe, Körperstelle, Behandler; synthetische Annahmen im Bericht |
| Observation | Laborbefund / Klinische Dokumentation | Zahl, Text, Code, Boolean, DAR, Komponenten, Kategorie, Status, effective/issued, UCUM-Code, Patient/Kontakt | Mehr als zwei Untersuchungscodings und nicht dargestellte Zusatzattribute; unsupported value[x] wird ausdrücklich ausgelassen |
| MedicationRequest / Administration | Medikation | Deutsches Präparat bzw. sichtbar offene Zuordnung, Status, Zeitpunkt/Verabreichungszeitraum, Verordnungsabsicht, Text, erste Dosis, einfache Tagesfrequenz | Weitere Dosen/Raten, Routen, Zeitpläne, Gründe und Behandler |
| Medication | Aus Medikationszeilen | Getrennte Definition je vollständiger Präparatbeschreibung; referenzierte Ressourcen werden aufgelöst | Konkrete Packungsstärken sind nicht vollständig strukturiert; unbekannte Konzepte außerhalb des Inventars bleiben sichtbar offen |
| AllergyIntolerance | Bewusst ausgeschlossen | Jede Auslassung im Verlustbericht; Originalquelle bleibt erhalten | IPS-konforme Unterstützung zurückgestellt |
| Immunization | Impfung | ATC 2026, deutscher Impfstofftext, Zeitpunkt, Status, Primärquellenangabe, Patient/Kontakt | Durchführungsort und weitere Impfdetails |
| DiagnosticReport | Befundbericht | Erstes Coding, Zeitpunkt, Ausgabezeit, Status, auflösbare Messwertverweise, vorhandene conclusion | Kategorien, Behandler, zusätzliche Codings; eingebettete Notizen stehen in DocumentReference |
| CarePlan | Behandlungsplan | Klinische SNOMED-Kategorie, Zeitraum, Status, Absicht, Beschreibung, Aktivitätscodes | Aktivitätsdetails/Status (explizit unknown), Ziele, Behandlerteam und Diagnoseverweise |
| Device | Ausgeschlossen | Auslassung mit Ressourcen-ID im Verlustbericht | Geräte und Hilfsmittel |
| DocumentReference | DocumentReference | Erster Dokumenttyp, Status, Datum, erster Kontakt und eingebetteter UTF-8-Klartext | Weitere Dokumentmetadaten und andere Anhangsformate; Excel-Grenze 32.767 Zeichen |
| ImagingStudy / SupplyDelivery / CareTeam | Noch kein Import | Im Quelleninventar und Verlustbericht ausgewiesen | Bildgebungsserien/-instanzen, Lieferereignisse und Organisations-/Behandlerbeziehungen |
| Claim / ExplanationOfBenefit / Provenance | Kein klinisches Blatt | Originaldatei bleibt erhalten; Auslassung im Bericht | US-Abrechnung und ursprüngliche Exportprovenienz werden nicht als klinische Daten umgedeutet |

Organisationen, Orte und Behandler, die nur als externe Suchreferenzen auftreten,
werden nicht durch erfundene deutsche Einrichtungen ersetzt. Es gibt keinen
versteckten Roh-FHIR-Tab als Ersatz für eine bearbeitbare klinische Eingabe.

## Excel vorher und nachher

Bestehende Zellen und Formatierungen werden mit LibreOffice/UNO weiterverwendet.
Neue Spalten stehen vor der bisherigen Ausfüllhilfe. Vorhandene Beispieldaten
wurden erhalten. Auswahllisten stehen im Blatt **Codes**, Bereiche AB bis BF;
vorhandene Diagnose- und Notfalllisten bleiben unverändert. Die Auswahl ist
[je Eingabespalte abgegrenzt](clinical-selections.md).

| Blatt | Vorher | Ergänzung |
| --- | --- | --- |
| Person | Freitext-Anschrift, kein Sterbezeitpunkt | Ausschließlich Straße, Postleitzahl, Ort, Bundesland, Land; zusätzlich Sterbezeitpunkt. Alte Anschrift-Spalte entfernt. |
| Prozedur | Implizites OPS, einzelner Zeitpunkt, immer completed | Explizites Codesystem, Zusatzcoding, Ende, Status, Kategorie |
| Laborbefund / Klinische Dokumentation | Nur numerischer LOINC-Messwert; beide als Labor ausgegeben | Werttyp, codierte Antworten, echte Kategorie, Status, Untersuchung ID, Komponente von, Ausgabezeitpunkt, UCUM-Einheitencode, Codesystem |
| Medikation | PZN/ATC, feste Statuswerte, Dosis und Häufigkeit vermischt | Original-Präparatcode/-system, Status, Absicht, Dosierungstext, Ende, Wirkstoffcode/-system; Menge und Häufigkeit getrennt |
| DocumentReference | Nur Dateipfad und Embed | Eingebetteter Klartext, Status, Datum und Dokumenttyp |
| Neue Blätter | Nicht vorhanden | Impfung, Befundbericht, Behandlungsplan |

Komponenten stehen direkt unter ihrer Hauptzeile. `Komponente von` verweist auf
`Untersuchung ID`; die Hauptzeile muss zuerst stehen. Daraus entsteht eine
Observation mit Komponenten, keine zusätzliche Observation pro Teilmessung.
Befundberichte verwenden dieselben IDs für Ergebnisverweise. IDs werden beim
FHIR-Export patientenbezogen und mit zulässiger Länge erzeugt.

Leere neue optionale Felder werden ausgelassen. `!dar:<reason>` ist eine
explizite Angabe. Für fehlende Messwerte wird zusätzlich Werttyp `Fehlend`
gewählt. Statuswerte werden über die zentralen Listen ausgewählt; in den neuen
klinischen Blättern stehen derzeit die FHIR-Statusbezeichnungen.

Größere Fälle erweitern den vorbereiteten Bereich durch Kopieren vorhandener
Zeilenformate. Zellwerte werden als Text gespeichert, damit Codes, IDs und
FHIR-Zeitangaben unverändert bleiben. Die CSV-Zwischenstufe erhält inzwischen
auch Zeilenumbrüche, Anführungszeichen und äußere Leerzeichen durch standardkonformes
CSV-Quoting. Sie normalisiert Dokumenttexte nicht auf eine einzige Zeile.

## Ausführen und prüfen

```sh
mvn test package
python3 -m unittest discover -s scripts/tests
python3 scripts/synthea_to_excel.py PATIENT.json FALL.xlsx
java -jar target/excel2fhir.jar -f FALL.xlsx -o FHIR_VERZEICHNIS -t CSV_VERZEICHNIS
python3 scripts/check_synthea_roundtrip.py PATIENT.json FHIR_BUNDLE.json FALL.loss.json
```

Für alle vorhandenen Patienten mit einem neuen Ausgabeverzeichnis:

```sh
python3 scripts/run_synthea_cases.py SYNTHEA_FHIR_VERZEICHNIS NEUES_AUSGABEVERZEICHNIS
```

Der Sammellauf führt jetzt auch die FHIR-Validierung aus und sammelt Import-,
Verlust- und Validierungsberichte. Installation, Container, Status und Exitcodes
sind im [Gesamtworkflow](synthea-workflow.md) beschrieben.

Der Rückvergleich prüft den unterstützten Umfang, Ressourcenanzahlen,
Originalcodings und erwartete ICD-Ergänzungen, Messwerte einschließlich
Komponenten, Prozedurzeiten/-status, Präparatdefinitionen, Dokumenttexte sowie
auflösbare Referenzen. Eine erfolgreiche Prüfung bedeutet keine vollständige
Übernahme aller Eigenschaften. Teilübernahmen und ausgelassene Ressourcen
stehen im jeweiligen `Fall.loss.json`.

Die neuen Ereignisressourcen beanspruchen zunächst FHIR-R4-Basisunterstützung.
Labor, Prozeduren und Medikation verwenden die vorhandenen KDS-Profile.
Eine vollständige KDS-/Terminologievalidierung bleibt getrennt vom Rückvergleich;
insbesondere die benötigte SNOMED-Ausgabe ist lokal weiterhin nicht verfügbar.

Die zusätzliche [Bewegungsanreicherung](synthea-movements.md) ergänzt Kontakte
mit automatisch abgeleiteten Elternbeziehungen; die folgenden Zahlen beschreiben den
ursprünglichen Durchlauf vor dieser Anreicherung.

Die [Deutschland-Anpassung der Personendaten](synthea-german-demographics.md)
ersetzt Namen und Anschriften sowie eindeutige generierte Namen in Dokumenten.
Der Rückvergleich prüft diese bewussten Änderungen anhand des versionierten
Vorrats. Die übrigen Dokumenttexte sind weiterhin in der Quellsprache.

## Geprüfter Bestand vom 12. September 2026

Alle 18 vorhandenen Patientenfälle wurden durch Excel und den Konverter geführt
und anschließend gegen ihre Quelle geprüft: 2.573 Diagnosen, 3.574 Kontakte,
35.836 Messwertressourcen, 13.475 Prozeduren, 2.226 Verordnungen,
328 Verabreichungen, 1.065 Impfungen, 7.619 Befundberichte, 243 Behandlungspläne,
475 Geräte, 18 Allergien und 3.574 Klartextdokumente (historischer Stand vor dem Allergie-Rückbau). Die Diagnoseergänzung
lieferte 1.595 ICD-10-GM-Codings; 148 Notfallkontakte erhielten die vorgesehene
Kennzeichnung. Komponenten erklären die höhere Anzahl von Excel-Messwertzeilen.

Ein großer Fall überschritt zunächst die Zeitgrenze des zellweisen UNO-Schreibers.
Nach Umstellung auf zeilenweises Schreiben bestand auch dieser Fall den erneuten
Rückvergleich. Alle 18 Ergebnisse wurden danach nochmals geprüft. Zusätzlich
bestanden 36 Java-Tests und 22 Python-Tests. Die Prüfungen ersetzen keine
vollständige Profil- und Terminologievalidierung.

Die Sichtprüfung zeigt noch breite Blätter und teilweise abgeschnittene lange
IDs, Bezeichnungen und Zeitangaben. Die vorhandene Formatierung wurde bewusst
weiterverwendet; eine bessere Darstellung dieser Inhalte gehört zum menschlichen
Review der gefüllten Arbeitsmappen.

## Bewusst vorgesehene menschliche Prüfung

- Gefüllte Fälle: Verständlichkeit der Spalten, Blattaufteilung, horizontales
  Scrollen, lesbare Darstellung von Codes, IDs, Zeitangaben und Dokumenttexten.
- Neue Statuslisten: FHIR-Bezeichnungen oder zusätzliche deutsche Beschriftungen.
- Medikationsdarstellung: zusätzliche Originalcode-Spalten neben PZN/ATC sowie
  die spätere Auswahl deutscher Präparate und genauer Dosierungsschemata.
- Fachliche Prüfung der OPS-Konkretisierungen, Aufteilungen und Therapieblöcke.
- Priorität der noch fehlenden Bildgebungs-, Liefer- und Behandlerdetails.

## Nachprüfung der neuen Populationen

Der unabhängige Abgleich `audit_synthea_projection.py` liest Originalquellen,
Excel und Zielbundle ohne Aufruf der Importfunktionen. Er prüft Ereigniszahlen,
Medikationsmengen/-frequenzen/-zeitpunkte, numerische und codierte Messwerte,
Komponenten, deutsche Versionsangaben und die gewünschte Coding-Reihenfolge.
Für Prozeduren prüft er zusätzlich regionale Aufteilungen, Therapieblöcke,
Wirkstoffzählung, Zeitfenster, Einzelbeschreibungen und die zugehörigen Excel-Zeilen.
Explizit ausgeschlossene Allergien werden separat bilanziert. Das ersetzt keine
medizinische Äquivalenzprüfung der redaktionellen Zuordnungen.

Ein nachgewiesenes US-Konzept für zahnärztliche Nachsorge wird durch seinen
internationalen SNOMED-Oberbegriff ersetzt. Das zahnärztliche Detail bleibt im
Text. Andere ursprünglich in US-Namespaces erzeugte Konzepte dürfen zur
internationalen Edition gehören; die ID allein ist kein Ausschlusskriterium.
Die gezielte Prüfung verwendet explizite Editionsstände, keinen behaupteten
vollständigen aktuellen SNOMED-Validierungsnachweis.

Synthea liefert bei LogMAR-Sehschärfe zusätzlich LOINC 98498-9/98499-7 für
ein nicht logarithmisches Längenverhältnis. Bei exakt erkanntem Quellkonzept
und LogMAR-Einheit werden stattdessen 6617-5 (links) bzw. 6616-7 (rechts)
vor SNOMED ausgegeben. Werte bleiben unverändert; es wird keine Messtafel oder
bestmögliche Korrektur unterstellt. Quellen und verworfene Zusatzcodings stehen
im Mappingbericht. Diese redaktionelle Entscheidung braucht menschlichen Review.
LOINC-Codes und Namen: Copyright Regenstrief Institute, Inc.;
[LOINC-Lizenz](https://loinc.org/license). Bestehender deutscher Quelllesetext
ist keine als offiziell verifiziert ausgegebene deutsche LOINC-Übersetzung.
