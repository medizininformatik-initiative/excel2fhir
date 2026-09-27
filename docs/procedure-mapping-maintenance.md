# Procedure mappings: behavior and maintenance

The Synthea importer uses `scripts/mappings/synthea-procedures-ops-2026.json` to
produce illustrative OPS 2026 data. Its 429 assessed source concepts include
126 existing OPS mappings and 261 concepts assigned to 65 contextual rule groups.
Forty-one concepts retain source coding and one is excluded. These are concept
counts: actual OPS coverage depends on the patient, episode and available facts.
An OPS companion counts as coverage while retaining the original service.

## Runtime decisions

`scripts/procedure_projection.py` applies the base mappings, regional splits and
chemotherapy blocks. `procedure_context.py` resolves patient and encounter
references, event age, linked reasons, active diagnoses and preceding procedures.
`contextual_procedures.py` filters candidates, selects compatible synthetic
services, assigns their times and merges duplicates. The ordinary import workflow
uses this behavior automatically; the workbook input structure stays the same.

Explicit source-specific candidate restrictions take precedence over weights.
Body-site information takes precedence over linked reasons, which take precedence
over other diagnoses. Ambiguous or missing wound regions retain source coding.
Known acute instability blocks cooperative and stress examinations. Age, pregnancy,
organ absence, route, indication and timing checks apply where configured by each
rule. These checks are deliberately scoped to the encoded source concepts and
recognized terminology labels; they are not a general clinical inference engine.

Global context checks also cover existing mappings. Conflicting patient references,
maternal/reproductive events with incompatible source sex or age, male reproductive
procedures with incompatible source sex, relevant prior hysterectomy, and events
after recorded death produce `context-conflict` with no output row and a loss entry.
The female age range 12–55 is a conservative synthetic dataset default, not a
universal clinical rule. Missing required facts block the affected candidates.
If only a proposed enrichment is incompatible, the importer retains the original
service and records `contextualFallback`. Enrichment requires completed events.

A synthetic replacement has its own OPS coding and description. A
`synthetic-companion` adds a separate service and preserves the original row.
Assumed durations fit inside the existing encounter and before recorded death.
Original and synthetic times are both recorded. A weekly therapy course records
five daily units in `syntheticSchedule` and emits one OPS row spanning that course.
A short or unbounded encounter cannot acquire that course.

Repeated synthetic services share an output by patient, encounter, OPS code and
start instant. Education, rehabilitation, monitoring, counselling, birth
supervision and access code families use one service per stay. A compatible
existing OPS output takes precedence. All contributing source IDs and owner links
remain in the report. Original source services remain separate.

## Replaying a selection

Each candidate has a stable ID and a positive integer weight. Weights provide
illustrative variety; they do not represent disease prevalence or clinical evidence.
For compatible candidates, the importer hashes this UTF-8 JSON array with SHA-256:

```text
["ops-sha256-weighted-v1", patientId, procedureId, sourceSystem, sourceCode, ruleId]
```

Serialization uses no extra whitespace and preserves Unicode. Sort candidates by
ID, sum their weights as `W`, interpret the digest as unsigned integer `H`, and use
`floor(H * W / 2^256)` as the bucket in cumulative weight intervals. An explicit
regional split emits all specified regions instead. Stable source IDs are required;
a missing Procedure ID is reported as an omission.

Input order and unrelated events do not change the draw. Candidate, condition or
weight changes may change that rule's output. Mapping hash, rule revision and
algorithm ID are recorded for audit; the mapping hash and revision do not seed the
draw. Deduplication chooses owners in source-ID order and records shared outputs.

The projection report contains `contextEvidence`, eligible and rejected candidates
with reasons, assumptions, selection identity and digest, original times, output
rows and shared-source links. `procedureMappingSummary` distinguishes source-event
coverage, companion coverage, context conflicts and emitted OPS rows.

## Sources and authored assumptions

Every entry's `provenance` contains its rationale, review status and pinned module
evidence. Evidence includes repository/commit, module path, JSON pointer, state,
file and state hashes, plus source facts used in the decision. The snapshot is
[Synthea commit 1c6d569](https://github.com/astruebi/synthea/tree/1c6d5693c3c95f8ea385b76abe7a2386127a6892),
also pinned in `scripts/synthea-version.txt`. The 429 concepts have 662 evidence
locations across 118 module files.

`targetSource` identifies the official
[BfArM terminal OPS 2026 catalogue](https://terminologien.bfarm.de/rendering_data/ValueSet-ops-terminale-kodes-2026.json)
and its SHA-256 fingerprint. Candidate codes, titles and versions come from that
catalogue. Topic groupings, candidate choices, weights and synthetic assumptions
are authored decisions. Catalogue validation establishes valid terminal codes;
it does not establish clinical equivalence. Review status records that distinction.

## Agent maintenance workflow

1. Read the entry, its `contextualRule`, overrides, guards and provenance. Open the
   pinned module at each JSON pointer and inspect the surrounding source state.
2. Compare source facts and intended service with the terminal catalogue and the
   linked OPS classification rules. Keep the rationale for retained or excluded
   concepts as well as mapped ones. Preserve source evidence when editing weights.
3. Update candidates, explicit restrictions and executable guards together. Use
   stable candidate/rule IDs; increment the rule revision for changed behavior.
   Update the mapping ID and provenance/review status honestly. A new guard also
   needs executable handling and a contradiction test.
4. Recheck the source inventory and target catalogue with the shipped checker,
   passing a checkout of the pinned Synthea revision and a downloaded catalogue:

   ```sh
   python3 scripts/audit_procedure_mapping.py /path/to/synthea /path/to/ValueSet-ops-terminale-kodes-2026.json
   python3 -m unittest discover -s scripts/tests -q
   mvn test
   ```

   The checker verifies all source locations and hashes, detects changed productive
   Procedure inventory, checks required provenance and rule structure, and checks
   every OPS code/title/version. A changed catalogue fingerprint requires review.
5. Exercise representative bundles through the importer. Inspect fallbacks,
   contradictions, companion rows, course times and event-level coverage. The
   independent `audit_procedures.py` / `audit_contextual_procedures.py` checks replay,
   output coding, periods and ownership during source-to-output auditing. Add tests
   for new positive cases, incompatible contexts and missing facts.

Preserve these files in the product so a future agent can reconstruct each
implemented decision. Replaying the stored decisions is deterministic; asking a
new agent to invent mappings from the same sources need not reproduce the same
subjective candidates or weights.
