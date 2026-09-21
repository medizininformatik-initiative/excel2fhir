# FHIR validation

FHIR profile and terminology validation is optional and disabled by default. Enable it with `-v` / `--validate-bundles` to validate completed bundles after conversion and post-processing. The same option applies to the Synthea import and generation commands; place it before `--` in the generation command. It retains every converted resource, including resources with validation errors. A `<bundle-name>.validation.json` report under the run’s `details/reports/` directory records all raw messages independently of `-vll`, their severity/location and the application classification. Treat the report and process exit status as part of the output: an existing FHIR file does not imply validation success.

The conversion CLI exits with status 1 if a validation error, validator failure or a recognized terminology-check gap occurred. It completes the remaining conversions before returning this validation status. Conversion/I/O exceptions also fail the command. A successful default conversion is labelled `NOT_VALIDATED` and exits with status 0. Synthea import and roundtrip checks run in both modes.

Classification:

- `ERROR`: retained ERROR or FATAL; FATAL is never ignored.
- `NOT_CHECKED`: an explicitly unavailable CodeSystem or unexpandable/unresolved ValueSet. This does not assert that the code is wrong or correct. It prevents a successful validation exit status, while keeping the original message.
- `IGNORED`: the retained historical OBI identifier warning exception. Unknown-code errors and generic validation failures are no longer suppressed by terminology URL.
- `WARNING` / `VALID`: the remaining HAPI result. Absence of errors does not prove complete terminology coverage or clinical plausibility.

The `strict` argument on the single-resource API controls its additional exception list (currently empty). CLI validation operates on final bundles with their completed relationships.

Counters explicitly distinguish error/warning messages from validation calls. A whole-bundle validation is one call; its report separately gives the entry count. A resource with no messages counts as one valid call. The file-validation API returns a result for every processed file, including read/validation failures. Its standalone CLI also returns nonzero for errors or recognized incomplete checks.

Reports include `referencesWithoutTargetInBundle`. Matching covers relative Type/id references and UUID URNs, against resource ids and entry fullUrls. Relative references can legitimately resolve outside a transaction; therefore this inventory is not automatically classified as an error. For generated self-contained examples, require this map to be empty. Absolute and contained references need their own resolution context; this check does not claim to validate them.

The bundled profiles do not provide a complete SNOMED or LOINC terminology server. Some missing catalogs are reported only as warnings by HAPI. Current known gaps include the international SNOMED edition 2025-07-01, historical versions referenced by the ICD-10-GM ValueSet, and an IPS laboratory ValueSet canonical referenced by the KDS laboratory profile. Do not turn these gaps into broad unknown-code exceptions. German text quality, approximate source-to-target mappings and clinical event chronology require separate review.

## Corrected conversion contracts

The workbook layout and human-readable input columns are unchanged:

- German state names in `Person/Bundesland` become ISO 3166-2:DE codes in FHIR. Already coded values pass through; non-German addresses are not mapped to German states.
- A laboratory `Werttyp=Text` becomes `valueCodeableConcept.text`. Missing `coding.system` and `coding.code` explicitly carry the standard Data Absent Reason extension (`unknown`). No organism or other clinical code is invented. Non-laboratory text observations retain `valueString`.
- The shared DAR extension factory uses the StructureDefinition URL. Actual DAR Coding values still use the CodeSystem URL.
- A medication dosage with both dose and daily frequency, and no free text, remains structured. If a dosage contains free text or only part of the structured information, all provided text/dose/frequency facts are retained in `Dosage.text`. Unknown dose units are named explicitly. This satisfies DosageDE's separation of text and complete structured dosage without inventing a schedule. MedicationAdministration has a different dosage type and retains its existing representation.

Review the textual medication output and the missing-code laboratory fallback as test-data representations. These changes do not claim microbiology-specific modeling or reconstruct discarded source dosage details.
