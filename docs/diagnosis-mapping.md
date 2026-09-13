# Näherungsweises Diagnosemapping für synthetische Testfälle

Der Synthea-Import ergänzt passende ICD-10-GM-Codings, damit synthetische Fälle
bereits ohne externen Terminologieserver nutzbar sind. Die Zuordnung ist bewusst
vorläufig. Sie behauptet weder medizinische Äquivalenz noch eine geprüfte
Abrechnungs- oder klinische Kodierqualität.

## Funktion und Daten

`scripts/diagnosis_mapping.py::map_diagnosis(condition)` liefert eine Entscheidung
mit Quellcoding, optionalem Zielcoding, Status und Begründung. Sie verändert die
Quelle nicht. Der Import verwendet die versionierte Tabelle
`scripts/mappings/synthea-diagnoses-icd10gm-2026.json`. Die Beurteilung basiert auf
den Synthea-Bezeichnungen und dem offiziellen ICD-10-GM-Katalog 2026; sie ist eine
Assistenzbeurteilung, keine unabhängige fachliche Prüfung. Es erfolgen keine
Netzwerk- oder KI-Aufrufe während eines Imports.

Fehlende klinische Details dürfen für die Testdatengenerierung durch feste,
dokumentierte Annahmen ergänzt werden. Die Tabelle legt je Quellcode einen
Zielcode fest; es gibt keine Zufallsauswahl und keine Einzelfallentscheidung beim
Import. Der Synthea-Modulkontext dient beim Aufbau der Tabelle als Grundlage,
wird zur Laufzeit aber nicht benötigt. Beispielsweise werden beide
Fehlgeburtscodes als Spontanabort ohne Komplikation (O03.9) abgebildet. Das ist
eine Testdatenannahme, keine aus dem individuellen Fall bewiesene Eigenschaft.

Die Funktion unterscheidet:

- `approximate`: zusätzliches ICD-10-GM-Coding aus der Tabelle.
- `unmapped`: beurteilt, aber keine ausreichend begründete Zuordnung;
  der Originalcode bleibt erhalten, der Zusatzcode bleibt leer.
- `not-assessed`: Quellcode, Bezeichnung oder explizite Version sind noch nicht
  durch den beurteilten Bestand abgedeckt; ebenfalls keine Ergänzung.
- `source-preserved`: ein ICD-10-GM-Coding existiert bereits in der Quelle und
  wird unabhängig vom Tabellenvorschlag unverändert übernommen.

Schlüssel ist der SNOMED-Code. Die mitgelieferte Bezeichnung wird zusätzlich gegen
die beurteilten Texte aus den erzeugenden Modulzuständen geprüft
(Groß-/Kleinschreibung und Leerraum sind unerheblich).
Bei fehlendem `coding.display` wird `code.text` verwendet. Unbekannte Codes,
abweichende Texte und nicht beurteilte explizite SNOMED-Versionen werden nicht
erraten. So führt ein oberflächlich ähnlicher Text nicht zu beliebigen Zuordnungen.
Der Importer unterstützt weiterhin nur seine ausdrücklich angegebenen Quellsysteme
und Versionen; ein nicht unterstütztes Quellcoding wird dort vor dem Mapping abgelehnt.

ICD-10-GM 2026 ist der feste Zielkatalog dieser Mappingversion, auch bei historischen
Ereignissen. Er wird ausdrücklich als Zusatzcodesystem eingetragen. Originalcode,
Diagnosebezeichnung, Zeitangaben und Referenzen bleiben erhalten. Ein
Zusatzcoding erzeugt keine zusätzliche Condition.

