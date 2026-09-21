# Contact input checks

The `Fall` sheet describes facility contacts, departments and care locations.
The converter derives contact IDs and parent relationships from row order and
clinical fields. See [contact entry and periods](synthea-movements.md) for examples.

Contact type describes the care-location contact; ward, room and bed describe its
location. An operation may have a bed, and a consultation may take place on the
ward. Secondary contacts can overlap their primary stay and each other.

## Input requirements

| Field or relationship | Rule |
| --- | --- |
| Start and end | Start must be readable. A supplied end must be readable and at or after start. |
| Parent period | A child starts within the parent's period and ends by its known end. |
| Primary stays | Enter them in chronological order with distinct, non-overlapping periods. |
| Secondary contact | Place it after its primary care-location stay and before the next primary stay. |
| Contact type | Use a supported value and supply at least one ward, room or bed. |
| Contact class | Use the same facility-contact class throughout a case. |
| Admission reason | Enter it on the facility row with an explicit case number. |
| Patient and case | Each contact must resolve to a known patient and case. |

A single contact can have equal start and end, subject to its parent's period.
A location contact can use a room or bed alone and can link directly to the facility
when a department is absent. Ends remain open when the derivation rules have no
known boundary. Class/type combinations are accepted independently of clinical
plausibility.

## Error reporting

The Excel precheck collects findings with sheet, row and field. Excel and direct
CSV processing use the same contact checks. When an error prevents reliable
relationship checks for a case, the report identifies that limit; independent
fields and other cases continue to be checked.

Input errors stop FHIR generation for the affected conversion. CSV processing
records `CONTACT_INPUT_ERROR` findings in the [import report](import-report.md).
Unexpected conversion errors produce an incomplete import, with partial output
available for diagnosis. The CSV contact checks also apply when workbook
consistency checking is disabled.

[FHIR validation](fhir-validation.md) separately checks the generated resources
against profiles and terminology.
