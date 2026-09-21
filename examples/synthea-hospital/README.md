# Hospital example for 2020–2026

This recipe selects ten synthetic patients with a range of inpatient histories
and retains their outpatient contacts. It is a curated demonstration. The
selection uses Synthea's original facility-contact classes.

Use fresh destination directories for each attempt so the selection contains
one source population.

## 1. Generate candidates

Generate a pool of 60 older patients with cardiac surgery. The keep-module requires
surgery at some point in their lifetime; the selection step checks hospital stays
within the target period.

```sh
docker compose -f compose.synthea.yml run --build --rm --entrypoint java synthea \
  -Xmx4g -Duser.timezone=Europe/Berlin -jar /app/target/synthea.jar \
  -p 60 -a 60-85 -s 20260917 -cs 20260916 -r 20270101 -e 20270101 \
  -k must_have_cardiac_surgery.json \
  --exporter.baseDirectory=outputGlobal/hospital-pool \
  --exporter.years_of_history=7 \
  --exporter.hospital.fhir.export=false --exporter.practitioner.fhir.export=false
```

This direct generator call sets the export format and destination needed by the
recipe. The simulation ends at the start of 2027 to include 2026. Synthea counts
365 days per history year, so the seven-year window starts around 3 January 2020.
Ongoing conditions, medication, care plans and their originating contacts may
carry earlier dates. Deceased patients may be exported in addition to the requested
living population.

## 2. Select ten cases

```sh
docker compose -f compose.synthea.yml run --rm --entrypoint python3 synthea \
  examples/synthea-hospital/select_cases.py \
  outputGlobal/hospital-pool/fhir outputGlobal/hospital-selection
```

The selection comprises six patients with at least two inpatient stays, two with
one and two with none during 2020–2026. It counts original `IMP` facility contacts
and uses deterministic ordering within groups. Source bundles are copied intact.
`hospital-selection/selection.json` records the chosen patients, counts and hashes.
If the pool is too small, generate another pool with a larger population or a
different seed.

## 3. Generate Excel and FHIR

```sh
docker compose -f compose.synthea.yml run --rm --entrypoint python3 synthea \
  /app/scripts/run_synthea_cases.py \
  -i outputGlobal/hospital-selection/fhir -o outputGlobal/hospital-results
```

Results follow the [Synthea output layout](../../docs/synthea-workflow.md#output)
under `outputGlobal/hospital-results/run-…-synthea-import/`, with one workbook per
source patient. The [common converter options](../../docs/converter-usage.md#common-options)
select KDS variants, formats and optional validation.

For a different scenario, adjust population size, age range, seeds and keep-module.
Additional clinical modules require checking mapping coverage for their source concepts.
