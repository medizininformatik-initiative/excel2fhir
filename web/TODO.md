# Configuration workbench TODOs

This checklist records the agreed behavior and remaining implementation work.
GUI code, catalogues, tests and documentation live under `web/`. Shared converter
behavior remains in the Java converter. Existing issue references describe the
planned work streams; this checklist does not change their status.

## DAR and table input — #75 / #77

- [x] Generate a JSON DAR catalogue from local 2026 profiles and maintained semantic field groups.
- [x] Record source fingerprints, profile evidence and conditions; document regeneration and extension in [DAR maintenance](catalog/dar/README.md).
- [x] Add representative validator checks and generator tests under `catalog/dar/tests/`.
- [ ] Connect the generated catalogue to the API and configuration editor.
- [ ] Offer only clinically suitable standard DAR codes in option controls. The underlying vocabulary has 15 codes; each field offers its reviewed subset.
- [ ] Keep input-table codes unrestricted, including intentionally invalid ordinary codes and arbitrary `!dar:<code>` values. Remove early rejection of unknown DAR codes from parsing, medication/diagnosis checks and input consistency checks. Preserve normal structural and type checks.
- [ ] Represent arbitrary table DAR codes as the standard DAR extension, or Observation `dataAbsentReason` where appropriate, without enum parsing that rejects unknown code strings.
- [ ] Add table-input regressions proving unknown DAR codes can reach output with FHIR validation disabled, and that the enabled FHIR validator reports them.
- [ ] Document that valid but clinically unsuitable DAR codes may pass FHIR validation; the option catalogue supplies the semantic selection policy.
- [ ] Make active DAR overrides always overwrite existing values and table-entered DAR after derivations. Default all overrides to unchanged/off.
- [ ] Clear the concrete replaced value and use the catalogue's representation. For Observation/component values, remove the entire `value[x]` and set `dataAbsentReason`.
- [ ] Preserve fixed/pattern profile data and coding-system discriminators. Verify contextual constraints such as Condition con-4/con-5 and Observation obs-6/mii-lab-2 with additional fixtures.
- [ ] Do not add a separate invalid-code GUI mode: unrestricted test codes belong to table input; option selectors retain suitable choices.

## Form structure and defaults — #75 / #76

- [ ] Define a versioned machine-readable option contract for labels, controls, defaults, choices, help, dependencies and selected values. Keep converter semantics in shared Java code.
- [ ] Put all resource selections/options on one continuous page, grouped by resource category, with Medication as its own section.
- [ ] Use separate tabs for DAR, IDs/repetitions, terminology/versions and additional identifiers.
- [ ] Keep controls permanently visible with a stable layout; disable unavailable options with a reason and retain their values. Add section anchors; use no accordions.
- [ ] Provide longer help via an info icon on hover, keyboard focus and click.
- [ ] Use checkboxes for independent choices, radio buttons for short exclusive choices and choice lists for longer selections.
- [ ] Default all supported resources to enabled. Default Patient, Medication and Location to generate and reference.
- [ ] In reference-only mode for Medication/Location, default existing-resource reference on and descriptive identifier/display off. Allow both independently and combine them in one Reference.
- [ ] Keep resource selection authoritative across all replacements, additions, derivations and DAR transformations. Never emit an unselected resource.
- [ ] Cover Patient, Encounter, Location, Condition, Procedure, laboratory and vital-signs Observation, Medication/Request/Administration/Statement, Immunization, DiagnosticReport, CarePlan, DocumentReference and Consent supported by the converter.
- [ ] Use the bundled 2026 profile baseline. Treat package/profile versions separately from terminology years; use a profile only when the emitted resource claims it.

## Contacts and clinical references — #75 / #77

