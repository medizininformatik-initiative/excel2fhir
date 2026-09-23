# Stays and secondary contacts

The `Fall` sheet uses case number, period, department and location to describe
contacts. The converter generates IDs, contact levels and parent references.
These rules apply to manual workbooks and generated input.

## Entering contacts

- Start a case with `Patient-ID`, `Fall-Nr`, `Start`, optional `Ende` and
  `Einrichtungskontaktklasse` (facility-contact class).
- `Fachabteilung` creates a department contact. Repeating the same department or
  entering a location change continues that department.
- A ward, room or bed creates a care-location contact. With a department it links
  to that department; otherwise it links directly to the facility contact.
- Empty contact type, `Normalstationär` or `Intensivstationär` describes a primary stay.
- `Operation`, `Untersuchung und Behandlung` or `Konsil` describes an additional
  secondary contact associated with the primary stay.

Order primary stays chronologically. Place their secondary contacts immediately
after them and before the next primary stay. An empty case number continues the
current case; the facility-contact class stays consistent throughout that case.
A department on a secondary row describes the performing specialty.

## Periods

An explicit secondary end is preserved. Otherwise it uses the primary stay's end.
A primary stay with an empty end ends at the next primary stay or the known
facility end, whichever comes first. With neither boundary available, the stay
and its dependent secondary contacts remain open.

A facility-only row defines the overall case period. If the first case row also
contains a department or location, it describes the first stay, and later primary
stays extend the facility period. Derived ends are recorded in
`contactEndDerivations`, with the reference and source row.

Example using one patient ID throughout:

| Fall-Nr | Start | Ende | Einrichtungskontaktklasse | Station | Zimmer | Bett | Kontaktart |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1001 | 2026-05-01 08:00 | 2026-05-05 12:00 | stationaer | | | | |
| 1001 | 2026-05-01 08:00 | | | Ward A | 12 | 2 | Normalstationär |
| 1001 | 2026-05-02 10:00 | | | Operating room | 1 | | Operation |
| 1001 | 2026-05-03 09:00 | | | Intensive care | 1 | 1 | Intensivstationär |

Ward A and the operating-room contact end when intensive care starts on 3 May
at 09:00. Intensive care ends with the facility contact on 5 May. All three
location contacts link directly to the facility because the department is empty.
Procedure times are entered separately in `Prozedur`.

[Contact input checks](contact-input-checks.md) describe period and ordering errors.

## Synthea enrichment

`scripts/synthea_movements.py` generates reproducible primary movements within
completed source encounters. The report records the seed, rule version and
synthetic assumptions for department selection and room and bed changes.
Open encounters retain their source representation.

Department selection uses specific source specialties, mapped operations and
time-compatible diagnoses. The rules in
[`synthea-departments.json`](../scripts/mappings/synthea-departments.json) group
existing ICD-10-GM mappings into candidate departments and apply age at admission
and clinical eligibility rules. Equally suitable departments are sampled
reproducibly. When the evidence yields no suitable department, the fallback is paediatrics
for children and internal medicine for adults. Missing age uses internal medicine.
These are approximate rules for synthetic data.

The department stays constant during the primary stay. Ward names use its prefix
and the number `1`, such as `Station CH1`; room and bed changes provide movement
variation. Intensive care requires specific source specialty evidence.
`movements.departmentDecisions` records candidates, evidence, rejected choices,
unmapped codes and fallback decisions in each case's `Fall.loss.json`.

The operative subset in `synthea-operative-procedures.json` adds operating-room
rows. Procedure start supplies the synthetic contact start. The empty contact end
is resolved by the shared rules above; procedure times retain their source values.
The report links source procedures and expected contact ends.

The KDS model uses `Encounter.partOf` for parent contacts, plus the code systems
`http://fhir.de/CodeSystem/kontaktart-de` and
`http://fhir.de/CodeSystem/Kontaktebene`. See the
[KDS contact guide](https://medizininformatik-initiative.github.io/kerndatensatz-basis/de/StructureDefinition-mii-pr-fall-kontakt-gesundheitseinrichtung.html).
