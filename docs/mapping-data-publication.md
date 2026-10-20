# Mapping data and publication

The objective is broad, plausible synthetic mapping coverage with documented
redistribution rights. Mapping choices may be approximate or deliberately more
general or specific for test scenarios. Contradiction guards remain applicable.
Third-party conditions apply to test data too.

## German annual classification targets

ICD-10-GM, OPS and German ATC 2025/2026 targets use the official BfArM editions.
The specific download conditions permit use and distribution as official works
subject to unchanged classification content and attribution:

- [ICD-10-GM and OPS, conditions dated 1 August 2025](https://www.bfarm.de/SharedDocs/Downloads/DE/Kodiersysteme/downloadbedingungen_20250801.pdf?__blob=publicationFile).
  ICD-10-GM also has a restriction on commercial advertising in the works.
- [German ATC conditions linked from the terminology server](https://terminologien.bfarm.de/files/conditions/bfarm.terminologien.atcgm-conditions.xml),
  section 1. The required WIdO/BfArM attribution accompanies machine-readable
  copies. A printed edition requires prior contact with BfArM.

The ATC catalogue landing page and PDF contain a general permission reservation.
The specific official-edition download conditions explicitly provide the usage
basis above. Use those conditions for this source; retain their link with the
attribution. This basis concerns German ATC, not a separate WHO ATC database.

`scripts/terminology-notices.json` contains the classification notices and their
conditions URLs. The source mapping tables carry their target terminology notice.
`build_annual_catalogues.py` places the notices in every annual catalogue entry;
import reports inherit them through `terminology.catalogues`. Keep
[NOTICE.md](../NOTICE.md) with distributed project data and applicable notices
with exported datasets. Exported workbooks/FHIR files alone do not automatically
carry every notice from the accompanying report.

Official target codes and descriptions retain their original content. The
project's approximate mapping decisions and reasons are separate from the
classification. Compare regenerated targets with the official annual source;
changing a target assignment must not rewrite the official meaning of a code.

## SNOMED source references and German text

The project follows the Affiliate-license notice pattern used by the
[MII Oncology module](https://github.com/medizininformatik-initiative/kerndatensatzmodul-onkologie/blob/395e942f71303c5e12292b7b9daa1b3fbbfc6814/input/fsh/rulesets/snomed-copyright.fsh).
SNOMED terminology content retains SNOMED International's rights. Implementers
must have the appropriate SNOMED CT Affiliate license; see
[license and access conditions](https://www.snomed.org/get-snomed).
The project license covers project-authored software and mapping logic, while
third-party terminology conditions accompany the referenced content.

The notice is in `NOTICE.md`, the shared terminology notices, and the source
registry, German text, diagnosis, procedure, observation and operative-subset
mapping files. Import reports include it as `terminology.snomedNotice`. Keep the
notice and conditions link with distributed mapping tables and generated datasets.
The notice states the applicable rights and implementer requirements; it does not
assert that a separate project-specific permission has been obtained.

The source registry and German text table contain 1,247 SNOMED identifiers.
The German labels are project-specific text for synthetic test scenarios, with
editorial, machine-drafted and selected Wikidata provenance recorded per entry.
Approximate mapping decisions, source evidence and contradiction guards remain
part of the published mapping data. Preserve these alongside the notices.

## German LOINC names

The German text catalogue uses unchanged `LONG_COMMON_NAME` values from the
German (Germany) linguistic variant of LOINC 2.83 for 173 of its 446 LOINC
identifiers. Entries without an official German long name retain their project
reading text and its provenance, including answer identifiers. Codes, units and
result values remain unchanged.

Each official entry records the LOINC version, term status and any external
copyright notice. The catalogue and import report identify the source file and
its SHA-256, the distribution archive SHA-256 and the uncovered identifiers.
The [LOINC notice](../NOTICE.md#loinc) and [license](https://loinc.org/license)
apply to the official names.

Refresh the names from a downloaded official distribution with
`python3 scripts/update_loinc_german_texts.py /path/to/Loinc_2.83.zip`.

## Medication product facts

The 495 medication mappings select 330 distinct PZNs. Evidence consists of product
facts (identifier, name, strength and form), not reproduced leaflets or dossiers.
Sources have differing extraction sizes: the September 2026 BfArM fixed-price
list supports 187 distinct PZNs and the TK list supports 50. Counts alone establish
neither infringement nor permission. Review these larger extractions against the
actual source terms or replace their evidence/products from a source that permits
redistribution; their publication basis remains unconfirmed.

An individual manufacturer or G-BA product citation is not by itself a reason to
remove a mapping. Keep fact-only evidence and existing provenance. If replacing a
source, verify the actual product facts against the replacement and record its
URL, version and evidence; do not relabel old evidence as coming from elsewhere.
Optional local commercial catalogues retain their separate redistribution guard.
`no-local-product-data` reports provenance, not license clearance.

## Maintenance

Prefer an attribution or verified source replacement over losing a useful mapping.
Keep source hashes, mapping rationales, annual target verification and contextual
guards. After metadata changes, rebuild the annual subset to refresh source
hashes and verify that all clinical entries and annual targets are unchanged.
Run the existing Python suite and Maven tests. Required notices are collected in
[NOTICE.md](../NOTICE.md); publication questions above must be resolved before
claiming that the entire data collection can be freely redistributed.
