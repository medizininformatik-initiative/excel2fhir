# Deutsche synthetische Personendaten

Der Synthea-Import verwendet standardmäßig `german-demographics-v2`. Die
bestehenden klinischen Daten werden mit einer neuen synthetischen Identität
kombiniert. Die Quell-Bundles bleiben unverändert. Dieser Schritt ist keine
vollständige Anpassung an das deutsche Gesundheitswesen und keine
Anonymisierungsfunktion für echte Patientendaten.

## Excel vorher und nachher

| Bereich | Vorher (vorhandener Tad-Fall) | Nachher |
| --- | --- | --- |
| Person: Vorname / Nachname | Tad831 Brady998 / Lesch175 | Gisbert / Textor |
| Person: Straße | 348 Ankunding Trafficway Suite 18 | Musterstraße 7 |
| Person: PLZ / Ort / Bundesland / Land | 01905 / Lynn / MA / US | 20095 / Hamburg / Hamburg / DE |
| Dokumenttext | `Tad831 is a newborn …` | `Gisbert is a newborn …` |
| Geburt, Geschlecht, Tod, klinische Daten | Quellwerte | Erhalten |

Es ändern sich Zellwerte in vorhandenen Spalten. Keine neuen Blätter, Spalten,
Dropdowns oder Layoutänderungen. Versicherungs- und Einwilligungsfelder bleiben
leer. Die bestehenden Kontakt- und Ortsanreicherungen bleiben erhalten.

## Regeln und Herkunft

`scripts/mappings/german-demographics.json` enthält **2.006 unterschiedliche
Vornamen und 1.349 Nachnamen**, also **2.706.094 mögliche vollständige Namen**.
Davon stehen 998 Vornamen im männlichen und 1.005 im weiblichen Vorrat.
Unbekanntes/anderes Geschlecht nutzt den gesamten Vornamensvorrat, statt die
Auswahl auf wenige als neutral eingestufte Namen zu begrenzen.

Die öffentlichen Faker-Listen (MIT) wurden auf Commit
`59a6872a547248bb40cdd0f809089a605c68467f` festgeschrieben:

- [de_DE: Vornamen und Nachnamen](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/de_DE/__init__.py)
- [de_AT: ergänzende Nachnamen](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/de_AT/__init__.py)
- [tr_TR: ergänzende Nachnamen](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/tr_TR/__init__.py)

Die bisherigen redaktionellen Ergänzungen bleiben erhalten. Die originale
MIT-Lizenz liegt unter `third-party/faker/LICENSE.txt`; Herkunft, genutzte Felder
und SHA-256-Prüfsummen stehen zusätzlich in `nameSources` der Datendatei.
Keine zusätzliche Laufzeitabhängigkeit und keine Netzabfrage beim Erzeugen.

Der Builder normalisiert Unicode nach NFC und Leerraum, entfernt Dubletten ohne
Beachtung der Groß-/Kleinschreibung und lässt Abkürzungen mit Punkt aus.
Tatsächliche Varianten wie Sara/Sarah oder Müller/Muller bleiben erhalten;
sie sind nicht automatisch dieselbe Person oder derselbe Name. Der große Vorrat
ist keine fachliche Prüfung jeder einzelnen Namenszuordnung des Upstreams.

Reproduktion: die drei verlinkten Quelldateien als `de_DE.py`, `de_AT.py`,
`tr_TR.py` und die [Lizenz](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/LICENSE.txt)
als `LICENSE.txt` lokal speichern, dann:

```sh
python3 scripts/build_german_name_pool.py QUELLVERZEICHNIS
```

Die Prüfsummen werden vor Verarbeitung geprüft. Der Builder liest Python-Literale
über AST und führt keinen heruntergeladenen Code aus. Der erneute Aufbau wurde
auf bytegleiche Ausgabe geprüft.

 Vor- und Nachnamen werden unabhängig gewählt;
aus den US-Angaben zu Race/Ethnicity wird nichts abgeleitet. Das administrative
Geschlecht wählt den Vornamensvorrat; `other` und `unknown` nutzen denselben
vollständigen Vorrat. Es gibt keine Alterskohorten, Familienbeziehungen oder
bevölkerungsstatistischen Häufigkeitsgewichte. Namensgleichheit ist möglich;
die Patient-ID bleibt das Unterscheidungsmerkmal.