- [ ] Enable all three contact levels by default; use ward/service -> department -> facility as the default hierarchy.
- [ ] Label hierarchy controls “ist Teil von (partOf)”. Derive hierarchy during generation from the known contact structure.
- [ ] Allow department parent none/facility and ward/service parent none/department/facility. Apply no automatic parent-level fallback.
- [ ] Default clinical Encounter references to department; offer facility, department, ward/service or none per clinical resource.
- [ ] Assign by same patient, selected level and inclusive time boundaries. Missing contact end is open. Select latest matching start, then original input row order; preserve that order through processing.
- [ ] Preserve the agreed exclusions of operation, consultation and examination/treatment contacts as ward/service reference targets.
- [ ] Support a generic case Encounter without a false KDS contact-level claim when Encounter is enabled but no contact level is selected.
- [ ] Use resource timestamp candidates: Observation effectiveDateTime; Procedure performedPeriod.start then performedDateTime; Administration/Statement effectivePeriod.start then effectiveDateTime; Request authoredOn; Condition recordedDate. Try the next candidate when the previous one finds no matching contact.
- [ ] Finalize remaining proposed candidates: Immunization occurrenceDateTime; DiagnosticReport effectiveDateTime then issued; CarePlan period.start.
- [ ] For DocumentReference, use explicit output time then reliably entered document creation time as a fixed fallback. Avoid current time/filesystem copy times for clinical matching.
- [ ] Add the optional known-input-contact fallback for DocumentReference without a usable timestamp, default off. Spell out DocumentReference in labels/documentation.
- [ ] Default diagnosis and procedure-related diagnosis references to facility contacts only; allow other levels optionally. Default parent diagnosis inheritance off.
- [ ] Explain that the selected contact conventions follow the agreed implementation-guide defaults; do not claim generic snapshot target references enforce these levels.

## Medication transformations — #77

- [ ] Offer Request retain/omit/replace with Administration/replace with Statement; default retain.
- [ ] Offer additional Statement from Administration and additional Administration from Statement; default both off.
- [ ] Apply replacements before one pass of additions; avoid recursive additions. Disable incompatible controls according to selected output resources.

## IDs, repetitions and terminology — #75 / #77 / #79

- [x] Set `-p` default to 1 in both Excel and CSV CLI entry points; preserve explicit overrides and test patient splitting.
- [ ] Retain existing resource-ID schemes/start defaults. Resource selection must not renumber retained resources.
- [ ] Expose PID prefix/suffix, initial offset, per-repetition offset and repetition count using existing semantics.
- [ ] Shift all clinical dates/times including birth date together in whole days, with base and per-repetition offsets.
- [ ] Offer Synthea mapping years 2025/2026, default 2026. Output the actual selected coding version by default; support omission and DAR overrides.
- [ ] Keep arbitrary explicit table versions allowed; never restrict table input to the two Synthea years.

## Additional generated identifiers — #75 / #77

- [ ] Add a dedicated tab with multiple enabled rules, resource multiselect, Identifier.system, value pattern, preview and help.
- [ ] Append generated identifiers while retaining existing ones; offer only resource types supporting identifier.
- [ ] Use a shared deterministic counter per rule across resource types and repetitions, unaffected by output selection.
- [ ] Automatically incorporate stable rule identity, resource type, resource ID and repetition index into hash input using unambiguous encoding.
- [ ] Check uniqueness of system/value pairs across generated resources/repetitions and report collisions. Account for repeated serialization of the same logical resource into different formats.
- [ ] Finalize pattern grammar, counter start and hash length with the user. Proposed tokens: count/zero-padding, patientId, resourceId, resourceType, iteration and hash plus literal text. Proposed counter start: 1; proposed hash: full SHA-256. These details are not yet approved.
- [ ] Explain that a deterministic hash identifier is not a secure pseudonymization guarantee.

## Checks, output and integration

- [ ] Default input consistency checking on and FHIR validation off; allow both to be toggled.
- [ ] Default JSON and NDJSON output on, maximum patients per output file 1; expose other existing output formats.
- [ ] Keep failed/incomplete FHIR validation visible in reports and preserve generated data according to the existing converter behavior.
- [x] Complete #74 Compose lifecycle and pinned Data Node/TORCH/FDE data-plane runtime checks using the bundled demo. See [workbench](README.md).
- [x] Correct the six starter DocumentReference case lists to text `1,2` in the template and bundled copy. Verify conversion, all reference targets, ten Blaze transactions and readback of all 272 unique resources with referential integrity enabled.
- [ ] Complete the separately scoped full Keycloak/OAuth, FLARE, external terminology and DSF integration.
