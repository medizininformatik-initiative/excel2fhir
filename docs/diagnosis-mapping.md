# Näherungsweises Diagnosemapping für synthetische Testfälle

Der Synthea-Import ergänzt passende ICD-10-GM-Codings, damit synthetische Fälle
bereits ohne externen Terminologieserver nutzbar sind. Die Zuordnung ist bewusst
vorläufig. Sie behauptet weder medizinische Äquivalenz noch eine geprüfte
Abrechnungs- oder klinische Kodierqualität.

## Funktion und Daten

`scripts/diagnosis_mapping.py::map_diagnosis(condition)` liefert eine Entscheidung
mit Quellcoding, optionalem Zielcoding, Status und Begründung. Sie verändert die
Quelle nicht. Der erste Stand verwendet die versionierte Tabelle
`scripts/mappings/synthea-diagnoses-icd10gm-2026.json`. Die Beurteilung basiert auf
den Synthea-Bezeichnungen und dem offiziellen ICD-10-GM-Katalog 2026; sie ist eine
Assistenzbeurteilung, keine unabhängige fachliche Prüfung. Es erfolgen keine
Netzwerk- oder KI-Aufrufe während eines Imports.

Die Funktion unterscheidet:

- `approximate`: zusätzliches ICD-10-GM-Coding aus der Tabelle.
- `unmapped`: keine ausreichend begründete oder noch keine beurteilte Zuordnung;
  der Originalcode bleibt erhalten, der Zusatzcode bleibt leer.
- `source-preserved`: ein ICD-10-GM-Coding existiert bereits in der Quelle und
  wird unabhängig vom Tabellenvorschlag unverändert übernommen.

Schlüssel ist der SNOMED-Code. Die mitgelieferte Bezeichnung wird zusätzlich gegen
den beurteilten Text geprüft (Groß-/Kleinschreibung und Leerraum sind unerheblich).
Bei fehlendem `coding.display` wird `code.text` verwendet. Unbekannte Codes,
abweichende Texte und nicht beurteilte explizite SNOMED-Versionen werden nicht
erraten. So führt ein oberflächlich ähnlicher Text nicht zu beliebigen Zuordnungen.
Der Importer unterstützt weiterhin nur seine ausdrücklich angegebenen Quellsysteme
und Versionen; ein nicht unterstütztes Quellcoding wird dort vor dem Mapping abgelehnt.

ICD-10-GM 2026 ist der feste Zielkatalog dieser Mappingversion, auch bei historischen
Ereignissen. Er wird ausdrücklich als Zusatzcodesystem eingetragen. Originalcode,
Diagnosebezeichnung, Zeitangaben, Status und Referenzen bleiben erhalten. Ein
Zusatzcoding erzeugt keine zusätzliche Condition.

## Erste Abdeckung und Beispiele

Die erste Tabelle beurteilt sämtliche 30 unterschiedlichen Condition-Konzepte des
bereits generierten Testpatienten. Davon erhalten 21 einen Zielcode; 9 bleiben
bewusst offen. Auf die 76 Diagnosezeilen verteilt entstehen 37 Zusatzcodings.
Diese Zahlen messen Abdeckung, keine Trefferquote. Der breitere Mappingpilot über
das gesamte Synthea-Inventar ist damit noch nicht abgeschlossen.

| Synthea-Bezeichnung | ICD-10-GM 2026 | Bewusste Vereinfachung |
| --- | --- | --- |
| Acute bronchitis | J20.9 | Kein bestimmter Erreger angenommen. |
| Prediabetes | R73.08 | Kein Diabetes-Typ ergänzt. |
| Body mass index 30+ – obesity | E66.99 | Kein konkreter Adipositasgrad aus einer Untergrenze abgeleitet. |
| Fracture subluxation of wrist | S62.8 | Grobe Frakturzuordnung; Subluxation nicht zusätzlich abgebildet. |
| History of appendectomy | Z90.4 | Zustand nach Organverlust; keine neue Appendizitis. |
| Full-time employment | offen | Beschäftigung allein wird nicht in eine Krankheit umgedeutet. |
| Viral sinusitis | offen | Akut/chronisch nicht allein aus dieser Bezeichnung ableitbar. |

Alle 21 Zielcodes wurden gegen das vorhandene offizielle CodeSystem und das
ValueSet der terminalen ICD-10-GM-Codes 2026 auf Existenz, Bezeichnung und
Endständigkeit geprüft. Quellen-URL und Prüfsummen stehen in der Mappingdatei.
Die vollständigen BfArM-Katalogdateien werden nicht im Repository dupliziert.

## Nachvollziehbarkeit und Verbesserung

Der Begleitbericht `case.loss.json` enthält unter `diagnosisMapping` die Version,
Prüfsumme und den vorläufigen Beurteilungsstatus. Unter `diagnosisMappings` steht
für jede Condition die konkrete Entscheidung mit Begründung und Zielbezeichnung.
Diese Angaben ermöglichen die Überprüfung einer Ergänzung auch dann, wenn Excel
später unabhängig vom Importer verwendet wird.

Verbesserungen erfolgen durch nachvollziehbare Änderungen der Tabelle mit neuer
Versions-ID oder durch Austausch der Funktion unter Beibehaltung des
Entscheidungsvertrags. Eine spätere Terminologieserver-Anbindung kann weitere
Validierung oder belegte Zuordnungen liefern. Bereits erzeugte Excel-Dateien
werden dadurch nicht automatisch geändert; sie bleiben eigenständig nutzbar.

Der Rückvergleich prüft Originalwerte und genau die erwarteten Zusatzcodings.
Tests sichern unter anderem den Vorrang vorhandener ICD-Codes, abweichende
Bezeichnungen und Versionen sowie die Erkennung fehlender oder manipulierter
Ergänzungen. Diese technischen Prüfungen ersetzen keine unabhängige Bewertung der
fachlichen Mappinggüte und keine vollständige SNOMED-Terminologievalidierung.
