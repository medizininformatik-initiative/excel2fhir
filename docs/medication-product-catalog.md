# Deutsche Medikamentendaten mit und ohne lokalen Produktkatalog

## Öffentlicher Synthea-Standard (ATC 2026)

`scripts/mappings/synthea-medications-de-2026.json` dokumentiert alle 495 RxNorm-Konzepte
des gepinnten Quellregisters. Alle 495 besitzen eine deutsche ATC-Zuordnung für 2026 und öffentlich belegte
UNII-Wirkstoffschlüssel sowie eine ausgewählte echte deutsche PZN.
Dies sind redaktionelle Testdatenentscheidungen mit ausstehendem menschlichem
Review, keine offizielle RxNorm-PZN-Überleitung.

Der Synthea-Import gibt RxNorm weder als Präparat- noch als Wirkstoffcoding aus.
Ohne belegte PZN bleiben Beschreibung, explizite Quellform und Medikationsereignis
erhalten. Die Beschreibung kennzeichnet offene PZN- und gegebenenfalls ATC-Zuordnung.
ATC allein identifiziert keine Packung. Wirkstoffschlüssel stammen aus den öffentlichen
NIH-RxNav- und FDA-UNII-Registern. Die Belege sind pro Wirkstoff dokumentiert.
UNII (`http://fdasis.nlm.nih.gov`) ist im KDS-Medikationsprofil 2026.0.1 vorgesehen;
es wird keine ASK-Abdeckung behauptet. Kombinationen stehen mit Semikolon getrennt
in der Wirkstoffspalte und werden als getrennte FHIR-Ingredients ausgegeben.
Manuelle RxNorm-Eingaben im allgemeinen Konverter bleiben möglich.

Die synthetischen Patientengeschichten dürfen plausibel konkretisiert werden.
Explizite Ersatzwahlen (etwa Hydrocodon retard zu Hydromorphon retard) enthalten
Zielwirkstoffe und ein neues Dosierungsschema. Die ursprüngliche Dosierung bleibt
im Herkunftsbericht erhalten. Eine solche Entscheidung behauptet keine
pharmazeutische Gleichwertigkeit. Unbekannte Codes außerhalb des gepinnten Bestands
bleiben sichtbar offen und werden nicht durch ähnliche Namen erraten.

ATC wird ausdrücklich mit der deutschen Jahresversion 2026 ausgegeben, auch bei
historischen Testereignissen. Es erfolgt keine Behauptung historischer Marktverfügbarkeit.
Packungsbelege stammen aus öffentlichen BfArM-, TK-/DAK- und Herstellerlisten,
G-BA-Herstellerdossiers, Gebrauchsinformationen und veröffentlichten Herstellerhinweisen.
Einzelne historische Packungen sind durch andere öffentliche Dokumente belegt.
URL, Hash und gegebenenfalls PDF-Seite stehen im Mapping. Die ausgewählten Fakten
erfordern weder MMI-Pharmindex noch Medication Graph oder eine laufende Terminologieabfrage.
Eine öffentliche Fundstelle ist keine pauschale Lizenz für deren vollständigen Datenbestand.

Stärke, Form und Therapie dürfen für das synthetische Szenario geändert werden.
`dosePolicy` unterscheidet erhaltene Quelldosierungen von explizit neu festgelegten
Schemata. Einheitenlose orale Synthea-Stückzahlen erhalten bei geeigneten Präparaten
die Einheit Tablette bzw. Kapsel; bereits angegebene physikalische Einheiten bleiben
erhalten. Der Rückvergleich berücksichtigt die dokumentierten Ergänzungen.
Packungsinhalt und verabreichte Dosis werden nicht gleichgesetzt. Für gewichts- oder
indikationsabhängige Infusionen kann das Schema als Text ohne erfundene feste Dosis stehen.

