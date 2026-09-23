# Diagnosis mapping for synthetic cases

The Synthea importer uses
`scripts/mappings/synthea-diagnoses-icd10gm-2026.json` to add approximate ICD-10-GM
codings. Each decision records the source, target and rationale. The table contains
fixed synthetic assumptions based on Synthea labels and the official 2026 catalog;
clinical review is required to assess those assumptions.

## Mapping decisions

`scripts/diagnosis_mapping.py::map_diagnosis(condition)` returns one of:

| Status | Result |
| --- | --- |
| `approximate` | Add the table's ICD-10-GM coding. |
| `unmapped` | Preserve the source coding after an assessment found insufficient support for a target. |
| `excluded` | Record the omission with source resource ID and reason. |
| `not-assessed` | Preserve the source when code, label or explicit version is outside the assessed inventory. |
| `source-preserved` | Preserve existing source ICD-10-GM coding, which takes precedence. |

The lookup uses the SNOMED code and checks its source label against the generating
module states, ignoring case and whitespace. `code.text` supplies the label when
`coding.display` is empty. Accepted explicit versions follow the importer's source
system rules.

The target catalog is ICD-10-GM 2026, including for historical synthetic events.
It is recorded explicitly in Excel. Source coding, label, times and relationships
remain traceable; additional coding describes the same Condition.

For the table's suspected lung-cancer, prostate-cancer and COVID concepts,
`targetVerificationStatus` sets `provisional` when the source status is absent or
`confirmed`. Other explicit statuses are preserved. The report records this under
`verificationStatusChange`. Existing ICD-10-GM coding takes precedence over the
mapping and its status adjustment.

## Inventory and provenance

Version `synthea-diagnoses-icd10gm-2026-v4` assesses 333 productive ConditionOnset
concepts from the pinned generator: 321 ICD-10-GM mappings, ten explicit exclusions
and two concepts retained with SNOMED alone. Exclusions cover medication-review
tasks and social attributes such as employment, education and migration.

The inventory is derived from generating ConditionOnset states. Each entry records
source files, states and module SHA-256 checksums. Additional source modules or
export configurations require their own inventory review.

Targets are checked against the official 2026 CodeSystem and terminal-code
ValueSet. To reproduce that audit with local source and catalog files:

```sh
python3 scripts/audit_diagnosis_mapping.py /path/to/synthea \
  /path/to/icd-gm2026.json /path/to/gm-terminal-vs.json
```

The audit compares source-code sets, labels, locations and target code existence,
version, labels and terminal status. The mapping stores catalog URLs and hashes.

## Examples of synthetic assumptions

| Source concept | ICD-10-GM 2026 | Interpretation |
| --- | --- | --- |
| Acute bronchitis | J20.9 | Unspecified organism. |
| Prediabetes | R73.08 | Abnormal glucose finding. |
| Body mass index 30+ – obesity | E66.99 | Unspecified obesity grade. |
| Fracture subluxation of wrist | S62.8 | Broad fracture assignment. |
| History of appendectomy | Z90.4 | Acquired absence of part of the digestive tract. |
| Miscarriage in first/second trimester | O03.9 | Assumed uncomplicated spontaneous abortion. |
| Suspected lung cancer | C34.9 | Provisional diagnosis. |

Elevated suicide risk is represented as suicidality using R45.8 for the synthetic
scenario. The distinction between risk and current symptom, including the
[catalog exclusion](https://klassifikationen.bfarm.de/icd-10-gm/kode-suche/htmlgm2026/block-r40-r46.htm),
requires clinical review. A suicide event with unspecified injury and hospice
place of death retain their source concepts.

## Reports and maintenance

`Fall.loss.json` records the mapping version, hash and assessment status under
`diagnosisMapping`; `diagnosisMappings` contains per-Condition decisions and
rationales. The source comparison checks original facts and expected additions.

Update the table with a new version ID when changing decisions. Review clinical
appropriateness separately from code existence and technical roundtrip tests.
[Diagnosis import](synthea-diagnosis-import.md) explains how the decisions fill Excel.
