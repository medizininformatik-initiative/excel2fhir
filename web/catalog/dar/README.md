# DAR field catalogue

`web/catalog/dar/generated/catalog.json` describes the Data Absent Reason
choices for supported converter fields. It is the shared contract for the
configuration editor and Java output overrides. `DarOverrides` applies selected
rules after resource derivations and contact assignment, before serialization.

## Sources and derivation

Run `python3 web/catalog/dar/generator/generate_catalog.py` to regenerate the catalogue.
Run `python3 web/catalog/dar/generator/generate_catalog.py --check` to verify that the committed
file matches its sources. The generator requires only the Python standard library.

The sources are:

- `web/catalog/dar/source/field-rules.json`: supported field paths, semantic groups, replacement
  representations, preconditions and German-profile associations.
- `src/main/resources/workbook-absent-reasons.json`: the 15 standard codes and labels.
- The local archives listed in `src/main/resources/fhir-packages.txt`, in the same
  order as the Java package loader. The current KDS baseline is the bundled 2026
  package set described in [FHIR packages](../../../docs/fhir-packages.md).

The generator resolves resource profiles and datatype descendants, reads snapshots,
merges differentials with their base definitions where needed, and records field
cardinalities, types and profile constraints. It rejects unresolved or forbidden
fields and direct fixed/pattern values. Ancestor patterns are also checked for
literal values at the target path. This extraction is deliberately limited: it is
not a replacement for HAPI snapshot generation or the FHIR instance validator.

The catalogue records SHA-256 digests for the source rules, labels, generator and
all loaded archives. Its output has no timestamp and is deterministic for the same
sources. The validation probe detects changed source digests.

## Meaning and replacement

The semantic allowlists are project decisions, not additional HL7 prohibitions.
A numeric measurement can offer NaN and infinity reasons; a name or coding version
cannot. Workflow-dependent reasons must describe the actual situation. `as-text`
requires the content to be present in the resource narrative, not merely in an
arbitrary text property. These contextual conditions cannot be inferred reliably
from a primitive datatype or a ValueSet binding.

Every field defaults to `unchanged`. Primitive replacements clear the value and use
`http://hl7.org/fhir/StructureDefinition/data-absent-reason`. Choice elements require
the concrete datatype produced by the converter. Complex replacements must retain
required children/patterns or replace only their supported descendants; the recorded
representation and conditions define the intended operation.

For Observation and its components, replace the entire measurement `value[x]` and
set `dataAbsentReason`. Numeric and nonnumeric measurement variants have different
code lists; select the variant using the original measurement type. Preserve
fixed laboratory category codings and Coding.system slice discriminators.

Profile constraints remain authoritative. For example, Condition `con-4` requires
an actual inactive/remission/resolved status when abatement exists; replacing that
status with DAR can make the resource invalid. `con-5`, `obs-6` and the laboratory
`mii-lab-2` condition must also be respected. Generated temporal references are
calculated before DAR overrides. Clearing attachment bytes requires consistent
handling of the corresponding URL, size and hash.

`applyTo` describes the intended occurrence scope. Existing coding occurrences are
updated without synthesizing new coding systems or repeated items. Missing scalar
fields and choices need a known concrete type before an override can create them;
the runtime uses the converter’s dateTime representation for missing scalar
choices. Repeated parents are not synthesized. Observation measurement variants
are selected from the original value type; an absent value has no inferable
variant and remains unchanged. `as-text` requires nonempty resource narrative;
the user remains responsible for its clinical content. Condition DAR overrides are
applied even when the resulting status/abatement combination violates `con-4` or
`con-5`. Roundtrip comparisons verify the requested replacement; enable FHIR
validation to check the generated resources against these constraints.

## Adding a field or changing the code selection

1. Identify the actual resource type/profile emitted by the converter and its exact
   element path. A bundled profile is used only when the resource claims that
   profile; generic Immunization, CarePlan and DocumentReference use the R4 base.
2. Add a rule to `web/catalog/dar/source/field-rules.json` with a stable unique ID, target paths,
   semantic group, representation, cleared fields and any contextual conditions.
   Reuse a group only when its reasons have the same clinical meaning. Define a
   new group when necessary; do not broaden unrelated fields to accommodate one.
3. Check the complete inherited constraints, required slices and literal patterns.
   Preserve required systems and structural profile data. For complex/choice
   fields, specify which concrete type is replaced and how related values behave.
4. Regenerate the catalogue and review the JSON diff, including profile evidence.
5. Add focused validator examples covering the new representation and relevant
   incompatible combinations. Run `python3 web/catalog/dar/generator/generate_catalog.py --check`
   and the verification commands below. Review semantic suitability separately: the FHIR validator does
   not necessarily reject a valid DAR code used in a clinically unsuitable field.
6. Commit the rules, generated catalogue, tests and relevant documentation together.

For a package update, follow [FHIR packages](../../../docs/fhir-packages.md), regenerate the
catalogue, review changed evidence and constraints, and rerun the verification commands below.

## Verification coverage

`tests/ValidateDarCatalog.java` checks source fingerprints and semantic numeric-code filtering,
and exercises representative primitive DAR and Observation absence representations
with the existing bundled-package validator. It also checks incompatible
Observation value/absence and Condition status/end combinations. These tests do
not certify every field/code combination; each extension of the catalogue needs
appropriate new fixtures. `DarOverridesTest` additionally checks runtime replacement, source preservation,
coding discriminators, narrative requirements, choice types and attachment metadata.

## Verification commands

Run from the repository root:

```sh
python3 web/catalog/dar/generator/generate_catalog.py --check
python3 web/catalog/dar/tests/test_generate_catalog.py
mvn clean test package
java --class-path target/excel2fhir.jar web/catalog/dar/tests/ValidateDarCatalog.java
```

The JSON is bundled as a converter JAR resource and used by the editor. The Java probe uses
the built converter JAR and its existing validator without adding GUI tests to
the converter source tree.
