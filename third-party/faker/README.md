# Faker name data attribution

The name lists in `scripts/mappings/german-demographics.json` include data
extracted from [Faker](https://github.com/joke2k/faker), published under the MIT
license. The upstream copyright and permission notice is preserved in LICENSE.txt.
Pinned commit: `59a6872a547248bb40cdd0f809089a605c68467f`.

Used providers: person/de_DE (male/female given names and surnames), person/de_AT
(surnames), person/tr_TR (surnames). Source URLs and SHA-256 digests are recorded
in the generated JSON under nameSources. Existing project-curated supplements
are stored separately in scripts/mappings/german-name-supplement.json.

Transformations: NFC normalization, whitespace cleanup, case-insensitive
deduplication, exclusion of dotted abbreviations, sorted flat name arrays.
No weighting, titles, academic degrees, or name-format templates were imported.
Faker's source comments additionally cite German Wiktionary given-name lists,
the Digital Dictionary of Surnames in Germany (de_DE surnames), and Wiktionary's
Austrian surname list (de_AT). Those original references remain available in the
pinned source files; no additional data was scraped from those sites.

Rebuild instructions and limitations: docs/synthea-german-demographics.md.
