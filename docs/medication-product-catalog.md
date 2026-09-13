# Deutsche Medikamentendaten mit und ohne lokalen Produktkatalog

Der Synthea-Import funktioniert ohne MMI-Pharmindex. Er erhält Quellcodes und
verwendet die vorhandenen deutschen Projektübersetzungen. Diese Übersetzungen
können weiterhin Handelsnamen aus der US-Quelle enthalten; sie sind keine
behaupteten deutschen Handelspräparate. Unbekannte Texte stehen im Textbericht.

Ein vorhandener lokaler Produktkatalog ergänzt diesen Bestand. Er liegt fest unter
`~/.local/share/excel2fhir/medication-products.json`, außerhalb des Repos.
Es gibt keinen zusätzlichen CLI-Schalter und keine laufende Neo4j-Abhängigkeit.
Die rohe Pharmindex-ZIP allein aktiviert nichts: Der Katalog muss zuvor aus
geeigneten, im jeweiligen Lizenzumfang nutzbaren Daten erzeugt werden.
Der Aufbau dieses echten MMI-Katalogs ist ein separater Schritt und noch nicht
implementiert. Der Adapter ist mit vollständig erfundenen Produktdaten getestet.

## Verhalten

- Kein Katalog oder kein Treffer: Originalcode und deutsche Projektbezeichnung.
- Treffer mit dokumentierter unveränderter Dosierung: PZN, deutsche Bezeichnung,
  Darreichungsform und optional ein Wirkstoffcode aus dem lokalen Katalog.
- Ungeklärte Dosierung beim Wechsel: Originalprodukt erhalten. Keine automatische
  Umrechnung allein aus ähnlichen Präparatnamen und kein ungeprüfter Zufallstreffer.
- Fehlerhafter Katalog, doppelte Quellzuordnung oder Code außerhalb des vorhandenen
  Synthea-Medikationsinventars: Import mit konkretem Fehler abbrechen.

Beide Wege verwenden dieselben Spalten und denselben FHIR-Konverter. Das
Codesystem PZN ist im bestehenden Medikamentencode-Feld auswählbar. Die separate
alte PZN-Spalte wird vom neuen Adapter nicht befüllt. Dadurch bleibt der Pfad
aktiv, der Einzeldosis und Tageshäufigkeit richtig unterscheidet.

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
