# FHIR validation

Add `-v` to validate completed bundles against the bundled FHIR profiles and
terminologies. This option applies to Excel, CSV and Synthea workflows. In a
Synthea generation command, place it before `--`.

Validation is disabled by default. A successful default conversion has status
`NOT_VALIDATED` and exit code 0. With validation enabled, errors, validator failures
and recognized terminology gaps produce exit code 1. Converted resources remain
available for inspection.

## Reports

`<bundle-name>.validation.json` records every validator message, its severity,
location and classification. Direct converter runs store reports under
`details/reports/`; Synthea retains them in the per-source converter runs under
`details/cases/`.

| Classification | Meaning |
| --- | --- |
| `ERROR` | A retained error or fatal message. |
| `NOT_CHECKED` | An explicitly unavailable CodeSystem or unresolved/unexpandable ValueSet. |
| `IGNORED` | The specific OBI identifier-warning exception. |
| `WARNING` / `VALID` | The remaining HAPI validation result. |

Message counters and validation-call counters are separate. Whole-bundle validation
counts as one call, with the resource count recorded separately. The file-validation
API reports a result for each processed file, including read or validation errors.

`referencesWithoutTargetInBundle` lists unresolved relative `Type/id` and UUID-URN
references by matching resource IDs and entry fullUrls. This inventory supports
review of self-contained output; relative references may also target an external
server. Absolute and contained references require their own resolution context.

## Terminology coverage

The [bundled package set](fhir-packages.md) supplies profiles and selected
terminologies. Complete SNOMED and LOINC checks require the matching terminology
editions or a terminology service. Known gaps include the international SNOMED
edition 2025-07-01, historical ICD-10-GM versions and an IPS laboratory ValueSet.
Some catalog gaps are reported as warnings by HAPI; inspect the detailed messages.

Allow at least 8 GB of Docker memory for validation; large inputs may need more.
The converter's container permits Java to use half the available memory as heap.
See [validator performance](validator-performance.md) for large-bundle behavior.

Clinical plausibility and the quality of synthetic mappings are assessed through
review of the workbook and its source reports.
