# Catalogue years and coding versions

The Synthea import selects German ICD-10-GM, OPS and ATC codes using one catalogue
year: **2025 or 2026**. The default is 2026. Event dates remain clinical dates and
do not select a catalogue. SNOMED CT, LOINC, product identifiers and ingredient
identifiers keep their own terminology conventions.

## Synthea settings

Set these values in the existing Converter Options file before importing:

```properties
SYNTHEA_MAPPING_YEAR = 2025
SYNTHEA_VERSION_OUTPUT = Jahr
```

`SYNTHEA_MAPPING_YEAR` selects the actual target codes and descriptions.
`SYNTHEA_VERSION_OUTPUT` controls the representation of their version:

| Value | FHIR output |
| --- | --- |
| `Jahr` | `version` contains the selected catalogue year. |
| A readable DAR label, for example `Unbekannt (Data Absent Reason)` | `_version.extension` contains that Data Absent Reason. |
| Empty value after `=` | Both `version` and `_version` are omitted. |

All 15 DAR choices from `workbook-absent-reasons.json` are supported. Configuration
files accept `!dar:<code>`. Select the catalogue year and version-output policy
in the editor or in the external configuration. One run uses one configuration;
generated workbooks contain the resulting case data.

The mapping report retains the actual target catalogue year even when the output
omits the version or uses DAR. `terminology` records the selected year, output
policy, catalogue URLs and fingerprints. Explicit ICD-10-GM coding already in a
source bundle takes precedence over an added mapping and retains its source
edition in year-output mode.

## Editing a workbook

`Diagnose` and `Prozedur` have `Version` and `Zusatzversion` fields. `Impfung` has
`Version`; `Medikation` has `ATC-Version`. Each version applies to its corresponding
coding. Code-system selections and version inputs are separate.

The dropdown suggests 2025, 2026 and all readable DAR labels. Ordinary text,
including another year or a custom edition string, is copied into `Coding.version`.
A DAR selection creates an extension on that primitive. An empty cell omits the
version entirely; the converter does not emit an empty JSON string.

Changing a version cell changes its FHIR representation; it does not remap the
code. The Synthea settings are import settings and do not rewrite manually entered
rows during Excel-to-FHIR conversion. Profile validation checks the resulting
resource against the loaded packages, including required versions and terminology
bindings. The bundled KDS diagnosis, procedure and medication profiles require
versions for ICD-10-GM, OPS and ATC codings. Omission therefore produces profile
errors. A DAR extension supplies the primitive element, but does not establish
terminology membership.

The medication package `2026.0.1` binds ATC codings to a required ValueSet that
includes editions 2018–2025. An explicit ATC version `2026` is outside that binding,
even when the code exists in the official 2026 catalogue. The base package
`2026.0.1` includes 2026 in its ICD-10-GM and OPS ValueSets. Package release years
and permitted terminology editions are separate. Missing terminology content can
prevent the validator from resolving these bindings; `NOT_CHECKED` does not
establish conformance. See [terminology coverage](fhir-validation.md#terminology-coverage).

## Maintaining the annual mappings

The clinical mapping tables describe provisional synthetic interpretations and
retain their source evidence. `scripts/mappings/annual-catalogues.json` supplies
verified annual targets for those decisions. It records the source mapping hashes,
official BfArM rendering-data URLs, catalogue hashes and a reason for every target.
The code applies this table after clinical/contextual selection, preserving the
contradiction guards and source evidence.

Four 2026 ICD-10-GM subdivisions use broader terminal categories in 2025:

| 2026 | 2025 |
| --- | --- |
| R53.9 | R53 |
| R73.08 | R73.0 |
| R76.88 | R76.8 |
| Z98.88 | Z98.8 |

All mapped OPS and ATC codes exist in both supported catalogues. Descriptions
come from the selected annual catalogue. Catalogue membership establishes code
availability; it does not establish clinical equivalence of the source mapping.

To reproduce or update the subset:

1. Download the six JSON files from the URLs in `annual-catalogues.json` into an
   external directory, using filenames `icd10gm-2025.json`, `ops-2025.json`,
   `atcgm-2025.json` and their 2026 equivalents. Preserve the original bytes.
2. Review any changed catalogue fingerprints and source mapping decisions. The
   explicit substitutions are in `scripts/build_annual_catalogues.py`.
3. Run `python3 scripts/build_annual_catalogues.py /path/to/catalogues /path/to/rebuilt.json`
   and compare the result with the committed subset. The builder rejects missing
   and nonterminal targets. Review every changed code and description before
   replacing the committed file.
4. Run `mvn test package` and `python3 -m unittest discover -s scripts/tests`.
   Exercise both years with year, DAR and omitted output, including the independent
   source/Excel/FHIR audit. The tests detect changes to the underlying mapping files
   that require refreshing the subset.

For the clinical decision evidence, see [diagnosis mappings](diagnosis-mapping.md),
[procedure maintenance](procedure-mapping-maintenance.md) and
[medication mappings](medication-product-catalog.md).
