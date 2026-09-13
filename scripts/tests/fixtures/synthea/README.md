# Synthea integration fixtures

These are reference-preserving extracts of the existing synthetic patient bundles
used in this project. No clinical values, identifiers, displays or codes were
invented or translated during extraction. The pipeline performs its normal
German projection later. These are regression fixtures, not a new feature for
exporting episodes or generating shorter histories.

For each available supported resource type, the first resource was selected;
resources referenced by the selection were recursively included if present in
the source bundle. Original entry order and resource contents are preserved.
Both extracts contain 30 resources. Missing types are not synthesized.

| Fixture | Original source | Original SHA-256 |
| --- | --- | --- |
| female.json | Corinne382_Kovacek682_2c6a4af2-72ae-93e7-8655-2f1bbc85684b.json | e297014ff4adc06c34e5e2c469285d87f58fb5329c4f6aa0d284cf036f4e79e0 |
| male.json | George991_Gleichner915_a54713ed-ad69-d7cc-7df8-e745e143f1ec.json | 681da17d9b3180dbcf5620fdb713a78cc6efe308bb225c779d55696750ea6b14 |

Source generator: [Synthea](https://github.com/synthetichealth/synthea),
The MITRE Corporation, Apache License 2.0. These are entirely synthetic records.
The retained Provenance/resource metadata, where present, describes the source;
no real patient information is used. The project Apache-2.0 license applies to
the regression fixture preparation.
