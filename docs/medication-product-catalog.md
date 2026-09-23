# German medication data

## Public Synthea mapping

`scripts/mappings/synthea-medications-de-2026.json` covers 495 RxNorm concepts from
the pinned source inventory. Each has a German ATC 2026 assignment, publicly
sourced UNII ingredient identifiers and a selected German PZN with product name
and form. These are curated synthetic-data choices requiring clinical review.

The mapping supplies German product information to Excel and keeps RxNorm in the
source report. Unknown concepts retain their event, description and explicit
source form, with unresolved PZN/ATC assignments identified in the text.
Semicolon-separated UNII values become separate ingredients. The shared Excel/CSV
converter also accepts manually supplied product and ingredient systems as described
in the [input contract](template-input-contracts.md#medication).

ATC uses the explicit year 2026. Product evidence includes public BfArM, insurer,
manufacturer and G-BA documents. URLs, hashes and PDF pages are recorded in the
table. Ingredient evidence comes from NIH RxNav and FDA UNII sources.

## Dose and product choices

`dosePolicy` distinguishes preserved source dosage from an explicitly selected
synthetic regimen. Substitutions record target ingredients, dose instructions and
the original dosage in provenance. These choices need review for therapeutic
appropriateness and pharmaceutical equivalence.

Unitless oral tablet/capsule counts receive the corresponding unit for suitable
products. Existing physical units are retained. Weight- or indication-dependent
infusions may use text instructions. Package quantity, product strength and dose
are separate facts.

Review examples include low-dose aspirin classification, implant versus intrauterine
levonorgestrel products, and the source-specific sublingual use of atropine drops.
For Herceptin SC, recombinant hyaluronidase is treated as an excipient according
to the recorded EU product information.

`audit_medication_mapping.py` checks source completeness and ATC target existence
against a locally available official 2026 workbook. `medicationMappingSummary`
counts events and unresolved codes; `clinicalMappings` records provenance.

## Optional local catalog

The importer loads a catalog from
`~/.local/share/excel2fhir/medication-products.json` when present. Prepare this
file from data you are entitled to use. The adapter checks its schema and source
inventory; catalog preparation is responsible for product validity and evidence.

A match with `doseCompatibility: "unchanged"` supplies product information from
the local catalog. An unresolved dose or absent match uses the public mapping.
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

Output using a local catalog is marked with
`productDataUsage.containsLocalProductData: true` and
`redistribution: "not-cleared"`. Save those workbooks and reports outside the
repository. The output guard checks ignored directories and symbolic links too.
The same data-use restrictions apply to FHIR subsequently generated from them.

Redistribution requires rights for the underlying product data. The adapter's
software license and source-data licenses are separate; retain the catalog's
licensing evidence with local output. The repository includes synthetic adapter
fixtures, including test PZN `00000000`.
