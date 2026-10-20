# German medication data

## Public Synthea mapping

`scripts/mappings/synthea-medications-de-2026.json` covers 495 RxNorm concepts from
the pinned source inventory. The table records German ATC 2026 assignments, publicly sourced UNII ingredient
identifiers and German PZN candidates with product name and form. These are curated synthetic-data choices requiring clinical review.

The mapping supplies German product information to Excel and keeps RxNorm in the
source report. Unknown concepts retain their event, description and explicit
source form, with unresolved PZN/ATC assignments identified in the text.
Semicolon-separated UNII values become separate ingredients. The shared Excel/CSV
converter also accepts manually supplied product and ingredient systems as described
in the [input contract](template-input-contracts.md#medication).

ATC uses the selected 2025 or 2026 catalogue; [version handling](terminology-versions.md)
controls its separate FHIR representation. Product evidence includes public BfArM, insurer,
manufacturer and G-BA documents. URLs, hashes and PDF pages are recorded in the
table. Ingredient evidence comes from NIH RxNav and FDA UNII sources.

## Dose and product choices

Existing source dosage takes precedence over product enrichment, including text-only,
as-needed, partial and multiple dosage instructions. The workbook transfers its
supported dose, unit, daily frequency and text fields; `clinicalMappings.sourceDosage`
retains the full source dosage. Other dosage details remain explicitly reported as
outside the workbook contract. Missing units are not filled from a candidate pack.

The public catalogue supplies German representatives for synthetic patient stories.
Available product, ATC and ingredient mappings are emitted even when they differ
in strength, active substance, formulation or regimen from the source. Medical
plausibility is not an exclusion criterion for generated test data.

`sourceDoseReview` contains informational notes about these differences. The notes
do not suppress fields or resources. Existing dosage values, units, frequency and
text are preserved; the full source dosage remains in the import report. A synthetic
regimen is supplied only when the source has no dosage facts. Missing dosage units
remain missing.

`clinicalMappings.productApproximation` identifies the selected representative.
The recorded nominal strength arrays are provenance, not conversion or rejection
rules. Local product selections also apply when their dose compatibility is unresolved;
available public classifications remain alongside the local product provenance.
Unknown source concepts retain their description and medication event even when
no national codes are available.

The reviewed examples are:

- RxNorm `1648755`: Nitrofurantoin-ratiopharm 100 mg prolonged-release capsules,
  PZN `07097540`, represent the 100 mg source capsule. The crystallisation difference
  is accepted for synthetic data; existing dosage stays unchanged. The historical
  pack is documented on PDF page 209 of the
  [G-BA/IQTIG antibiotic report](https://www.g-ba.de/downloads/39-261-6055/2023-06-15_DeQS-RL_Freigabe-IQTIG-Bericht_Pruefung-Umsetzbarkeit-QS-AB-Z.pdf);
  strength, form and schedule are described in the
  [product information](https://www.fachinfo.de/fi/pdf/001489).
- RxNorm `197541`: Colchicin Zentiva 0.5 mg tablets represent the 0.6 mg source
  tablet as an accepted approximation. No tablet-count or milligram conversion is
  applied. [Manufacturer product catalogue](https://www.zentiva.de/produkte/produktkatalog/colchicin-zentiva).
- Quinapril to ramipril and other active-ingredient substitutions retain both the
  product mapping and the original dose, including explicit milligram values.
- Release changes, combination changes and product-specific biological units remain
  documented as notes and do not remove product or classification data.

`python3 scripts/audit_medication_dose_compatibility.py` inventories the available
product mappings and informational notes. These findings do not block generation.

Review examples include low-dose aspirin classification, implant versus intrauterine
levonorgestrel products, and the source-specific sublingual use of atropine drops.
For Herceptin SC, recombinant hyaluronidase is treated as an excipient according
to the recorded EU product information.

`audit_medication_mapping.py` checks source completeness and ATC target existence
against a locally available official 2026 workbook. `medicationMappingSummary`
counts events and unresolved codes; `clinicalMappings` records provenance.

## Optional local catalog

The importer loads a catalog from
`~/.local/share/synthifhir/medication-products.json` when present. Prepare this
file from data you are entitled to use. The adapter checks its schema and source
inventory; catalog preparation is responsible for product validity and evidence.

A matching entry supplies product information from the local catalog, including
entries marked `doseCompatibility: "unresolved"`. An absent match uses the public mapping.
Malformed entries, duplicate source assignments and source codes outside the
inventory stop the import with an error.

```json
{
  "schemaVersion": 1,
  "provider": "mmi-local",
  "id": "local-product-catalog",
  "sourceVersion": "2026-05-01",
  "entries": []
}
```

Each entry contains:

| Field | Content |
| --- | --- |
| `source.system`, `source.code` | One unversioned source coding from the Synthea medication inventory. |
| `target.system` | `http://fhir.de/CodeSystem/ifa/pzn`. |
| `target.code` | Eight-digit PZN stored as a string. |
| `target.display` | German product name. |
| `target.doseForm` | German dose-form text. |
| `target.ingredient` | Optional unversioned coding with `system` and `code`, using ASK, UNII, SNOMED CT or RxNorm. |
| `doseCompatibility` | `unchanged` or `unresolved`. |
| `provenance.source` | Source of the assignment. |
| `provenance.method` | Selection method. |
| `provenance.evidence` | Evidence covering ingredients, strength, form and any approximation. |

The table lists the accepted target fields. The local adapter represents one
optional ingredient coding per product. Catalog identity, source version and
SHA-256 are recorded under `productCatalog`; per-event choices remain under
`clinicalMappings`.

## Local data and distribution

For public mapping provenance, notices and outstanding publication questions,
see [mapping data and publication](mapping-data-publication.md).


Output using a local catalog is marked with
`productDataUsage.containsLocalProductData: true` and
`redistribution: "not-cleared"`. Save those workbooks and reports outside the
repository. The output guard checks ignored directories and symbolic links too.
The same data-use restrictions apply to FHIR subsequently generated from them.

Redistribution requires rights for the underlying product data. The adapter's
software license and source-data licenses are separate; retain the catalog's
licensing evidence with local output. The repository includes synthetic adapter
fixtures, including test PZN `00000000`.
