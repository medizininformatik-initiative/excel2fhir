# Importbilanz

Jede begonnene CSV→FHIR-Konvertierung schreibt neben den FHIR-Dateien eine
`*.import.json`. `COMPLETE` bedeutet, dass die erfassten Eingaben ohne erkannte
Importfehler verarbeitet wurden. `INCOMPLETE` führt auch ohne `-v` zum CLI-Exitcode
1. Bereits erzeugte Ergebnisse bleiben für die Fehlersuche erhalten; sie dürfen
bei diesem Status nicht als vollständiger Export behandelt werden.

Der Bericht nennt Tabelle, CSV-Datei, logische Datensatznummer (ohne Kopfzeile),
Fehlerkategorie und Ursache. Die Datensatznummer ist keine Excel-Zeilennummer:
CSV-Felder können mehrere Textzeilen enthalten. Bei wiederholter Konvertierung
werden zusätzlich Ausgabepräfix und Iteration angegeben. Vollständige CSV-Zeilen
werden nicht absichtlich in den Bericht kopiert; Fehlermeldungen können dennoch
betroffene Eingabewerte enthalten.

Die Konverteransichten `Person` und `Consent` lesen dasselbe Person-Blatt,
werden aber getrennt bilanziert. Ihre Zeilenzahlen daher nicht als unterschiedliche
Eingabezeilen addieren.

Pro Tabelle bzw. Konverteransicht stehen folgende Zähler bereit:

- `rowsRead`: vollständig eingelesene CSV-Datensätze.
- `emptyRows`: gemäß den vorhandenen Tabellenregeln leere Datensätze.
- `processedRows` / `failedRows`: eindeutige erfolgreich bzw. fehlerhaft
  verarbeitete Datensätze. Bei mehreren Ausgabevarianten können sich diese Mengen
  überschneiden.
- `successfulAttempts` / `failedAttempts`: tatsächliche Aufrufe des jeweiligen
  Zeilenkonverters; Vorprüfungsfehler zählen nicht als Konvertierungsversuch.
- `returnedResources`: vom Zeilenkonverter zurückgegebene Ressourcen über alle
  erfolgreichen Versuche, keine Anzahl eindeutiger Ressourcen in der Ausgabe.
- `unprocessedRows`: eingelesene, nicht leere Datensätze ohne Erfolg oder
  zugeordneten Zeilenfehler, beispielsweise nach Ablehnung einer ganzen Tabelle.
- `rejected`: die Tabelle konnte nicht regulär eingelesen oder akzeptiert werden.

Fehlende Pflichtspalten einschließlich Patient-ID, doppelte Spaltennamen,
abweichende Feldanzahlen, unbekannte Patienten und Konvertierungsfehler werden
sichtbar. Leere Patient-IDs übernehmen weiterhin die vorherige ID derselben
Tabelle. Eine fehlerhafte Feldanzahl unterbricht diese Übernahme. Patient-IDs
werden ohne Berücksichtigung der Groß-/Kleinschreibung als ganze Zeichenfolge
verglichen, nicht als regulärer Ausdruck.

Bei syntaktisch unlesbarem CSV kann keine vollständige Zeilenzahl angegeben
werden. Ein vor Beginn der CSV-Konvertierung abgebrochener Excel-Vorcheck oder
ungültiger CLI-Aufruf liefert Exitcode 1 und die Fehlermeldung, aber noch keinen
Importbericht. Zeilenkonvertierungen sind keine Transaktionen: nach einem Fehler
können bereits vorgenommene Änderungen an Ressourcen erhalten bleiben.

Die drei Berichte beantworten unterschiedliche Fragen:

| Bericht | Bedeutung |
| --- | --- |
| `*.loss.json` | Welche Synthea-Inhalte wurden projiziert, ersetzt oder ausgelassen? |
| `*.import.json` | Wurden die Excel-/CSV-Eingaben vollständig verarbeitet? |
| `*.validation.json` (mit `-v`) | Welche FHIR-Prüfungen bestanden, scheiterten oder waren `NOT_CHECKED`? |

Ein vollständiger Import ist keine bestandene FHIR-Validierung. Fehlende
Terminologien bleiben `NOT_CHECKED` und führen mit `-v` weiterhin zu Exitcode 1.
