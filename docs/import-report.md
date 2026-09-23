# Import reports

Each CSV-to-FHIR conversion writes `*.import.json` under `details/reports/`.
`COMPLETE` means the recorded input was processed without detected import errors.
`INCOMPLETE` produces exit code 1, including when FHIR validation is disabled.
Partial output is retained for diagnosis under `details/pending/`.

Findings identify the table, CSV file, logical record number, category and cause.
Record numbers exclude the header and count CSV records, which may span several
physical lines. Repeated conversion attempts also identify the output prefix and
iteration. Error messages may include affected input values.

## Counters

| Field | Meaning |
| --- | --- |
| `rowsRead` | Successfully read CSV records. |
| `emptyRows` | Records considered empty by that table's rules. |
| `processedRows` / `failedRows` | Distinct records with successful/failed processing; a record can appear in both across attempts. |
| `successfulAttempts` / `failedAttempts` | Actual row-converter calls. |
| `returnedResources` | Resources returned across successful calls, counted per attempt. |
| `unprocessedRows` | Read, nonempty records with neither success nor an assigned row error. |
| `rejected` | Table reading or acceptance failed. |

`Person` and `Consent` read the same source sheet and have separate counters.
Use their counts as converter views rather than summing them as distinct input rows.
Malformed CSV can prevent a complete record count. A workbook precheck or command
error that occurs before CSV conversion is reported in the log and exit status.

## Input handling

Checks cover required columns, duplicate headings, field counts, patient references
and conversion errors. An empty patient ID inherits the preceding ID in the same
table; malformed field counts interrupt that inheritance. Patient IDs are compared
as whole strings, case-insensitively.

[Contact checks](contact-input-checks.md) run before bundle generation. Findings
include field and record number; several findings on one record count as one
failed row. `successfulAttempts = 0` and `unprocessedRows` show a precheck abort.
A row-conversion error can leave earlier changes to resources in the partial output.

## Related reports

| Report | Question answered |
| --- | --- |
| `*.loss.json` | Which Synthea source data was projected, substituted or omitted? |
| `*.import.json` | Was the Excel/CSV input fully processed? |
| `*.validation.json` | What did the requested FHIR checks find? |

[FHIR validation](fhir-validation.md) has its own status. An unavailable terminology
check produces `NOT_CHECKED` and exit code 1 when validation is enabled.
