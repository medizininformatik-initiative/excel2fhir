# Converter option contract

`contract.json` defines the agreed controls, defaults, dependencies and processing
rules for the configuration editor and shared Java converter. The contract records the agreed behavior. Issues #76 and #77 deliver the
editor and converter integration.

The Java converter owns execution semantics. Backend and frontend adapters use
the contract to present settings and pass selected values; they do not implement
contact assignment, transformations or identifier generation independently.
Bindings identify existing Java properties and CLI inputs. A binding does not
claim that the current converter already supports the full target behavior.

## Interface languages

The interface defaults to German and offers English. `de.json` and `en.json`
contain the visible labels, choices, help, disabled-control reasons and interface
messages. The contract references stable text keys; option IDs and values remain
language independent. DAR field/code labels are resolved using the key patterns
in `dar`; its clinical catalogue remains the source for allowed codes and rules.

The language setting affects presentation only. FHIR output retains the German
KDS profiles, terminology bindings and German descriptions/display text. Original
converter logs and validation reports retain their source language. Configuration
schema annotations use English independently of the current interface language.

The localization test checks every required key, rejects missing/unused/duplicate
keys and verifies matching placeholders in both languages. Add a new text key to
both language files when adding a control or message.

## Configuration values

A configuration contains `schemaVersion`, a `values` object keyed by option ID,
a `dar` object keyed by DAR field ID and an `identifierRules` array. Missing
values use contract defaults. JSON Schema describes accepted shapes; the Java
adapter applies defaults and validates clinical dependencies. Unknown keys,
wrong types, unsupported versions and conflicting duplicate values are errors.
Profile names identify saved configuration profiles, separately from FHIR profiles.

Controls remain visible. Disabled selections retain their stored values. The
effective configuration disables unavailable settings and explains why, without
substituting another resource or contact level. Choice dependencies apply to
individual choices as well as entire controls. A selected unavailable parent or
reference target becomes ineffective while its selection is retained.

```json
{
  "schemaVersion": 1,
  "values": {
    "resource.Patient.mode": "reference-only",
    "reference.Condition.encounter": "department",
    "contact.inheritDiagnoses": false,
    "checks.fhirValidation": true,
    "output.formats": ["JSON", "NDJSON"]
  },
  "dar": {},
  "identifierRules": []
}
```

Resource selection controls all output. IDs use the stable potential-resource
sequence, so deselecting a resource does not renumber the retained resources.
Reference-only modes can refer to existing external resources. Medication and
Location can combine an external reference with descriptive identifier/display
information in the same Reference. Clinical timestamps are shifted together in
whole days, including birth date.

## Contacts and references

Matching requires the same patient and selected contact level. Start and end
boundaries are inclusive; a missing end is open. The latest matching start wins,
then original input row order. Preserve that order during processing. If a
timestamp candidate finds no contact, try the next candidate. Missing matches
omit the reference and appear in the report. Contact levels are not substituted.

Immunization uses `occurrenceDateTime`. DiagnosticReport tries
`effectiveDateTime`, then `issued`. CarePlan uses `period.start`. These follow
the same next-candidate and contact matching rules.

Hierarchy is derived from the known input contact structure. When Encounter is
enabled with no selected level, generate one general case Encounter without a
KDS contact-level claim. Operation, consultation and examination/treatment
contacts remain excluded as ward/service clinical reference targets.

The department target and facility diagnosis defaults are project conventions;
generic Encounter targets in profile snapshots do not enforce these levels.

## DAR and transformations

DAR field IDs, clinical code choices, representations and conditional constraints
come from the [DAR catalogue](../dar/README.md). The option contract references
that catalogue rather than copying its 73 field definitions. Every field defaults
to unchanged. An enabled override replaces existing values and table DAR values
after derivations, using the catalogue representation and clearing replaced
values. Fixed/pattern values, coding-system discriminators and profile metadata
are preserved. Condition and Observation constraints still apply; do not remove
other clinical facts to hide a validation failure.

Table code strings remain unrestricted by this target contract, including
unknown `!dar:<code>` tokens. Structural and type checks still apply. Optional
FHIR validation reports invalid vocabulary codes; a valid but clinically
unsuitable DAR code can pass that validator. Editor choices provide the semantic
selection policy.

Medication replacements precede one pass of additions. Additions do not recurse;
new resources receive distinct IDs. Missing medication facts are omitted. Active
DAR overrides are applied after these derivations.

Additional identifiers append to existing identifiers. Each rule has a stable
identity and one counter across eligible resources and repetitions, unaffected
by output selection. The same logical resource serialized in several formats is
counted once. Duplicate system/value pairs across distinct logical resources or
repetitions fail with the rule and conflicting resource identities. Deterministic
hash identifiers do not guarantee secure pseudonymization. Hashes use the first 32 lowercase hexadecimal characters of SHA-256 with
collision checking. Patterns combine literal text with `{count}`, `{count:08}`, `{patientId}`,
`{resourceId}`, `{resourceType}`, `{iteration}` and `{hash}`. The shared counter
starts at 1; the repetition index starts at 0. Padding sets a minimum width, not
a maximum. `{{` and `}}` insert literal braces. Unknown tokens, unmatched braces
and malformed padding are errors; patterns do not evaluate expressions.

## Checks

Generate the structural configuration schema and check definition consistency:

```sh
python3 web/catalog/options/generate_schema.py
python3 web/catalog/options/generate_schema.py --check
python3 -m unittest discover -s web/catalog/options/tests -v
```

Verify that every existing Java option is represented, directly or by an explicit
adapter mapping, against a built converter:

```sh
java --class-path target/excel2fhir.jar web/catalog/options/tests/CheckJavaBindings.java
```

Keep implementation and acceptance in #76/#77 distinct from this definition.
Parallelism and environment selection are execution settings outside the clinical
option contract.
