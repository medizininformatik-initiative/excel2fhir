# Deutsche synthetische Personendaten

Der Synthea-Import verwendet standardmäßig `german-demographics-v1`. Die
bestehenden klinischen Daten werden mit einer neuen synthetischen Identität
kombiniert. Die Quell-Bundles bleiben unverändert. Dieser Schritt ist keine
vollständige Anpassung an das deutsche Gesundheitswesen und keine
Anonymisierungsfunktion für echte Patientendaten.

## Excel vorher und nachher

| Bereich | Vorher (vorhandener Tad-Fall) | Nachher |
| --- | --- | --- |
| Person: Vorname / Nachname | Tad831 Brady998 / Lesch175 | Jonas / Rossi |
| Person: Straße | 348 Ankunding Trafficway Suite 18 | Musterstraße 7 |
| Person: PLZ / Ort / Bundesland / Land | 01905 / Lynn / MA / US | 20095 / Hamburg / Hamburg / DE |
| Dokumenttext | `Tad831 is a newborn …` | `Jonas is a newborn …` |
| Geburt, Geschlecht, Tod, klinische Daten | Quellwerte | Erhalten |

Es ändern sich Zellwerte in vorhandenen Spalten. Keine neuen Blätter, Spalten,
Dropdowns oder Layoutänderungen. Versicherungs- und Einwilligungsfelder bleiben
leer. Die bestehenden Kontakt- und Ortsanreicherungen bleiben erhalten.

## Regeln und Herkunft

`scripts/mappings/german-demographics.json` enthält einen kleinen, redaktionell
zusammengestellten Namensvorrat. Vor- und Nachnamen werden unabhängig gewählt;
aus den US-Angaben zu Race/Ethnicity wird nichts abgeleitet. Das administrative
Geschlecht wählt den Vornamensvorrat; `other` und `unknown` nutzen denselben
neutralen Vorrat. Es gibt keine Alterskohorten, Familienbeziehungen oder
bevölkerungsstatistischen Häufigkeitsgewichte. Namensgleichheit ist möglich;
die Patient-ID bleibt das Unterscheidungsmerkmal.

SHA-256 über Version, Patient-ID und getrennte Auswahlzwecke bestimmt Namen,
Ort, Straße und Hausnummer. Ergebnisse sind unabhängig von Reihenfolge und
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

30 Python- und 39 Java-Tests bestanden. Alle 18 vorhandenen Fälle wurden neu
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
