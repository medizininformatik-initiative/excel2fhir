# Supported Synthea input

The importer fills editable Excel workbooks from Synthea R4 bundles, using
versioned German mappings and documented synthetic assumptions. The shared
Excel/CSV converter generates the selected KDS variants from those workbooks.
See [running the workflow](synthea-workflow.md) or [importing bundles](synthea-manual.md).

## Resource coverage

| Source resource | Workbook sheet | Imported content |
| --- | --- | --- |
| Patient | `Person` | Synthetic German name/address, source birth date, sex and death time. |
| Encounter | `Fall` | Class, period, patient association and emergency admission reason, plus synthetic movements. |
| Condition | `Diagnose` | Source and mapped coding, documentation/onset/abatement times, status and case association. |
| Procedure | `Prozedur` | Assessed OPS/SNOMED coding, period, status, category and patient/case association. |
| Observation | `Laborbefund` / `Klinische Dokumentation` | Numeric, text, coded and Boolean values, missing reasons, components, category, status, effective/issued times and UCUM units. |
| MedicationRequest / MedicationAdministration | `Medikation` | Product, status, intent, event times, dosage text, first dose and simple daily frequency. |
| Medication | Product fields in medication rows | Referenced definitions, German product selection and ingredient information. |
| Immunization | `Impfung` | ATC 2026 classification, vaccine text, time, status, primary-source flag and patient/case association. |
| DiagnosticReport | `Befundbericht` | First coding, times, status, resolvable result references and conclusion. |
| CarePlan | `Behandlungsplan` | Clinical SNOMED category, period, status, intent, description and activity codes. |
| DocumentReference | `DocumentReference` | First document type, status, date, first contact and embedded UTF-8 text, up to Excel's 32,767-character cell limit. |

`Fall.loss.json` records omitted resources and unsupported details with source IDs.
This includes AllergyIntolerance, Device, ImagingStudy, SupplyDelivery, CareTeam,
Claim, ExplanationOfBenefit and source Provenance. Original source bundles remain
available alongside the workbooks.

## Mapping inventory

`scripts/mappings/synthea-source-code-registry.json` inventories source code/system
pairs and provenance for the pinned generator. Mapping decisions live in the
resource-specific tables:

- [Diagnoses](diagnosis-mapping.md): assessed ICD-10-GM 2026 assignments, retained
  source concepts, exclusions and provisional statuses.
- [Medication](medication-product-catalog.md): German ATC, PZN and UNII assignments
  with dose policies and source evidence.
- [Procedures](procedure-mapping-maintenance.md): OPS assignments, contextual
  candidates, source provenance, regional splits and therapy blocks.
- [German names and addresses](synthea-german-demographics.md) and
  [clinical text](synthea-german-texts.md).

The source registry can be rebuilt from the corresponding external inventory and
source checkout using `build_synthea_code_registry.py`. A generator revision
change requires reassessing inventory coverage.

## Procedure projection

The procedure table covers 429 source concepts: 428 Procedure-State concepts and
a combined thorax/abdomen/pelvis CT found in generated output. Single-code OPS
assignments use terminal OPS 2026 descriptions. Source SNOMED concepts are retained
where appropriate; the report records every synthetic assumption and output row.

Combined CT examinations create one row per body region. Radiochemotherapy creates
radiation fractions and one chemotherapy entry per encounter and treatment block.
Blocks use treatment days and distinct parenteral cytotoxic ingredients from linked
administrations; at least two full intervening rest days start a new block.
When administrations are unavailable, the mapping records an assumed intravenous
substance. Longer blocks involving 5-FU, ARA-C, azacitidine or decitabine retain
source events when dose/infusion details needed for OPS classification are absent.

Regional CT rows and radiation fractions retain their source time window. A
chemotherapy block spans its first through last event. Split rows retain source
codes and IDs in the report, with their own target coding in Excel.

Contextual rules enrich 261 additional source concepts with compatible OPS
replacements or separate companion services. Selection is deterministic per source
event, with guards for age, reproductive context, anatomy, indication and time.
Conflicting source events are reported and withheld; incompatible enrichments retain
the source service. The report records rejected candidates, assumptions, selection
seeds, synthetic periods and shared output links. See
[procedure mapping behavior and maintenance](procedure-mapping-maintenance.md) for
precise defaults, coverage interpretation and reproducible source checks.
The metabolic-panel procedure is recorded as omitted while its laboratory results
and reports are retained.

The table records sources for these decisions, including the
[OPS chemotherapy/radiotherapy rules](https://klassifikationen.bfarm.de/ops/kode-suche/htmlops2026/block-8-52...8-54.htm).
Clinical review assesses the appropriateness of each synthetic interpretation.

## Observations and documents

Observation components follow the main row and reference its `Untersuchung ID`.
Diagnostic reports use the same IDs. The converter creates patient-specific FHIR
IDs and links the results. See [workbook input fields](template-input-contracts.md).

For recognized LogMAR visual-acuity observations, the mapping uses LOINC 6617-5
(left) or 6616-7 (right) ahead of SNOMED and preserves the numeric value. The report
records the source and replaced additional codings. LOINC codes and names are
copyright Regenstrief Institute, Inc.; see the [LOINC license](https://loinc.org/license).

The importer stores codes, IDs and FHIR times in text cells. CSV quoting preserves
line breaks, quotes and surrounding whitespace in document text. German identity
and text projection are documented in their dedicated guides.

## Verification

The automatic source comparison checks supported resource counts, coding, expected
mapping additions, values, components, periods, products, document text and local
references. It uses the effective Converter Options for each KDS variant.

`audit_synthea_projection.py` independently reads source bundles, Excel and target
FHIR for standard-ID, single-patient output. `audit_procedures.py` checks regional
splits, therapy blocks, ingredient counts, periods and source-to-row links.

FHIR profile and terminology validation is enabled separately with `-v`.
Review mapping assumptions, dosage choices and text quality in the generated
workbook and projection report as part of accepting a synthetic scenario.
