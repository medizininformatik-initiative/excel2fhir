# Configuration workbench TODOs

This checklist records the agreed behavior and remaining implementation work.
GUI code, catalogues, tests and documentation live under `web/`. Shared converter
behavior remains in the Java converter. Existing issue references describe the
planned work streams; this checklist does not change their status.

## Follow-up work recorded on 2026-10-08

Open items are ordered by the agreed next steps. Verified implementation is
summarized below with code/test evidence; the comprehensive output audit remains
open even where individual behaviors have regression coverage.

Generation must follow input and configuration, including intentionally nonconformant
test data. Profile/terminology conformance belongs to optional validation, not silent
correction or additional generator restrictions.

- [ ] Audit configuration-to-FHIR behavior across all relevant combinations:
  resource selection and defaults; contact hierarchy, timestamp candidates,
  diagnosis references and DocumentReference matching; medication transformations;
  DAR precedence, complex values, coding systems and fixed/pattern constraints;
  patient/resource IDs, repetitions, time shifts, identifier stability/collisions;
  terminology years, arbitrary table versions and emitted profile claims; output
  formats, consistency checking and optional validation. Reuse existing focused
  tests and add end-to-end evidence for uncovered cases.
- [ ] Complete a determinism audit across the pipeline, derived resources, formats
  and versions; distinguish clinical content from run/archive metadata.
- [ ] Measure and improve memory/runtime with generator, portal, Blaze and TORCH;
  cover larger end-to-end workloads including 1000 patients. Deferred by the user.
- [ ] Agree on compatible `mii-data-quality` rules and scope before integration.
- [ ] Complete the separately scoped Keycloak/OAuth, FLARE, external terminology
  and DSF integration.

## Awaiting external response

- [x] Send the BfArM inquiry about use and redistribution of derived UNII-to-ASK
  mappings. The user confirmed sending it on 2026-10-08.
- [ ] Await the BfArM response and clarify usage conditions, then review candidate
  substance matches, salts, hydrates and mixtures; avoid a dependency on MMI Pharmindex.

## Verified implementation

- [x] Keep successful generation independent of source comparison differences and
  optional FHIR validation findings. Show validation reports and Synthea transfer
  notes separately, with configuration-covered omissions outside the import-note
  count. Comparisons account for resource selection, DAR and time shifts; each
  failing comparison reports its first difference rather than a total field count.
  Technical failures and incomplete imports fail generation. Persist summaries
  through cleanup. Evidence: `WorkflowRunTest`, `test_case_workflow.py`,
  `test_transfer_findings.py`, `test_run_outcomes.py`.

- [x] Preserve free table codes and nonempty `!dar:<code>` values, including unknown
  codes. DAR editor fields accept free text and suggested standard codes in both
  missing-only and overwrite modes. Structural checks remain active; optional FHIR
  validation reports vocabulary violations. Evidence: `InputCodeTest`,
  `ClinicalImportConverterTest`, `DarOverridesTest`, `CsvCommandLineTest`,
  configuration import/export tests.

- [x] Reuse all saved Excel workbooks of a completed run as one immutable input
  set; edit configuration and DIZ-specific ID prefix/suffix before starting a
  separate conversion without Synthea. Copies, checksums and source-run provenance
  survive source deletion. Evidence: `run_replay.py`, `test_run_replay.py`.

- [x] Run cleanup in the deletion dialog retains configuration, Excel, FHIR
  results, referenced files and checksums; removes source/intermediate files,
  logs and redundant archives. Downloads and FHIR upload remain usable, with
  dataset ZIPs rebuilt from checksum-verified result files. Active uploads block
  cleanup. Original source comparison requires original sources; repeat the
  Excel conversion using the retained workbooks/configuration. Evidence:
  `run_cleanup.py`, `test_run_cleanup.py`.

The evidence below records implemented behavior, not a claim that every possible
configuration combination has been checked.

- [x] DAR catalogue, field-specific choices and API configuration validation:
  `catalog/dar/generated/catalog.json`, `ConfigurationEditor.tsx`,
  `ContractConfigurationTest.defaultsAreExecutableAndDarIsSupported` and the API
  configuration preflight. `DarOverridesTest` covers missing-only/overwrite modes,
  complex values, coding-system preservation and scoped Encounter rules.
- [x] Common Encounter DAR rules are named explicitly. Inpatient/outpatient controls
  label the unchanged selection as inheritance. The existing inheritance behavior
  is covered by `EncounterOutputPolicyTest.unchangedScopedDarInheritsAndOtherClassesKeepTheirEnd`.
- [x] Versioned configuration contract, editor tabs, resource controls, help,
  import/export, saved configurations and identifier pattern editor:
  `catalog/options/contract.json`, frontend configuration tests and
  `ContractConfigurationTest`. Resource selection is implemented and covered by
  `ResourceOutputPolicyTest`, `PatientOutputPolicyTest` and `ContactOutputPolicyTest`.
- [x] Contact hierarchy, clinical reference assignment and diagnosis propagation:
  `ContactIndexTest`, `ContactOutputPolicyTest`, `ClinicalEncounterAssignmentTest`
  and `DiagnosisOutputPolicyTest`. These cover inclusive boundaries, tie-breaking,
  unavailable levels, secondary-contact exclusions, timestamp candidates and
  DocumentReference strategies. Review remaining edge cases in the full audit.
- [x] Medication replacement/addition order, stable derived IDs and resource
  selection: `MedicationTransformationsTest`.
- [x] Patient ID controls, terminology settings and clinical time shifts exist:
  `contract.json`, `ConverterOptionsTest`, `ClinicalTimeShiftTest` and
  `utils/TerminologyVersionUtilTest`. Repetitions append deterministic `-rep-N` suffixes for numeric IDs and UUIDs.
- [x] Additional identifier allocation, formatting, hashing, existing identifiers
  and collision checks: `AdditionalIdentifiersTest`.
- [x] Output/validation settings, per-run snapshots, variant archives and dataset
  inspection: `ContractConfigurationTest`, `ConverterVariantsTest`,
  `DatasetInspectionTest` and backend dataset/worker tests. JSON/NDJSON defaults,
  one patient per output file and disabled FHIR validation are in the contract.
- [x] Compact upload history: five recent completed uploads plus every active
  upload, collapsed details, visible concise errors, older entries on demand,
  deletion of individual/all completed local records and logs. Active uploads and
  FHIR server resources are retained. Evidence: `test_fhir_uploads.py`.
- [x] Compact configuration/Excel download for completed runs with saved workbooks:
  original bytes, available effective options/environment metadata, converter
  fingerprint and per-file checksums. Temporary ZIP files are removed after
  download. External document files referenced by workbooks must be supplied
  separately; upstream generation/source comparison requires original sources.
  Failed runs may contain only some workbooks. Evidence: `test_run_inputs.py`.
- [x] UI introduction/layout review, CLI `-p` default of 1, Compose lifecycle and
  bundled Data Node/TORCH/FDE checks, and the six starter DocumentReference case
  lists were completed in the existing checklist. See `README.md`,
  `CsvCommandLineTest` and `ConverterVariantsTest` for current behavior.
