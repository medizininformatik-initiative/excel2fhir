# Aufenthalte und zusätzliche Kontakte

Im Blatt **Fall** reichen Fallnummer, Zeitraum, Fachabteilung und Ortsangaben.
Kontakt-IDs, Kontaktebenen und Elternverknüpfungen erzeugt der Converter selbst.

## Eingabe

- Erste Fallzeile: Patient-ID, Fall-Nr, Start, optional Ende und Einrichtungskontaktklasse.
- Fachabteilung angegeben: Abteilungskontakt. Wiederholungen derselben Abteilung
  und Ortswechsel ohne neue Fachabteilungsangabe führen den vorhandenen Abteilungskontakt fort.
  Ohne vorherige oder angegebene Fachabteilung wird kein Abteilungskontakt erfunden.
- Mindestens Station, Zimmer oder Bett angegeben: Versorgungsstellenkontakt.
  Nur ausgefüllte Orte werden erzeugt. Ohne Abteilung verweist der Kontakt direkt
  auf den Einrichtungskontakt. Ein einzelnes Bett braucht keine erfundene Station.
- Kontaktart leer, Normalstationär oder Intensivstationär: primärer Aufenthalt.
- Kontaktart Operation, Untersuchung und Behandlung oder Konsil: zusätzlicher
  sekundärer Versorgungsstellenkontakt, der den primären Stationskontakt nicht verändert.
  Auch hier ist mindestens eine Ortsangabe nötig.

Primäre Aufenthalte stehen zeitlich geordnet. Die zugehörigen Sekundärkontakte
stehen jeweils unmittelbar nach ihrer primären Zeile und vor der nächsten
primären Verlegung. Fallnummer und Klasse dürfen wiederholt werden; eine leere
Fallnummer führt den aktuellen Fall fort. Die Klasse muss innerhalb des Falls gleich bleiben.
Eine Fachabteilung auf einer Sekundärzeile beschreibt die ausführende Fachrichtung
und erzeugt keine primäre Verlegung.

Die konkreten aktuellen Ablehnungen und erlaubten Kombinationen stehen unter
[Kontakt-Eingabeprüfungen](contact-input-checks.md).

## Zeiträume

Ein ausdrücklich vorhandenes sekundäres Kontaktende bleibt erhalten. Fehlt es,
wird das Ende des zugehörigen primären Aufenthalts verwendet. Ein primärer
Aufenthalt ohne Ende endet spätestens am bekannten Ende des Einrichtungskontakts
oder am Beginn des nächsten primären Aufenthalts. Sind beide unbekannt, bleibt
er offen, ebenso seine Sekundärkontakte. Ein weiterer Sekundärkontakt schließt
keinen vorherigen Kontakt. Abgeleitete Enden stehen im Importbericht unter
`contactEndDerivations`, einschließlich Referenz und Ursprungszeile.

Eine reine Einrichtungszeile ohne Abteilung/Ort gibt den Zeitraum des gesamten
Falls vor. Untergeordnete Kontakte dürfen ihn nicht überschreiten.
Enthält die erste Fallzeile Abteilung/Ort, beschreibt sie zugleich den ersten
Aufenthalt; folgende primäre
Aufenthalte erweitern dann den Einrichtungszeitraum. Sekundärkontakte tun das nie.

Beispiel: Station A, Zimmer 12, Bett 2 vom 1.–3. Mai; zusätzlicher OP-Kontakt
am 2. Mai ohne Ende; echte Verlegung auf Intensivstation am 3. Mai.
Der OP-Kontakt endet am 3. Mai. Das Bett auf Station bleibt bis dahin erhalten.
Ein Ende der Operation als Prozedur wird daraus **nicht** abgeleitet.

So kann dieser Fall im Blatt stehen (Patient-ID in jeder Zeile gleich; leere
Felder bleiben tatsächlich leer):