Bei Herceptin SC wird rekombinante Hyaluronidase entsprechend der EU-Fachinformation
als Hilfsstoff behandelt und nicht als zweiter aktiver Wirkstoff ausgegeben.
Atropin-Augentropfen werden im Synthea-Modul Zerebralparese sublingual gegen
Speichelfluss verwendet; das ausdrücklich synthetische Anwendungsschema erhält
diesen Kontext. Begleitmedikation, altersabhängige Dosierung und Therapieersatzwahlen
bleiben Bestandteile des menschlichen Reviews der befüllten Excel-Dateien.

Der amtliche ATC-Vollkatalog wird nur extern zur Prüfung verwendet und nicht
weiterverteilt. `audit_medication_mapping.py` prüft Quellvollständigkeit und
Zielcode-Existenz gegen die originale 2026er-XLSX. Dies ersetzt keine fachliche
Äquivalenzprüfung. `medicationMappingSummary` bilanziert Ereignisse und offene Codes;
`clinicalMappings` enthält die Herkunft einschließlich RxNorm.

Besondere Reviewfälle: ASS 81 mg wird in Synthea auch als Analgetikum verwendet;
die Klassifikation bleibt die des niedrig dosierten antithrombotischen Präparats.
ASS 325 mg ist im gepinnten Koronarsyndrom-Modul antithrombotisch klassifiziert.
Levonorgestrel-Implantate werden von Intrauterinsystemen unterschieden.

## Optionaler lokaler Adapter (historischer Aufbau)

Die folgenden Adapterdetails betreffen den optionalen lokalen Katalog. Der frühere
RxNorm-Rückfall wird im Synthea-Standard durch den oben beschriebenen öffentlichen
Mappingweg ersetzt. MMI und Medication Graph sind keine Voraussetzung.


Der Synthea-Import funktioniert ohne MMI-Pharmindex. Er nutzt den öffentlichen Mappingbestand und
verwendet die vorhandenen deutschen Projektübersetzungen bei fehlender Produktauswahl. Diese Übersetzungen
werden vor Ausgabe um in eckigen Klammern angegebene US-Handelsnamen bereinigt;
sie sind keine behaupteten deutschen Handelspräparate. Unbekannte Texte stehen im Textbericht.

Ein vorhandener lokaler Produktkatalog ergänzt diesen Bestand. Er liegt fest unter
`~/.local/share/excel2fhir/medication-products.json`, außerhalb des Repos.
Es gibt keinen zusätzlichen CLI-Schalter und keine laufende Neo4j-Abhängigkeit.
Die rohe Pharmindex-ZIP allein aktiviert nichts: Der Katalog muss zuvor aus
geeigneten, im jeweiligen Lizenzumfang nutzbaren Daten erzeugt werden.
Der Aufbau dieses echten MMI-Katalogs ist ein separater Schritt und noch nicht
implementiert. Der Adapter ist mit vollständig erfundenen Produktdaten getestet.

## Verhalten

- Kein lokaler Katalog oder kein Treffer: öffentlicher Mappingweg mit transparenten offenen Zuordnungen.
- Treffer mit dokumentierter unveränderter Dosierung: PZN, deutsche Bezeichnung,
  Darreichungsform und optional ein Wirkstoffcode aus dem lokalen Katalog.
- Ungeklärte Dosierung beim lokalen Wechsel: öffentlicher Mappingweg. Keine automatische
  Umrechnung allein aus ähnlichen Präparatnamen und kein ungeprüfter Zufallstreffer.
- Fehlerhafter Katalog, doppelte Quellzuordnung oder Code außerhalb des vorhandenen
  Synthea-Medikationsinventars: Import mit konkretem Fehler abbrechen.

Beide Wege verwenden dieselben Spalten und denselben FHIR-Konverter.
PZN wird über Präparatcode und Präparatcodesystem erfasst. ATC-Code und
ATC-Version sind davon getrennte zusätzliche Angaben. Der lokale Adapter
füllt Präparatbezeichnung, Präparatcode/-system, Darreichungsform und gegebenenfalls
Wirkstoffcode/-system; er erzeugt weiterhin keine Produktstärke aus der Dosierung.

## Datenformat des lokalen Katalogs

