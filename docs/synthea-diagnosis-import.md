# Diagnoseabbildung aus Synthea

Für den vollständigen Ablauf siehe [Synthea → Excel → FHIR](synthea-workflow.md).
Diese Seite beschreibt die Diagnoseabbildung; der Import umfasst inzwischen
auch die weiteren in [Importumfang](synthea-clinical-import.md) genannten Bereiche.

## Codes und Zeiten

Der Import übernimmt Diagnosezeiten, Status und Patient-/Kontaktzuordnung in das
Blatt **Diagnose**. Die versionierte Mappingtabelle ergänzt näherungsweise
passende ICD-10-GM-2026-Codes, gegebenenfalls unter dokumentierten synthetischen
Annahmen. ICD steht vor dem ergänzenden SNOMED-Coding. Bereits vorhandenes
ICD-10-GM mit ausdrücklich angegebener Version hat Vorrang.

Für das festgelegte produktive Diagnoseinventar gibt es 333 Entscheidungen:
321 Zuordnungen, zehn bewusste Ausschlüsse und zwei SNOMED-Konzepte ohne
ICD-Ergänzung. Auslassungen und Statusanpassungen werden mit Quell-ID im
`Fall.loss.json` berichtet. [Mappingregeln, Quellen und Grenzen](diagnosis-mapping.md).

Die spätere Excel→FHIR-Konvertierung liest die Codes aus Excel. Sie führt kein
erneutes Synthea-Mapping aus und ersetzt deshalb keine manuellen Änderungen.

## Notfallkontakte

Synthea-Notfallkontakte (`EMER`) werden im verwendeten KDS-Modell als `AMB` mit
Aufnahmegrund, Unterelement `VierteStelle`, Code `7` abgebildet. Beide Angaben
stehen in Excel. Stationäre Kontakte bleiben `IMP`. Der Verlust-/Mappingbericht
enthält die ursprüngliche Klasse und die Überleitung unter `encounterMappings`.
Eine zusätzliche stationäre Aufnahme wird daraus nicht erfunden.

## Prüfung

Der automatische Rückvergleich prüft Diagnoseanzahl unter Berücksichtigung
expliziter Ausschlüsse, Codes/Versionen, Zeiten, Status und Referenzen gegen die
Quelle und die verwendete Mappingversion. Die wirksamen Converter-Optionen werden
berücksichtigt. Rohdaten und Mapping-Prüfsummen bleiben für den Review erhalten.

Mit `-v` validiert der Java-Converter die vollständigen Zielbundles und erhält
auch Ressourcen mit Fehlern. Nicht ausführbare Terminologieprüfungen führen zu
`NOT_CHECKED` und Exitcode 1. Fehlende SNOMED-Ausgaben beweisen weder gültige noch
ungültige Codes. Das frühere Verhalten, abgelehnte Ressourcen aus der Ausgabe zu
entfernen, gilt nicht mehr. [Details zur FHIR-Validierung](fhir-validation.md).

`recordedDate` ist im eingebundenen Diagnoseprofil verpflichtend; Onset und
Abatement sind optional. Data Absent Reasons und zeitliche Konsistenz müssen zur
jeweiligen Eingabe passen. Ein technisch erfolgreicher Rückvergleich ersetzt
keine medizinische Prüfung der synthetischen Zuordnungen.