| Fall-Nr | Start | Ende | Einrichtungskontaktklasse | Fachabteilung | Station | Zimmer | Bett | Kontaktart |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1001 | 2026-05-01 08:00 | 2026-05-05 12:00 | stationaer | | | | | |
| 1001 | 2026-05-01 08:00 | | | | Station A | 12 | 2 | Normalstationär |
| 1001 | 2026-05-02 10:00 | | | | OP | Saal 1 | | Operation |
| 1001 | 2026-05-03 09:00 | | | | Intensivstation | 1 | 1 | Intensivstationär |

Die erste Zeile legt den Gesamtfall fest. Station A und der zusätzliche OP-Kontakt
enden mit dem Beginn des Intensivaufenthalts am 3. Mai um 09:00. Der Intensivkontakt
endet mit dem Gesamtfall. Weil hier keine Fachabteilung angegeben ist, entsteht
kein Abteilungskontakt. Der Beginn des OP-Kontakts steht ausdrücklich in dessen
eigener Zeile; eine manuelle Eingabe braucht dafür keine passende Prozedurzeile.
Die separate Einrichtungszeile ist hilfreich für einen ausdrücklich festgelegten
Gesamtzeitraum, aber nach der oben beschriebenen CSV-Konvention nicht zwingend.

Unsortierte oder überlappende primäre Aufenthalte, Sekundärkontakte ohne primären
Aufenthalt und unpassende Zeiträume werden als Eingabefehler gemeldet.
Die Überlappung zwischen primären und sekundären Kontakten ist dagegen beabsichtigt.

## Synthea-Anreicherung

`scripts/synthea_movements.py` erzeugt reproduzierbare primäre Bewegungen innerhalb
abgeschlossener Quellkontakte. Seed, Regelversion und Annahmen stehen im Bericht.
Abteilungs-/Zimmer-/Bettwechsel und mögliche Intensivphasen sind synthetische
Testdatenannahmen, keine statistisch kalibrierten Krankenhausverläufe.
Offene Quellfälle bleiben unverändert und werden als nicht angereichert gemeldet.

Die vorhandene operative SNOMED-Teilmenge aus `synthea-operative-procedures.json`
löst zusätzliche OP-Zeilen aus. Der Prozedurbeginn dient als synthetischer
Kontaktbeginn; ein OP-Kontaktende ist nicht bekannt und bleibt in Excel leer.
Vor-/Nachbereitungszeiten und automatische Rückverlegungen wegen einer OP entfallen.
Vorhandene Prozedurzeiten bleiben vollständig unverändert. Der Bericht enthält
Quellprozeduren und das aus dem primären Aufenthalt erwartete Kontaktende.
Ambulante operative Kontakte bleiben ambulant; das macht den begleitenden
primären Ortskontakt nicht zu einem sekundären Untersuchungs-/Behandlungskontakt.

## Grundlage und Umstellung

Das eingebundene KDS-Basisprofil 2026.0.1 erlaubt unter `Encounter.partOf` eine
optionale Encounter-Referenz; eine fehlende Zwischenebene muss nicht erfunden werden.
Kontaktart und Kontaktebene verwenden `http://fhir.de/CodeSystem/kontaktart-de`
und `http://fhir.de/CodeSystem/Kontaktebene`.
[Fall-Leitfaden](https://medizininformatik-initiative.github.io/kerndatensatz-basis/de/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.html).

Die drei technischen Kontaktspalten wurden aus beiden Vorlagen entfernt.
Alte CSV-/Excel-Fälle ohne diese Angaben bleiben lesbar. Bereits erzeugte Fälle
mit befüllten technischen Kontaktspalten müssen neu aus Synthea erzeugt oder
fachlich in die neue Eingabe überführt werden. Der Converter lehnt ihre stille
Umdeutung ab. `simplify_contact_template.py` aktualisiert ausschließlich Vorlagen
mit leeren technischen Kontaktspalten über LibreOffice/UNO; kein automatischer
Konverter für explizite Patientenhierarchien.
