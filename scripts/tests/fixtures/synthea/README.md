# Synthea integration fixtures

These synthetic patient fixtures preserve references, entry order and source
resource content. Each contains 30 resources. Selection starts with the first
available resource of each supported type and recursively includes referenced
resources present in the source bundle. The pipeline applies its German projection
during integration tests.

| Fixture | Source bundle | Source SHA-256 |
| --- | --- | --- |
| female.json | Corinne382_Kovacek682_2c6a4af2-72ae-93e7-8655-2f1bbc85684b.json | e297014ff4adc06c34e5e2c469285d87f58fb5329c4f6aa0d284cf036f4e79e0 |
| male.json | George991_Gleichner915_a54713ed-ad69-d7cc-7df8-e745e143f1ec.json | 681da17d9b3180dbcf5620fdb713a78cc6efe308bb225c779d55696750ea6b14 |

Source generator: [Synthea](https://github.com/synthetichealth/synthea),
The MITRE Corporation, Apache License 2.0. Retained resource metadata describes
the source. The project Apache-2.0 license applies to fixture preparation.
