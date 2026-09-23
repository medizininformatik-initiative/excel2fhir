# Synthetic German identities

The Synthea importer combines source clinical data with a deterministic synthetic
German name and address. Birth date, administrative sex and death time come from
the source. The original source bundle is retained. This feature is intended for
synthetic input; real patient data requires a dedicated de-identification process.

## Selection rules

`scripts/mappings/german-demographics.json` contains the versioned name and address
pools. First and last names are selected independently. Administrative sex selects
the first-name pool; `other` and `unknown` use the full pool. Patient IDs distinguish
people even when selected names coincide.

SHA-256 of the rule version, patient ID and selection purpose determines each
choice. Results are independent of input order and population size. The report
records the data-file hash and generated identity. The selection is deterministic
rather than population-weighted.

Postal-code/city pairs are based on the recorded city sources; streets and house
numbers are synthetic. The address represents one location. The source evidence
covers the city/postal-code pairs:
[Berlin](https://www.berlin.de/sehenswuerdigkeiten/3559880-3558930-rotes-rathaus.html),
[Hamburg](https://www.hamburg.de/service/info/11297145/),
[Munich](https://stadt.muenchen.de/service/info/stadtverwaltung/10264283/) and
[Cologne](https://www.koeln.de/apps/strassen/rathausplatz).

## Name sources

The pools use Faker data pinned to commit
`59a6872a547248bb40cdd0f809089a605c68467f`:

- [de_DE given names and surnames](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/de_DE/__init__.py)
- [de_AT supplementary surnames](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/de_AT/__init__.py)
- [tr_TR supplementary surnames](https://github.com/joke2k/faker/blob/59a6872a547248bb40cdd0f809089a605c68467f/faker/providers/person/tr_TR/__init__.py)

Project-curated supplements are stored in `german-name-supplement.json`.
The MIT notice is in [third-party/faker](../third-party/faker/README.md), and
`nameSources` records source URLs, fields and checksums.

To rebuild the pool, place the pinned source files in a directory as `de_DE.py`,
`de_AT.py`, `tr_TR.py`, together with their `LICENSE.txt`, then run:

```sh
python3 scripts/build_german_name_pool.py SOURCE_DIRECTORY
```

The builder checks hashes, reads Python literals through AST, normalizes Unicode
and whitespace, and deduplicates names case-insensitively. Distinct spellings such
as Sara/Sarah remain separate. Changes to the pool require a version update.

## Names in documents

Identity projection replaces complete source names and Synthea name components
with distinctive numeric suffixes. It preserves ordinary words that could also
be names, such as `May` or `Brown`. Replacements use word boundaries and one pass.
`documentIdentityChanges` records replacement counts and source/target text hashes.

[German text projection](synthea-german-texts.md) then processes the clinical
wording. Source comparison verifies the resulting patient fields and document text.
