# Faker name data attribution

The name lists in `scripts/mappings/german-demographics.json` include data from
[Faker](https://github.com/joke2k/faker), published under the MIT license.
The upstream copyright and permission notice is preserved in [LICENSE.txt](LICENSE.txt).
The pinned source commit is `59a6872a547248bb40cdd0f809089a605c68467f`.

Used providers are person/de_DE (male/female given names and surnames), person/de_AT
(surnames) and person/tr_TR (surnames). Source URLs and SHA-256 hashes are recorded
under `nameSources`. Project-curated supplements are stored in
`scripts/mappings/german-name-supplement.json`.

The builder applies NFC normalization, whitespace cleanup, case-insensitive
deduplication and sorted flat name arrays, excluding dotted abbreviations.
Faker's source comments cite German Wiktionary given-name lists, the Digital
Dictionary of Surnames in Germany and Wiktionary's Austrian surname list.
Those references remain available in the pinned source files.

See [German identity generation](../../docs/synthea-german-demographics.md)
for rebuilding the data and the selection rules.
