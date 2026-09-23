# Workbook input fields

Use the supplied workbook's sheet names, column headings and dropdowns. This page
summarizes how input fields map to FHIR. Detailed instructions are available for
[diagnoses](diagnosis-workbook.md), [contacts](synthea-movements.md) and
[selection lists](clinical-selections.md).

## Person and address

`Person` records identity, consent and address fields. Enter address components
in `Straße`, `Postleitzahl`, `Ort`, `Bundesland` and `Land`. Available components
are preserved when the country is empty. An entirely empty address receives the
Data Absent Reason `unknown`. For country `DE`, German state names become
ISO 3166-2 codes.

## Medication

`Medikation` separates product identity, ATC classification, ingredients and dose:

- `Präparatbezeichnung`, `Präparatcode` and `Präparatcodesystem` identify the product.
- `ATC-Code` and `ATC-Version` provide the classification and its explicit year.
- `Wirkstoffcode` and `Wirkstoffcodesystem` identify ingredients. Separate multiple
  ingredients with semicolons; all use the selected system. Supported systems are
  ASK, UNII, SNOMED CT and RxNorm. Each ingredient becomes a separate FHIR entry.
- `Darreichungsform` is the dose-form text. `Einzeldosis` is the amount per dose.

| Medication type | `Dokumentationszeitpunkt` | `Beginn` / `Ende` |
| --- | --- | --- |
| `Verordnung` (request) | Optional `authoredOn` | Leave empty. |
| `Verabreichung` (administration) | Leave empty. | `effectiveDateTime` or `effectivePeriod`; start required. |
| `Medikationsaussage` (statement) | Optional `dateAsserted` | `effectiveDateTime` or `effectivePeriod`; start required. |

Use an explicit Data Absent Reason for an unknown required time or ingredient.
Unknown ingredients also require their code system. Status choices depend on the
medication type. The default status is `active` for requests/statements and
`completed` for administrations. Request intent defaults to `order`.

For requests and statements, a complete dose, unit and integer daily frequency
produce structured dosage. Free text or partial instructions preserve all
provided facts in `Dosage.text`. Administrations keep dose structured and daily
frequency as text; a missing dose unit receives DAR `unknown`. A text-only
administration dosage requires an explicit unknown dose to satisfy the input rule.

Medication IDs include product details, ATC version, form and ingredient system;
references use those generated IDs.

## Observations

The investigation code identifies what was measured. An additional coding describes
the same investigation. Result code/system describe a coded answer.
`Einheit` is the unit label; `Einheitencode` is its UCUM code.

Laboratory rows support number, text, coded answer, components and missing value.
Clinical documentation also supports Boolean values. Components follow their
parent row and refer to its `Untersuchung ID` through `Komponente von`.
A parent and its components form one Observation. Diagnostic reports use those
investigation IDs for result references.

Laboratory text results become `valueCodeableConcept.text`, with explicit DAR
for the unavailable coding. Clinical text observations use `valueString`.

## Other clinical fields

- `Prozedur`: procedure code/system, optional additional coding, performed start/end,
  status and category. Contact periods and procedure periods are separate inputs.
- `DocumentReference`: `Dokumenttext` supplies embedded text. Otherwise,
  `Dateipfad` and `Embed` select file content. Document type, date and status
  describe the document.
- `Impfung`, `Befundbericht` and `Behandlungsplan`: event code/system, time and
  resource-specific status fields. See the workbook's field help.

## Missing values

An empty optional field omits the corresponding property. Explicit missing-value
choices such as `Unbekannt (Data Absent Reason)` or `!dar:unknown` create the
appropriate FHIR extension. [Selection lists](clinical-selections.md) describe
where those choices apply. Profile requirements are checked by the optional
[FHIR validator](fhir-validation.md).