SHA-256 über Version, Patient-ID und getrennte Auswahlzwecke bestimmt Namen,
Ort, Straße und Hausnummer. Für Adressen bleibt der Seed-Namensraum von v1
erhalten, damit die Namenserweiterung keine Anschriften verändert. Ergebnisse sind unabhängig von Reihenfolge und
Gesamtzahl der Fälle. Änderungen am Vorrat erfordern eine neue Version.
Der Bericht enthält außerdem die Prüfsumme der Datendatei und die neue Identität.

PLZ/Ort-Paare wurden am 13.09.2026 anhand der Stadtportale geprüft:
[Berlin](https://www.berlin.de/sehenswuerdigkeiten/3559880-3558930-rotes-rathaus.html),
[Hamburg](https://www.hamburg.de/service/info/11297145/),
[München](https://stadt.muenchen.de/service/info/stadtverwaltung/10264283/) und
[Köln](https://www.koeln.de/apps/strassen/rathausplatz).
Diese Quellen belegen die Orts-/PLZ-Zuordnung, nicht die erfundenen Straßen.
Die Anschriften sind nicht als zustellbare Adressen gedacht; es werden keine
realen Haushalte verwendet. Eine einzelne Anschrift stellt keine Wohnhistorie dar.

Dokumenttexte erhalten nur gezielte Namensersetzungen: vollständige Quellnamen
und eindeutig durch Ziffernsuffix gekennzeichnete Synthea-Namensbestandteile.
Isolierte normale Wörter wie `May` oder `Brown` bleiben erhalten, um Monate und
klinische Begriffe nicht zu verändern. Ersetzungen erfolgen an Wortgrenzen,
in einem Durchgang und mit protokollierter Anzahl sowie Quell-/Zieltexthashes.
Dies ist keine allgemeine Garantie, jede Identitätsangabe im Freitext zu finden.

**Weiterhin offen:** englische Fachbezeichnungen und Dokumenttexte einschließlich
US-Versicherungen/Sozialbeschreibungen, nationale Produkt-/OPS-Mappings und weitere
Versorgungselemente. Der Bericht kennzeichnet die Textübersetzung als `deferred`.
Eine deutsche Neugenerierung aus strukturierten Daten wäre eine synthetische
Zusammenfassung; sie darf nicht als vollständige Übersetzung ausgegeben werden.

## Prüfung und späterer Nutzerreview

33 Python- und 39 Java-Tests bestanden. Unter v2 haben die 18 vorhandenen Fälle
weder doppelte vollständige Namen noch wiederholte Vor- oder Nachnamen. Dies ist
ein Ergebnis dieses Bestands, keine Eindeutigkeitsgarantie für beliebige größere
Populationen. Die Hash-Auswahl hat weiterhin keine Kollisionsauflösung.
Die drei Review-Fälle wurden mit v2 erneut vollständig durch Excel/CSV/FHIR
geprüft. Gegenüber v1 ändern sich ausschließlich Vor-/Nachnamen und betroffene
Dokumenttexte; Adressen, klinische Werte und andere Blattinhalte bleiben gleich.

Vorheriger Stand v1: 30 Python- und 39 Java-Tests bestanden. Alle 18 vorhandenen Fälle wurden neu
vorbereitet: 3.574 Dokumente, darin 4.261 Namensersetzungen. Die drei vorhandenen
Bewegungsbeispiele wurden vollständig durch LibreOffice, CSV und FHIR geführt;
Patientendaten, klinische Werte, Dokumenttextänderungen und Bewegungen bestanden
den Rückvergleich. Vollständige Terminologievalidierung bleibt gesondert offen.

Für den menschlichen Review:

- Passen Vielfalt und Kombinationen der Namen? Sind Alterskohorten später nötig?
- Sollen Straßen sichtbar Testdaten bleiben oder später unauffälliger wirken?
- Reichen später deutsche Zusammenfassungen aus strukturierten Informationen,
  oder werden zusätzliche Inhalte aus den englischen Freitexten benötigt?
- Bereits offen: Dropdowns, Blatt-/Spaltenaufteilung, enge Zeitspalten,
  Kontaktvarianz und OP-/Intensivannahmen.

Diese Punkte blockieren die technische Weiterentwicklung nicht.