Bei den drei expliziten Verdachtskonzepten für Lungenkrebs, Prostatakrebs und
COVID legt `targetVerificationStatus` in der Tabelle `provisional` fest.
Ein fehlender oder von Synthea als `confirmed` exportierter Verifikationsstatus
wird dann in Excel auf „Vorläufig“ gesetzt. Andere ausdrücklich gesetzte
Statuswerte, insbesondere `refuted` und `entered-in-error`, bleiben erhalten.
`verificationStatusChange` im Begleitbericht hält Ausgangswert und Zielstatus
fest; der Rückvergleich prüft diese Änderung unabhängig vom Bericht.
Bereits vorhandene ICD-10-GM-Codings haben weiterhin Vorrang und lösen keine
Statusänderung aus.

## Abdeckung des produktiven Diagnoseinventars

Mappingversion `synthea-diagnoses-icd10gm-2026-v3` beurteilt alle **333** unterschiedlichen
primären ConditionOnset-Codes in den produktiven Modulen des gepinnten
Synthea-Checkouts `d9d07a6eef91ee5144293b42ab64224d84d124f8`. Das sind 409
Quellvorkommen. **320** Konzepte erhalten eine näherungsweise Zuordnung, **13**
bleiben bewusst ohne ICD-Ergänzung; **0** dieser Quellkonzepte sind noch
unbeurteilt. Diese Zahlen messen Abdeckung, keine Trefferquote.

Ohne Ergänzung bleiben neun neutrale Angaben zu Militärdienst, Migration,
Beschäftigung und Bildung sowie erhöhtes Suizidrisiko ohne Handlung, eine fällige
Medikamentenprüfung, ein Suizidereignis ohne konkrete Schädigungsart und der
Sterbeort Hospiz. Daraus wird keine zusätzliche Krankheit konstruiert. Konkrete
Suizidmethoden werden dagegen auf eine passende Schädigung abgebildet; bei
Versuchen ist deren Eintritt eine dokumentierte Testdatenannahme. Dies bedeutet
nicht, dass ICD keine Codes für äußere Ursachen kennt: Ein solcher Zusatzcode
allein wäre hier keine eigenständige ICD-Diagnose.

Die frühere Gesamtzählung 334 enthielt den Platzhalter `1234` aus
`src/main/resources/templates/modules/onset_distribution.json`. Dieser gehört
nicht zu den produktiven Modulen und wird nicht gemappt. Codes aus ConditionEnd,
logischen Abfragen, anderen Ressourcentypen und deren Bezeichnungen sind ebenfalls
keine zusätzlichen Diagnosequellen. Beispielsweise stammt die irreführende
Bezeichnung „Male Infertility“ für Code `427089005` aus einer anderen Verwendung;
der erzeugende ConditionOnset-Zustand bezeichnet damit Diabetes durch Mukoviszidose.
Die Mappingfunktion übernimmt nur den dort belegten Text.

Im geprüften Java-Quellcode ist State.ConditionOnset der einzige Aufrufer von
`HealthRecord.conditionStart`. Logic.ActiveCondition kann bestehende Conditions
kopieren; Death-/Lifecycle-Codes erzeugen keine zusätzlichen Condition-Diagnosen.
Der R4-Exporter übernimmt `condition.codes.get(0)`. Alle produktiven
ConditionOnset-Zustände dieses Standes enthalten genau ein SNOMED-Coding.
Externe Module, remote ValueSets und benutzerdefinierte CodeMapper-/Flexporter-
Konfigurationen sind ausdrücklich außerhalb dieses abgegrenzten Inventars.

Jeder Tabelleneintrag enthält die erzeugenden Dateien und Zustände sowie die
SHA-256-Prüfsummen der Moduldateien. Der Audit ist mit den vorhandenen externen
Dateien reproduzierbar:

```sh
python3 scripts/audit_diagnosis_mapping.py /path/to/synthea \
  /path/to/icd-gm2026.json /path/to/gm-terminal-vs.json
```

Er vergleicht die exakte Menge der Quellcodes, ihre Bezeichnungen und Fundstellen
sowie Zielsystem, Version, Zielbezeichnungen und Endständigkeit. Neue oder
veränderte Quellen und Kataloge werden so erkennbar. Die offiziellen Katalogdateien
bleiben außerhalb des Repos; dieser vollständige Audit benötigt sie lokal.
Die CI prüft die Auditlogik mit kleinen Fixtures.

