# Kontaktangaben: Was wird angenommen, was wird abgelehnt?

Die Kontaktart beschreibt den Versorgungsstellenkontakt einer Fallzeile. Station,
Zimmer und Bett sind seine Ortsangaben. Eine Operation mit angegebenem Bett ist
zulässig und erzeugt einen OP-Kontakt mit diesem Bett. Sie beendet nicht automatisch
den vorherigen Stationskontakt. Ein Konsil kann am Stationsbett stattfinden.
Ortsnamen lösen keine automatische fachliche Klassifikation oder Verlegung aus.

Diese Übersicht beschreibt die aktuell implementierten Regeln für das Blatt
**Fall** und den Kontakt-Converter. Sie ist keine Behauptung, alle Eingabeprüfungen
der anderen Blätter oder sämtliche KDS-Profilregeln abzudecken.

## Erlaubte Kombinationen

- Operation mit Bett; Konsil mit Station, Zimmer und Bett.
- Sekundärkontakt gleichzeitig mit dem primären Aufenthalt oder anderen Sekundärkontakten.
- Ortskontakt ohne Fachabteilung, auch nur mit Zimmer oder nur mit Bett.
- Offenes Ende. Ohne ableitbares Ende bleiben Kontakt und nachgeordnete Sekundärkontakte offen.
- Fachlich ungewöhnliche Kombinationen aus Kontaktklasse und Kontaktart werden
  nicht zusätzlich gesperrt, beispielsweise ambulant mit Normalstationär.
  Diese Annahme beim Import ist keine Bestätigung fachlicher oder Profilkonformität.

## Aktuelle Ablehnungen

| Eingabe | Aktuelles Verhalten und Grund |
| --- | --- |
| Fehlender oder nicht lesbarer Beginn; nicht lesbares Ende | Fehler: kein verarbeitbarer Zeitwert. |
| Ende vor Beginn | Fehler in Excel-Vorprüfung und Kontakt-Converter; die Werte wären grundsätzlich darstellbar. |
| Ende gleich Beginn | Excel-Vorprüfung meldet Fehler; der direkte Kontakt-Converter erlaubt das für eine einzelne Zeile. Diese Inkonsistenz ist offen. |
| Kind beginnt vor dem Elternkontakt oder beginnt an/nach dessen bekanntem Ende; Kind endet nach dem Elternende | Fehler im Kontakt-Converter. Das ist eine zeitliche Konsistenzregel, keine technische Unmöglichkeit der FHIR-Darstellung. |
| Überlappende oder rückwärts sortierte primäre Aufenthalte | Fehler im Kontakt-Converter. Explizite Überlappungen sind darstellbar; die zeitliche Zuordnung bei automatisch abgeleiteten Enden benötigt eine eindeutige Regel. |
| Sekundärkontakt ohne zuvor eingetragenen primären Versorgungsstellenkontakt | Fehler: die aktuelle implizite Eingabe verlangt diese Zuordnung. Das ist eine Grenze dieses Eingabemodells. |
| Kontaktart ausgefüllt, aber Station/Zimmer/Bett sämtlich leer | Fehler: die vereinbarte Ableitung erzeugt ohne Ortsangabe keinen Versorgungsstellenkontakt. |
| Unbekannte Kontaktart | Fehler: kein bekanntes Mapping für diese Auswahl. |
| Unterschiedliche Einrichtungskontaktklasse in Zeilen desselben Falls | Fehler: der Converter übernimmt derzeit eine gemeinsame Klasse vom Einrichtungskontakt. |
| Aufnahmegrund auf einer Folgezeile | Fehler im Kontakt-Converter: keine automatische Entscheidung zwischen mehreren Einrichtungsangaben. |
| Erster Kontakt ohne zuordenbare Fallnummer; unbekannter Patient | Fehler: die notwendige Zuordnung fehlt. |
| Befüllte alte technische Kontaktspalten | Fehler: kein stilles Umdeuten der früheren expliziten Hierarchie. |

Die zeitlichen Konsistenzprüfungen sollen gesondert entschieden werden. Explizite
widersprüchliche Testdaten sind nicht automatisch Eingabefehler, die einen Import
verhindern müssen. Eine Lockerung ist hier noch nicht als implementiert beschrieben.
Es gibt keine neue Plausibilitätsoption und keine neue Sperre für Operation plus Bett.

## Import und FHIR-Validierung unterscheiden

Eine akzeptierte Eingabe kann FHIR-Profilregeln verletzen. Der optionale
FHIR-Validator meldet solche Befunde im Validierungsbericht. Ein bereits erzeugtes
Bundle wird dadurch nicht nachträglich fachlich korrigiert. Vollständiger Import
und gültiges FHIR sind unterschiedliche Aussagen, siehe [Importbilanz](import-report.md).
