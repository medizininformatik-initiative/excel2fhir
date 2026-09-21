# Workbook selection lists

The `Codes` sheet provides dropdown values for specific input fields. Sheet names,
column headings and selection labels match the supplied German workbook.
Clinical code and date fields also accept manual input.

## Code systems and categories

| Input | Available choices | Range on `Codes` |
| --- | --- | --- |
| Laboratory investigation system | LOINC | AS30 |
| Clinical investigation and additional coding systems | LOINC, SNOMED CT | AT30:AT31 |
| Observation answer system | LOINC, SNOMED CT | AU30:AU31 |
| Procedure systems | SNOMED CT, OPS 2009–2026 | AB30:AB48 |
| Medication product system | PZN, SNOMED CT, RxNorm, CVX | AV30:AV33 |
| Medication ingredient system | ASK, UNII, SNOMED CT, RxNorm | AW30:AW33 |
| Immunization system | ATC 2026, SNOMED CT, RxNorm, CVX | AY30:AY33 |
| Report/document type system | LOINC, SNOMED CT | AZ30:AZ31 |
| Care plan system | SNOMED CT | BA30 |
| Laboratory category | laboratory | AD30 |
| Clinical observation category | vital-signs, survey, social-history, exam, imaging, procedure, therapy, activity | AR30:AR37 |

Diagnosis selections provide SNOMED CT and versioned ICD-10-GM choices. See
[diagnosis input](diagnosis-workbook.md). ATC classification has separate medication
columns. The ingredient-code dropdown includes UNII values from the public Synthea
mapping; semicolon-separated combinations represent several ingredients.

Status and intent lists follow the resource type. Medication status depends on the
medication type in the same row: request, administration or statement. Laboratory
value types are number, text, code, components and missing; clinical observations
also offer Boolean values.

## Missing values

Readable missing-value labels come from
`src/main/resources/workbook-absent-reasons.json`, shared by Java and Python.
They include the suffix `(Data Absent Reason)`, for example
`Unbekannt (Data Absent Reason)`. CSV and workbook inputs also accept
`!dar:<code>`.

General reasons cover unknown, asked but unknown, temporarily unknown, not asked,
asked but declined and masked. Code fields additionally offer unsupported and
not-permitted reasons. Measurement fields have choices for not applicable,
measurement errors and tests not performed.

| Sheet | Fields with explicit missing-value choices |
| --- | --- |
| `Fall` | Admission reason. |
| `Diagnose` | Codes, times and supported status fields. |
| `Prozedur` | Codes and times. |
| `Medikation` | Codes, event times and dose. |
| `Laborbefund` / `Klinische Dokumentation` | Result value with value type `Fehlend`. |
| `DocumentReference` | Document code. |
| `Impfung` | Code and vaccination time. |
| `Befundbericht` | Code and investigation time. |
| `Behandlungsplan` | Code and validity period. |

Empty fields and explicit missing reasons have different output behavior; see
[input fields](template-input-contracts.md). FHIR status codes such as `unknown`
remain status values. The optional validator checks profile requirements and
relationships between fields.

## Maintaining the lists

To update selection ranges in a copy of an existing workbook:

```sh
python3 scripts/clinical_selections.py INPUT.xlsx OUTPUT.xlsx
```

Use a new output filename. The script locates fields by their headings, replaces
validation links and preserves data and input-sheet layout through LibreOffice/UNO.
Generated Synthea workbooks extend selection links to their populated rows.

The first options sheet lists all Converter Options with descriptions and defaults.
Commented settings use those defaults. A regression test compares the documented
option list with the Java enums.

The lists are input aids based on the bundled profiles and converter support.
Terminology validation is handled by the [FHIR validator](fhir-validation.md).
