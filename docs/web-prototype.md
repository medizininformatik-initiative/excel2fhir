# Web workbench prototype

The local workbench converts the bundled starter or INTERPOLAR demo workbook
with the existing converter defaults. It uses React, TypeScript, Vite, Tailwind
CSS and a shadcn/ui Button, with FastAPI and a separate Python worker.

## Start and use

```sh
docker compose -f compose.web.yml up -d --build
```

Open <http://localhost:5184>, choose a workbook and the default configuration
profile, and select **Start conversion**. Select a run to view its status and
recent logs. **Snapshot** downloads the submitted configuration and input and
converter fingerprints. **Download** provides a ZIP containing FHIR results,
converter reports, effective options and logs. Conversion uses JSON and NDJSON;
FHIR validation is disabled. See [converter results](converter-usage.md#output).

**Cancel** cancels queued work or terminates the running converter process group.
Reloading or closing the browser leaves work running. A worker restart marks
unfinished running jobs as `interrupted`; start a new conversion to retry.
Queued jobs remain queued and run when the worker is available.

The named `workbench` volume persists SQLite, JSON profiles, immutable per-run
input/configuration copies, logs and results across container recreation.

```sh
docker compose -f compose.web.yml down
```

The workbench listens on the local loopback interface. It is intended for one
local user. Only the web service publishes a port; API and worker communicate
through SQLite and persistent files. Neither service receives the Docker socket.
The prototype offers the two bundled inputs and one default profile. Uploads,
profile editing, Synthea and environment controls are follow-up work.

## Verify

```sh
mvn test package
python3 -m pip install -r web/backend/requirements-test.txt
python3 -m unittest discover -s web/backend -p 'test_*.py'
npm ci --prefix web/frontend
npm run build --prefix web/frontend
python3 web/integration/smoke_workbench.py
```

The smoke test requires the running Compose project. It creates real conversions,
checks FHIR downloads and immutable snapshots, cancels queued and running jobs,
and kills/restarts only this project's worker container to check recovery.

## Architecture and resource boundaries

1. FastAPI copies the selected workbook and Java-derived effective defaults into
   a UUID job directory, writes the JSON snapshot, then inserts the SQLite job.
   The snapshot includes the converter JAR hash. A queued job fails explicitly
   if its converter image changes before execution.
2. SQLite WAL and short `BEGIN IMMEDIATE` claims allow independent jobs without
   double claiming. One supervisor currently owns recovery and executes jobs
   sequentially. An exclusive lock prevents competing recovery supervisors.
3. Each job launches its own JVM with its own working directory and output root.
   The worker gets two CPUs and 2 GiB RAM; the JVM heap is capped at 1536 MiB.
   The API gets one CPU and 512 MiB. These are prototype resource bounds, not
   capacity measurements for large generation or validation workloads.
4. `EncounterConverter` contains mutable static contact pointers, collections of
   secondary contacts, derived ends and a Location map. `ConverterOptions` has
   per-instance counters and caches. Root log appenders also belong to the JVM.
   Keep independent jobs in separate JVMs. Within-run parallelism needs a separate
   audit before splitting patients or complete bundles.
5. `FHIRValidator` builds HAPI contexts, terminology support and caches. Parallel
   validation multiplies their memory cost. Synthea already has internal
   concurrency; future scheduling must budget that together with JVM heaps.
6. Bounded parallel execution can add slots behind the existing transactional
   claim interface while retaining a single recovery owner. Multiple independent
   supervisors need leases/heartbeats and per-owner recovery first. Deterministic
   IDs, seeds, cross-resource validation and variant isolation belong to #84.

The options contract and editor follow in #75–#77. Durable profile editing and
richer job management follow in #78; expanded sources follow in #79. Data Node
lifecycle/import controls and TORCH/FDE user workflows follow in #81–#82.

## Versioned Data Node probe

`web/integration/data-node.lock.json` records the upstream commit. Download its
archive, extract it outside this repository, then prepare a fresh test directory:

```sh
python3 web/integration/prepare_data_node.py /absolute/upstream /absolute/node-test
docker compose -f /absolute/node-test/compose.yml up -d
```

The preparation script requires Python 3, OpenSSL and JDK `keytool`. It copies the
upstream `data-node/` directory and extends its Compose services instead of
maintaining independent service definitions. It generates a seven-day localhost
certificate and PKCS12 trust store, a disposable `prototype` / `prototype` Basic
auth account, and a small patient-count Measure. Use only generated test data.

The isolated project is `excel2fhir-data-node-74`. Ports 5185–5188 are bound to
loopback: Blaze, TORCH, TORCH's file server, and the TLS reverse proxy. Its named
volumes persist Blaze data and TORCH output. FDE writes into `node-test/fde-output`.
Absolute host paths identify bind mounts; these must be available to the Docker
host. Run the controller on the host for this probe.

This data-plane probe uses direct internal FHIR access with OAuth issuer settings
empty. TORCH's external proxy route requires Basic auth over locally verified
TLS. Full upstream Keycloak discovery/token exchange, external terminology,
FLARE cohort selection and DSF are integration gaps. Direct local service ports
are for diagnostics; this topology is not a production authentication design.

Extract a successful workbench download and choose exactly one `fhir/default`
variant directory, then run:

```sh
python3 web/integration/probe_data_node.py /absolute/node-test /absolute/fhir/default
docker compose -f /absolute/node-test/compose.yml run --rm fhir-data-evaluator
```

The probe converts generated bundles into PUT transactions retaining resource
IDs and references, saves import responses, checks unauthenticated TORCH access,
submits the upstream example CRTDL for explicit imported patient IDs, and saves
its asynchronous response. Inspect `torch-response.json`, `torch-result.json`
and `fde-output/`. HTTP acceptance alone does not establish successful extraction.
FDE reports data availability; it does not replace FHIR profile validation.

```sh
docker compose -f /absolute/node-test/compose.yml down
```

Use a separate project/target per dataset when resource IDs overlap. No web API
accepts Docker commands or host paths. A future internal controller should expose
only allowlisted environment operations and coordinate environment ownership.

## Validation evidence and remaining integration work

Status on 2026-10-02:

| Check | Result |
| --- | --- |
| `mvn test package` | Passed: 112 tests, no failures or errors. |
| Python queue, API and worker tests | Passed: 8 tests, including competing claims, cancellation, immutable copies, image-change detection and recovery. |
| Frontend TypeScript/production build | Passed; npm audit reported zero known vulnerabilities. |
| Real converter through worker | Starter and demo workbooks both succeeded. |
| Browser → FastAPI → worker → result | Passed locally with Vite, FastAPI and a separate worker process. Reloading immediately after submission retained the job; it completed successfully. |
| HTTP result download | Passed: the starter ZIP contained one FHIR Bundle and 20 files. Browser automation could not collect its download event; direct HTTP verified the same endpoint. |
| Running-job cancellation / supervisor shutdown | Passed with real Java processes; states became `cancelled` / `interrupted`. |
| Compose configuration | Both workbench and isolated upstream test configuration passed `docker compose config`. |
| Compose runtime and hard container crash | Pending: image/package downloads prevented complete image builds. The supplied smoke test has not run against containers. |
| Data Node startup, import, TORCH and FDE | Blocked before application startup by image downloads. No successful import, extraction or evaluation report is claimed. |

The Data Node startup was attempted twice. The first attempt failed with a Docker
Registry TLS handshake timeout. The retry ended with `short read: expected
293557980 bytes but got 17063543: unexpected EOF`. A separate FDE invocation
reached image pulling; it was stopped after the same sustained transfer problems.
The workbench's converter build stage succeeded, but Alpine package downloads and
the frontend Node image pull did not complete. These are environment/network
blockers, not evidence that the upstream application integration works.

Next, complete both image builds and run `smoke_workbench.py`, then start the
pinned Data Node, import exactly one generated KDS variant, submit the TORCH
request and inspect its final files, and run FDE and inspect its report. Verify
container recreation and data persistence, TLS rejection/acceptance and Basic
auth at runtime. Full Keycloak/OAuth discovery and token exchange still require
a dedicated integration check. Issue #74 remains open until these acceptance
checks are resolved or explicitly accepted as documented limitations.

The implementation follows the [shadcn/ui Button](https://ui.shadcn.com/docs/components/radix/button)
and [Tailwind Vite integration](https://tailwindcss.com/docs/installation/using-vite).
The Data Node services come from the
[pinned upstream commit](https://github.com/medizininformatik-initiative/dataportal/tree/ce654d58f02be004625234504af0315c33d8b294/data-node).
The small FDE Measure uses the structure of the
[v1.3.3 basic Measure example](https://github.com/medizininformatik-initiative/fhir-data-evaluator/blob/v1.3.3/docs/example-measures/example-measure-1.json).