```json
{
  "schemaVersion": 1,
  "provider": "mmi-local",
  "id": "local-product-catalog",
  "sourceVersion": "2026-05-01",
  "entries": []
}
```

Jeder Eintrag enthält:

| Feld | Bedeutung |
| --- | --- |
| `source.system`, `source.code` | Genau ein Quellcode des vorhandenen Synthea-Medikationsinventars; keine explizite Quellversion in dieser ersten Stufe |
| `target.system` | `http://fhir.de/CodeSystem/ifa/pzn` |
| `target.code` | Achtstellige PZN als String, damit führende Nullen erhalten bleiben |
| `target.display` | Deutsche Produktbezeichnung |
| `target.doseForm` | Deutsche Darreichungsform als Text |
| `target.ingredient` | Optional ein Coding mit `system` und `code`: ASK, SNOMED CT oder RxNorm; keine explizite Version in dieser Stufe |
| `doseCompatibility` | `unchanged` bei begründet unverändert übernehmbarer Dosierung, sonst `unresolved` |
| `provenance.source` | Tatsächliche Quelle der Zuordnung |
| `provenance.method` | Vorgehen bei der Auswahl |
| `provenance.evidence` | Begründung, insbesondere Wirkstoff, Stärke und Form; Näherungen ausdrücklich nennen |

Der Adapter überprüft Format und Inventarbezug. Er validiert weder die Existenz
einer PZN im Pharmindex noch die pharmazeutische Richtigkeit der angegebenen
Dosiskompatibilität. Das muss der vorgelagerte Katalogaufbau leisten.
Unbekannte Ziel-Felder werden abgewiesen, damit Produktinformationen nicht
stillschweigend verloren gehen. Mehrere Wirkstoffe, strukturierte Produktstärken
und zusätzliche ATC-Codierungen benötigen den separat geplanten Medikationsumbau.
Bei fehlender Wirkstoffangabe bleibt die bestehende explizite DAR-Ausgabe erhalten.

## Herkunft und Ausgabedateien

Der Import lädt den Katalog einmal. Der Bericht enthält `productCatalog` mit
Kennung, Quellstand und SHA-256. Unter `clinicalMappings` bleiben Originalcode,
gewähltes Produkt, Methode und Begründung nachvollziehbar. Der Rückvergleich
verlangt denselben Katalogstand und prüft die Herkunftsangaben mit.

Sobald lokale Produktdaten verwendet werden, steht unter `productDataUsage`
`containsLocalProductData: true` und `redistribution: "not-cleared"`.
Der Generator verweigert dann Excel-/Berichtsausgaben innerhalb des Repos,
auch in ignorierten Unterordnern und über symbolische Verknüpfungen.
Der gemeinsame Workbook-Schreibpfad schützt auch den Mehrfall-Generator.
Außerhalb des Repos bleiben diese Dateien lokale Arbeitsergebnisse. Die
Kennzeichnung ist keine Lizenzprüfung und verhindert kein späteres manuelles
Kopieren oder Hochladen. Insbesondere gilt sie auch für anschließend erzeugte
FHIR-Dateien; die separate Java-Konvertierung liest den Begleitbericht nicht.

In das öffentliche Repo gehören der Adapter, das Datenformat, eigene Logik und
weiterverteilbare Daten. Echte MMI-Rohdaten, daraus erzeugte Kataloge und angereicherte
Beispiele werden nicht mitgeliefert. Die Test-PZN `00000000` und Testbezeichnungen
sind ausschließlich erfundene technische Fixtures, keine echten Produktzuordnungen.
Eine Veröffentlichung lokaler Produktdaten erfordert gesondert belegte Ausgaberechte.

Die Softwarelizenz des [Medication-Graph-FHIR-Converter](https://github.com/medizininformatik-initiative/Medication-Graph-FHIR-Converter)
(MIT) ersetzt keine Lizenz für dessen MMI-Eingabedaten. Das Projekt weist selbst
auf diese Trennung hin. [Vidal MMI: Nutzungsrechte, Abschnitt 6](https://www.mmi.de/agb/allgemeine-geschaeftsbedingungen-medien).
