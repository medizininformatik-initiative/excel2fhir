# Availability integration check

`smoke_availability.py` verifies FDE reports, the corrected ontology loader,
repeatable update bytes, Elasticsearch bulk item responses and the final
catalogue value. It runs inside the derived updater image, which supplies the
upstream Python modules and dependencies.

Use an isolated test Blaze with at least ten synthetic Patients with a gender,
and the complete v5.0.0 ontology index initialized by the portal. The index must
have zero availability before publication. The test restores its availability
values to zero after verification. Keep other users away from these test stores.

Run FDE with `web/integration/availability-measure.json`, as configured in
`web/compose.yml`. This Measure uses the Patient gender group from
[v5.0.0 CdsCodingAvailability](https://github.com/medizininformatik-initiative/fhir-ontology-generator/releases/tag/v5.0.0)
(`availability.zip`), with local identity metadata and only the `patient-gender`
stratifier. The independent Data Node probe uses `measure.json` for basic counts.

Mount the following into a container built from
`web/deployment/availability-updater`, on the test Compose network:

- This script as `/probe.py`, read-only.
- A writable, empty evidence directory as `/check`.
- The extracted v5.0.0 `elastic/` directory as `/ontology/elastic`, read-only.
  It can be copied from `/work/elastic` in the completed `init-elasticsearch`
  container, preserving `content/` and its files.

Run the first phase while Blaze is reachable by its Compose service name:

```sh
python /probe.py collect /check /ontology 12
```

Replace `12` with the actual test Patient count. The probe downloads the matching
v5.0.0 availability mappings, reads the standard report from Blaze via its
DocumentReference, verifies the score and independently calculates the expected
catalogue node and bucket. It loads the whole ontology twice and compares every
output filename and SHA-256 without normalization. Evidence is saved in
`verification.json`, with the reports and both sets of update files alongside it.

Blaze can then be stopped before starting Elasticsearch. Run the second phase
with the same evidence directory:

```sh
python /probe.py publish /check
```

This uploads through the upstream uploader, additionally checks every bulk
response for item errors, refreshes the index and reads the target node back.
It records the observed value and restores zero availability in a `finally` block.
Each probe container can be limited to 512 MiB. Stop the test Elasticsearch after
completion; delete only the Patients, reports and references created for the test.

The staged test covers real FDE → Blaze report retrieval → full ontology mapping
→ Elasticsearch publication. It does not measure simultaneous full-stack memory
or prove determinism of the entire generation pipeline.
