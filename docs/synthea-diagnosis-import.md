# Import Synthea diagnoses

The importer fills `Diagnose` with source times, statuses and patient/case
associations. The [diagnosis mapping](diagnosis-mapping.md) supplies assessed
ICD-10-GM 2026 codings and documented synthetic assumptions.

ICD-10-GM is the primary coding; SNOMED is additional coding or the retained source
when a target is unavailable. Existing source ICD-10-GM with an explicit version
takes precedence. Exclusions and status adjustments are recorded with source IDs
in `Fall.loss.json`. Excel2FHIR then converts the codes entered in the workbook.

## Emergency contacts

A source `EMER` encounter becomes an `AMB` facility contact with admission-reason
subelement `VierteStelle`, code `7`. Excel records these as `ambulant` and `Notfall`.
Inpatient contacts use `IMP`. The source class and projection are recorded under
`encounterMappings`.

## Verification

Source comparison checks diagnosis counts, expected exclusions, coding versions,
times, statuses and references according to the selected Converter Options.
The [diagnosis input contract](diagnosis-workbook.md) describes required fields
and Data Absent Reasons. Optional [FHIR validation](fhir-validation.md) checks
the completed resources.

See [supported clinical input](synthea-clinical-import.md) for the other resource types.
