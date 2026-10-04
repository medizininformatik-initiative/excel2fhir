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

## Properties export and import

The editor exports `converter-configuration.config` as UTF-8 Properties text.
Descriptions, choice explanations, defaults and inactivity reasons use the selected
interface language. Names and values are language independent. The contract's
`propertyName` on each option is its stable uppercase export name. Existing Java
property names are reused where value semantics match; level selectors and other
expanded controls have their own names. The version line is required:

```properties
CONFIGURATION_VERSION = 1

# Patient output and references
# generate-reference: generate the Patient and references to it.
# reference-only: retain references to an existing Patient.
# neither: omit the Patient and references to it.
PATIENT_MODE = generate-reference

MEDICATION_ADMINISTRATION_ENABLED = false

# Inactive because MedicationAdministration output is disabled.
# MEDICATION_ADMINISTRATION_TREATMENT = add-statement
```

One physical text line corresponds to one row in column A of an options sheet.
Comments are wrapped into separate lines. Strings use Java Properties escapes for
backslashes, line breaks, tabs and leading spaces; literal Unicode is retained.
Booleans use `true`/`false`, integers use decimal notation, enums use the contract's
technical values and sets use comma-separated values (an empty set has an empty
right-hand side). Strings are unquoted. All options and all DAR fields are exported,
including defaults. Missing options use contract defaults.

When a control or its selected choice is unavailable, its assignment is prefixed
with `# `. Explicit effective `false` and `none` values remain active assignments.
The web importer reads recognized uppercase assignments in comments as stored
selections and reevaluates dependencies. Ordinary prose comments are ignored.
Duplicate assignments, including active/commented duplicates, are rejected;
edit the existing assignment rather than adding a second one. Unknown assignment
names, malformed values and unsupported versions also fail import.

A plain Java Properties reader ignores commented assignments. The shared
configuration adapter must also restore recognized commented selections before
evaluating dependencies, just as the web importer does. Otherwise an unavailable
selected contact level could fall back to a different, available default level.
Missing assignments use defaults; stored unavailable selections become ineffective
without substituting another choice.

The shared Java reader accepts this format from external files, CSV option files
and Excel option sheets. It validates stored values and determines effective
selections from the same contract. Existing directly equivalent Java properties
are mapped; other effective settings produce explicit preflight errors, including
unsupported defaults. The complete default configuration is therefore not yet
executable. Patient output modes have an internally tested implementation that
projects output after input derivations, preserving internal patient identity and
resource IDs. Remaining execution semantics are tracked in #77. The current web
Start conversion action still uses the existing converter defaults.

`propertiesFormat.darProperties` maps each DAR field ID to its uppercase name.
Each value is `unchanged` or an allowed field-specific DAR code. Missing DAR values
mean `unchanged`; import represents unchanged fields by their absence in the draft.
DAR assignments for resources not selected for output are commented out. The
export includes the field descriptions, allowed codes and clinical conditions.

Identifier rules use consecutive blocks starting at `IDENTIFIER_RULE_1_` with
`ID`, `ENABLED`, `RESOURCES`, `SYSTEM` and `PATTERN` assignments. `ID` preserves the
rule UUID; numbering preserves rule order. All five fields are required for each
block. Resources are comma-separated FHIR resource types. Rules that are disabled
or have no output resource selected retain their settings in commented assignments;
an explicit `ENABLED = false` remains active. Identifier patterns use the syntax
below and Properties escaping for literal backslashes and line breaks.

Import validates the entire draft before replacing the editor contents. JSON is
also accepted for saved drafts; browser persistence uses the configuration object.

## Contacts and references

DocumentReference offers three encounter assignment strategies:

- **Use only supplied encounters:** retain input encounter references and leave
  missing references absent.
- **Derive missing assignments from timestamps** (default): retain supplied
  encounters and use timestamp matching only for missing assignments.
- **Always derive assignments from timestamps:** recompute references even when
  encounters are supplied. If no usable timestamp or matching encounter exists,
  omit the reference, including any supplied reference, and report the reason.

The first two strategies report temporal conflicts without replacing supplied
encounters. Input encounters are identified by the combination of patient ID and case
number. A combination absent from the input is reported as a nonexistent
encounter. In preserving strategies, an unresolved supplied encounter is not
treated as a missing selection and silently replaced by timestamp matching. Contact levels apply to automatic matching;
supplied encounters are not moved to a different level. Selecting no encounter
reference omits the field in every strategy.

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

MedicationRequest replacement runs first. MedicationAdministration and
MedicationStatement each offer one action: retain, additionally create the other
type, or replace with the other type. The latter two require the target resource
to be enabled. These actions run once on a snapshot after request replacement;
resources created during this pass are not transformed again. Both directions
can be configured without recursive conversions. New resources receive distinct IDs. Missing medication facts are omitted. Active
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