## Beispiele und Grenzen der Annäherung

| Synthea-Bezeichnung | ICD-10-GM 2026 | Bewusste Vereinfachung |
| --- | --- | --- |
| Acute bronchitis | J20.9 | Kein bestimmter Erreger angenommen. |
| Prediabetes | R73.08 | Kein Diabetes-Typ ergänzt. |
| Body mass index 30+ – obesity | E66.99 | Kein konkreter Adipositasgrad aus einer Untergrenze abgeleitet. |
| Fracture subluxation of wrist | S62.8 | Grobe Frakturzuordnung; Subluxation nicht zusätzlich abgebildet. |
| History of appendectomy | Z90.4 | Zustand nach Organverlust; keine neue Appendizitis. |
| Full-time employment | offen | Beschäftigung allein wird nicht in eine Krankheit umgedeutet. |
| Viral sinusitis | J01.9 | Akuter Verlauf entsprechend dem auditierten Synthea-Pfad. |
| Miscarriage in first/second trimester | O03.9 | Feste Annahme eines unkomplizierten Spontanaborts; Trimenon bleibt im Original. |
| Meconium ileus | E84.1 | Darmmanifestation der Mukoviszidose; kein zusätzliches P75-Coding. |
| Suspected lung cancer | C34.9 | Vorläufige Diagnose; kein gesicherter Tumorstatus. |

Alle verwendeten Zielcodes wurden gegen das vorhandene offizielle CodeSystem und das
ValueSet der terminalen ICD-10-GM-Codes 2026 auf Existenz, Bezeichnung und
Endständigkeit geprüft. Quellen-URL und Prüfsummen stehen in der Mappingdatei.
Die vollständigen BfArM-Katalogdateien werden nicht im Repository dupliziert.

Weitere bewusste Verallgemeinerungen stehen direkt in der Tabelle: etwa
Knochenmarktransplantation ohne Annahme des aktuellen Immunsuppressionsstatus,
Lungenkarzinom ohne Abbildung von Histologie/TNM-Stadium und regionale Frakturen,
wenn die endständigen Zielcodes eine nicht bekannte Knochenstruktur voraussetzen.
Verdachtsdiagnosen werden nicht zu bestätigten Tumoren. Soziale Merkmale wie
Bildung, Beschäftigung oder Migration werden nicht pauschal in Krankheiten umgedeutet.

## Breiter Laufzeittest

Ein unveränderter Synthea-Lauf mit Seed und Clinician-Seed 20260912,
Referenz-/Enddatum 20260912, Alter 20–85 und vollständiger Historie erzeugte
12 lebende plus 6 verstorbene Patienten. Die 18 Patienten enthalten **2.573**
Conditions und **3.574** Encounters. Die 136 unterschiedlichen Diagnosekonzepte
sind vollständig im beurteilten Bestand enthalten; es gibt keine zusätzlichen
unbekannten Quellcodes oder abweichenden Bezeichnungen.

Mit Mappingversion v2 wurden über den vollständigen Rückweg aller 18 Fälle **1.395** näherungsweise
ICD-10-GM-Codings ergänzt; **1.178** Diagnosezeilen bleiben bewusst ohne Ergänzung.
Originalcodings, Diagnosezeiten, Statuswerte und Referenzen werden getrennt von
den erwarteten Ergänzungen geprüft. Damit ist mehr Laufzeitabdeckung belegt,
aber noch keine unabhängige Messung der medizinischen Mappinggenauigkeit.

Der erneute Import derselben 18 Quelldateien mit v3 liefert **1.595** Ergänzungen,
**978** Zeilen ohne Ergänzung und **eine** explizite Verifikationsstatusänderung.
Diese Zahlen stammen aus der Importvorbereitung; sie sind kein erneuter
vollständiger Excel-Rückweg aller 18 Fälle.

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
