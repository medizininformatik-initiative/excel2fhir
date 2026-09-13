# FHIR validation

`-v` validates completed bundles after conversion and post-processing. It retains every converted resource, including resources with validation errors. A `<bundle-name>.validation.json` report beside each output records all raw messages independently of `-vll`, their severity/location and the application classification. Treat the report and process exit status as part of the output: an existing FHIR file does not imply validation success.

The conversion CLI exits with status 1 if a validation error, validator failure or a recognized terminology-check gap occurred. It completes the remaining conversions before returning this validation status. Conversion/I/O exceptions also fail the command. Without `-v`, no profile validation is performed.

Classification:

- `ERROR`: retained ERROR or FATAL; FATAL is never ignored.
- `NOT_CHECKED`: an explicitly unavailable CodeSystem or unexpandable/unresolved ValueSet. This does not assert that the code is wrong or correct. It prevents a successful validation exit status, while keeping the original message.
- `IGNORED`: the retained historical OBI identifier warning exception. Unknown-code errors and generic validation failures are no longer suppressed by terminology URL.
- `WARNING` / `VALID`: the remaining HAPI result. Absence of errors does not prove complete terminology coverage or clinical plausibility.

The legacy `strict` argument concerns the additional single-resource exception list only (currently empty); it never meant “disable all exceptions”. Conversion now validates final bundles, not incomplete resources before their relationships have been established.

Counters explicitly distinguish error/warning messages from validation calls. A whole-bundle validation is one call; its report separately gives the entry count. A resource with no messages counts as one valid call. The file-validation API returns a result for every processed file, including read/validation failures. Its standalone CLI also returns nonzero for errors or recognized incomplete checks.

Reports include `referencesWithoutTargetInBundle`. Matching covers relative Type/id references and UUID URNs, against resource ids and entry fullUrls. Relative references can legitimately resolve outside a transaction; therefore this inventory is not automatically classified as an error. For generated self-contained examples, require this map to be empty. Absolute and contained references need their own resolution context; this check does not claim to validate them.

The bundled profiles do not provide a complete SNOMED or LOINC terminology server. Some missing catalogs are reported only as warnings by HAPI. Current known gaps include the international SNOMED edition 2025-07-01, historical versions referenced by the ICD-10-GM ValueSet, and an IPS laboratory ValueSet canonical referenced by the KDS laboratory profile. Do not turn these gaps into broad unknown-code exceptions. German text quality, approximate source-to-target mappings and clinical event chronology require separate review.
