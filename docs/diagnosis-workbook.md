# Diagnoseeingabe: neues Excel-Schema

Eine Zeile erzeugt eine Condition. Original- und Zusatzcoding beschreiben dieselbe
Diagnose. Unterschiedliche Diagnosen stehen in getrennten Zeilen.

## Vorher und nachher

| Vorher | Nachher | Änderung |
|---|---|---|
| A Patient-ID | A Patient-ID | unverändert |
| B Fall-Nr | B Fall-Nr | unverändert |
| C Bezeichner | C Bezeichner | unverändert |
| D ICD | D Code | Code bleibt wörtlich erhalten, keine Extraktion oder Großschreibung |
| — | E Codesystem | ausdrückliche Auswahl einschließlich Version |
| — | F Zusatzcode | optionales weiteres Coding derselben Diagnose |
| — | G Zusatzcodesystem | System/Version des Zusatzcodes |
| E Dokumentationszeitpunkt | H Dokumentationszeitpunkt | eigene Angabe, kein Ersatz aus dem Fallbeginn |
| — | I Beginn | onsetDateTime |
| — | J Ende | abatementDateTime |
| — | K Klinischer Status | clinicalStatus |
| — | L Verifikationsstatus | verificationStatus |
| F Typ | M Typ | weiterhin Diagnoserolle, z.B. Hauptdiagnose |
| G Erklärung/Ausfüllhilfe | N Erklärung/Ausfüllhilfe | kurze einzeilige Hinweise |

Die bestehenden Dateien `FHIR_Testdatengenerator_Vorlage.xlsx` und
`FHIR_Testdatengenerator_Interpolar_Demo.xlsx` wurden als Kopien mit LibreOffice/UNO
bearbeitet. Die vorhandenen Diagnosecodes bleiben erhalten. Ihre bisher aus dem
Falldatum abgeleiteten ICD-Versionen sind nun ausdrücklich eingetragen: 2019 in
der Standardvorlage, 2025 in der Demo. Die Demo enthält Ereignisse von 2026;
der alte Generator begrenzte deren Kodierung auf 2025. Ein Versionswechsel auf
2026 würde eine separate fachliche Prüfung der Codes erfordern.

Beginn, Ende und Status bleiben in den bestehenden Beispielen leer, weil diese
Angaben bisher nicht vorhanden waren. Sie werden nicht aus anderen Daten erfunden.
Alte Dateien mit der Spalte ICD werden nicht mehr als neues Schema akzeptiert.

## Auswahlwerte

Die bestehenden Listen bleiben erhalten. Die neuen Listen beginnen in Zeile 29
(Überschriften), damit lange vorhandene Location-Erklärungen darüber frei bleiben.
Ergänzt werden:

| Liste | Quelle auf Codes | Ziel auf Diagnose |
|---|---|---|
| Codesystem einschließlich Version | W30:W48 | E2:E1031, G2:G1031 |
| Klinischer Status und DAR | X30:X50 | K2:K1031 |
| Verifikationsstatus und DAR | Y30:Y50 | L2:L1031 |
| Expliziter Fehlwert | Z30:Z44 | D/F/H/I/J, Zeilen 2 bis 1031 |
| Diagnoserolle, bestehende Liste | D4:D13 | M2:M1031 |

In der Demo reichen die vorbereiteten Dropdownbereiche bis Zeile 1006,
in der Standardvorlage bis Zeile 1031.

Code- und Zeitfelder erlauben neben den DAR-Vorschlägen die freie Eingabe.
Codesysteme und Statuswerte verwenden die zentrale Auswahlliste. Codezellen sind
als Text formatiert; führende Nullen bleiben erhalten. Der Importer darf keine
Diagnoserolle aus einer beliebigen Quellkategorie ableiten.

`SNOMED CT (Version nicht angegeben)` bezeichnet bewusst eine Quelle ohne
Versionsinformation. Es wird keine SNOMED-Version ausgegeben. `ICD-10-GM 2026`
setzt dagegen ausdrücklich die Coding-Version 2026, unabhängig vom Ereignisdatum.
Die Auswahl bestätigt nicht die fachliche Gültigkeit eines Codes.

## Leerwerte und expliziter DAR

Leer bedeutet: die entsprechende Eigenschaft bzw. das Coding wird weggelassen.
Ein Code benötigt ein gewähltes System. Ein System ohne Code erzeugt kein Coding.
Ein Zusatzcoding desselben Systems ist wegen der 0..1-Kardinalität der unterstützten
Profil-Slices nicht zulässig.

`!dar:unknown`, `!dar:masked` usw. erzeugen ausdrücklich eine FHIR-
Data-Absent-Reason-Extension. Die Gründe stehen auf Codes, getrennt von den
fachlichen Statuswerten. Beim Code sitzt die Extension an coding.code, beim Datum
am Datumselement und beim Status am CodeableConcept. Ein DAR macht eine Ressource
nicht automatisch profilkonform. Die vorhandene optionale FHIR-Validierung kann
ungültige Ressourcen weiterhin ablehnen; ohne diese Validierung werden bewusst
ungültige Codes unverändert ausgegeben.

ISO-/FHIR-Zeitangaben einschließlich Zeitzonenoffset und Teilpräzision werden im
Diagnosekonverter erhalten. Echte Excel-Datumszellen werden wie bisher lokal
interpretiert. Für importierte FHIR-Zeitangaben verwendet der Importer Textzellen.

## Beispiel

Vorher: `ICD = K35.2`, System und Version implizit, kein Diagnoseverlauf.

Nachher können Original und geprüftes Ziel getrennt vorliegen:

- Code `74400008`, Codesystem `SNOMED CT (Version nicht angegeben)`.
- Zusatzcode und Zusatzcodesystem nur bei einer tatsächlich geprüften Zuordnung.
- Beginn und Ende beschreiben den klinischen Verlauf, Dokumentationszeitpunkt die Erfassung.

Es wird keine automatische SNOMED→ICD-10-GM-Übersetzung vorgenommen.
