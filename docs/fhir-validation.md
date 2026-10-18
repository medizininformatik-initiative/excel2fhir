# FHIR validation

Add `-v` to validate completed bundles against the bundled FHIR profiles and
terminologies. This option applies to Excel, CSV and Synthea workflows. In a
Synthea generation command, place it before `--`.

Validation is disabled by default. A successful default conversion has status
`NOT_VALIDATED` and exit code 0. With validation enabled, completed generation has
status `COMPLETE` and exit code 0 even when validation reports findings or unavailable
terminology checks. Inspect the separate validation reports for conformance.
Technical process failures and incomplete imports produce exit code 1.

With a configuration file, `CHECKS_FHIR_VALIDATION` selects profile validation and
takes precedence over `-v`. `CHECK_INPUT_CONSISTENCY` controls the additional
workbook input checks. These settings have separate purposes.

Free codes and custom data-absent-reason codes are retained in generated resources,
including deliberately invalid codes. Enabling profile validation adds findings
without correcting or removing those values. Required input structure and patient
references must still be usable: missing required headers and unknown patient IDs
fail conversion even with consistency checks and profile validation disabled.
See [input requirements](contact-input-checks.md) for the shared checks.

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
edition 2025-07-01, UNII 20250702, historical ICD-10-GM/OPS/ATC versions and an IPS
laboratory ValueSet. Expansion may fail on a historical edition included in a
ValueSet even when the resource specifies 2026. This is an incomplete binding
check, not evidence that the resource uses that historical edition.
Some catalog gaps are reported as warnings by HAPI; inspect the detailed messages.

The [coding-version rules](terminology-versions.md#editing-a-workbook) describe
required version elements and the medication package's ATC edition restriction.
An independent catalogue-membership audit complements profile validation but
does not replace its bindings or terminology checks.

Allow at least 8 GB of Docker memory for validation; large inputs may need more.
The converter's container permits Java to use half the available memory as heap.
See [validator performance](validator-performance.md) for large-bundle behavior.

Clinical plausibility and the quality of synthetic mappings are assessed through
review of the workbook and its source reports.

The [DAR field catalogue](../web/catalog/dar/README.md) documents the generated configuration
contract, its profile sources, semantic code selections and extension workflow.
