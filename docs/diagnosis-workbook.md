# Diagnosis input

Each row in `Diagnose` creates one Condition. Primary and additional coding
describe the same diagnosis. Enter distinct diagnoses in separate rows.

## Fields

| Column | Workbook heading | FHIR meaning |
| --- | --- | --- |
| A | `Patient-ID` | Patient association. |
| B | `Fall-Nr` | Case association. |
| C | `Bezeichner` | `code.text`. |
| D / E | `Code` / `Codesystem` | Primary coding, including the selected version. |
| F / G | `Zusatzcode` / `Zusatzcodesystem` | Optional additional coding. |
| H | `Dokumentationszeitpunkt` | `recordedDate`: first documentation of this entry. |
| I | `Beginn` | `onsetDateTime`: clinical onset. |
| J | `Ende` | `abatementDateTime`: resolution or remission. |
| K | `Klinischer Status` | `clinicalStatus`. |
| L | `Verifikationsstatus` | `verificationStatus`. |
| M | `Typ` | Diagnosis role within the case. |

Code values are preserved literally, including leading zeros. Choose a code system
for each populated code. Additional coding uses a different system, consistent
with the supported profile slices.

The system selection determines the coding version. For example,
`ICD-10-GM 2026` supplies version `2026`; `SNOMED CT (Version nicht angegeben)`
supplies an unversioned coding. The event date is an independent field.

## Times and missing values

The three time fields represent separate clinical facts. ISO/FHIR text values
retain their precision and timezone offset; native Excel dates use local time.
An empty field omits that property. Choose an explicit Data Absent Reason when
the reason for an unavailable value should be represented.

DAR attaches to `coding.code` for codes, the date element for times and the
CodeableConcept for status. The workbook provides readable choices; CSV also
accepts `!dar:<reason>`. See [selection lists](clinical-selections.md).

## Profile checks

The bundled KDS diagnosis profile requires `code` and `recordedDate`. Onset,
abatement and status are optional, subject to content rules:

- Abatement requires clinical status inactive, resolved or remission (`con-4`).
- Verification status entered-in-error requires an absent clinical status (`con-5`).

The optional [FHIR validator](fhir-validation.md) checks these rules. Workbook
consistency checks cover selections and system associations. Empty required
fields can be used to construct intentionally invalid test data.

## Admission reason

On `Fall`, `Aufnahmegrund (4. Stelle)` belongs on the facility-contact row with
an explicit `Fall-Nr`. Selecting `Notfall` produces the admission-reason extension
with subelement `VierteStelle` and code `7`. Contact class is selected separately;
an outpatient emergency contact uses `ambulant` plus `Notfall`.

The dropdown also offers explicit missing-value reasons. See the
[KDS facility-contact profile](https://medizininformatik-initiative.github.io/kerndatensatz-basis/de/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.html)
and [contact input rules](contact-input-checks.md).
