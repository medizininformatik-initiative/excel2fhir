# Web workbench prototype

The interface defaults to German. The language selector switches between German
and English and remembers the selection in this browser. Status messages and
interface errors follow the selected language; converter logs and validation
reports retain their original text. FHIR output uses the German KDS independently
of the interface language. Shared interface texts live in
[`catalog/options/de.json`](catalog/options/de.json) and
[`catalog/options/en.json`](catalog/options/en.json).

The local workbench converts the bundled starter or INTERPOLAR demo workbook
with the existing converter defaults. It uses React, TypeScript, Vite, Tailwind
CSS and a shadcn/ui Button, with FastAPI and a separate Python worker.

## Configuration editor

The configuration editor provides a continuous resource page with section links
and separate tabs for additional identifiers, Data Absent Reason (DAR),
IDs/repetitions/time shifts, terminology, and checks/output. Controls use the agreed option contract and DAR
catalogue. Unavailable controls remain visible, explain their dependencies, and
retain selected values. Help icons support hover, keyboard focus and click, including the pattern syntax
and sample-preview details. Tab
navigation supports the arrow keys, Home and End.

The first visit starts with contract defaults. Changes are saved automatically
in this browser after a short typing pause and when leaving the page. The status
shows whether the draft is saved or saving failed. Incomplete entries are retained
for subsequent visits; validation determines whether the configuration can be exported.
**Export file** downloads its
versioned UTF-8 `.config` file with uppercase variable names and comments in the
selected interface language. Inactive selections are commented out and retained
when imported again. Each line can be pasted into a separate row in column A of
a `Konvertierungsoptionen` sheet. **Import file** accepts `.config` and JSON
configurations and validates them before replacing the current draft. See the
[configuration format](catalog/options/README.md#properties-export-and-import)
for values, comments, DAR fields and identifier rules. Imports and **Restore defaults**
are saved automatically. Invalid drafts cannot be exported. Imports reject unknown fields, incorrect types,
unsupported schema versions, duplicate assignments, JSON members or rule IDs, unsuitable DAR
codes and invalid identifier patterns.

DAR replacements require a field-specific code. Resource selection controls their
availability; existing DAR selections remain stored when unavailable. Conditional
codes display the applicable narrative or procedure condition. Identifier rules
retain their UUID when edited or imported and provide deterministic sample
previews. The preview uses counter 1 and repetition 0 with example resource and
patient IDs, and caps displayed counter padding at 256 characters. The identifier system shows an example placeholder while empty and unfocused.
Pattern tokens use compact buttons with explanations on hover or keyboard focus,
and can be clicked to insert at the
cursor, replace selected text, or append when no cursor position is available.
Actual resource counters and collision checks belong to converter execution.

With the editor source selected, **Start conversion** submits its configuration
as versioned Properties. The API validates it with the Java converter before queuing the job.
Each job keeps its own input, configuration and converter fingerprint. Later
editor changes apply to subsequent jobs. Invalid drafts disable the start action
only when the editor source is selected;
server-side validation errors are displayed without creating a job.

## Saved configurations

With **Current editor settings** selected, use **Save editor configuration**, the
first action above the saved configurations, to save an executable editor draft
under a name. Configurations persist as JSON documents
in the workbench volume and are available across browser sessions and container
restarts. Names are unique regardless of letter case.

Select an entry and choose **Load into editor** to edit or run it. Confirming the
load replaces the current draft. **Replace with editor draft** updates the selected
saved configuration after confirmation. **Rename** changes its name; **Duplicate**
creates an independent copy of its saved settings under a new name. **Delete**
removes the entry from the list while retaining the editor draft and existing runs.
Deleted documents are retained under `configurations/.trash/` in the volume.

Each update checks the saved revision. If another tab changed the same entry,
cancel the action and choose the current entry before retrying. The list refreshes
automatically every five seconds while the page is visible and when returning to
the page. This keeps changes from other tabs visible without replacing the editor
draft or an open confirmation.
Changes to saved configurations never modify the snapshots of existing runs.
Import and export continue to use the shared versioned configuration format.

## Start and use

```sh
docker compose -f web/compose.yml up -d --build
```

Open <http://localhost:5184>, choose a workbook and a **Configuration source**,
then select **Start conversion**. **Configuration from workbook** runs all included
configuration sheets as separate variants, or converter defaults if none exist.
**Current editor settings** uses the editable draft below. When the workbook is
selected, the retained editor draft is greyed out; it does not preview workbook
settings. New browser sessions default to the workbook source; the last selected
source is saved in the browser. Select a run to view its status and
recent logs. **Snapshot** downloads the submitted configuration and input and
converter fingerprints. **Download** provides a ZIP containing FHIR results,
converter reports, effective options and logs, including for failed validation
runs. Formats, patients per output file and FHIR validation follow the submitted
configuration. Defaults are JSON and NDJSON, one patient per JSON bundle and
FHIR validation disabled. See [converter results](../docs/converter-usage.md#output).

**Cancel** cancels queued work or terminates the running converter process group.
Reloading or closing the browser leaves work running. A worker restart marks
unfinished running jobs as `interrupted`; start a new conversion to retry.
Queued jobs remain queued and run when the worker is available.

The named `workbench` volume persists SQLite, immutable per-run
input/configuration copies, logs and results across container recreation.

```sh
docker compose -f web/compose.yml down
```

The workbench listens on the local loopback interface. It is intended for one
local user. Only the web service publishes a port; API and worker communicate
through SQLite and persistent files. Neither service receives the Docker socket.
The workbench offers the two bundled inputs with workbook or editor configuration.
Saved configurations are stored in the local workbench volume. Uploads, Synthea
and environment controls are tracked separately.

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
recreates this project's API to check proxy reconnection, and kills/restarts
this project's worker container to check recovery.

## Architecture and resource boundaries

The verified prototype uses the selected React/FastAPI stack, a persistent SQLite
queue and a separate worker with one JVM per conversion. This is the architecture
for the next workbench increments. The shared converter integration comes next;
bounded parallel execution follows the shared-state and resource-budget audit.

1. FastAPI copies the selected workbook into a UUID job directory and records the
   selected configuration source. For editor settings, it also saves and validates
   the submitted configuration with Java. Workbook settings are read by the
   converter from the saved workbook. It writes the JSON snapshot, then inserts
   the SQLite job.
   The snapshot includes the converter JAR hash. A queued job fails explicitly
   if its converter image changes before execution.
2. SQLite WAL and short `BEGIN IMMEDIATE` claims allow independent jobs without
   double claiming. One supervisor currently owns recovery and executes jobs
   sequentially. An exclusive lock prevents competing recovery supervisors.
3. Each job launches its own JVM with its own working directory and output root.
   The worker gets two CPUs and 4 GiB RAM; the JVM heap is capped at 3 GiB
   to load the bundled FHIR validation profiles.
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

Shared option execution uses the same Java pipeline for Excel, CSV and web.
Multi-configuration runs and repeating saved runs are tracked in #78; expanded sources in #79. Data Node
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
volumes persist Blaze data and TORCH output. FDE uploads reports to Blaze;
`verify_fde.py` exports the latest reports into `node-test/fde-output`.
The TORCH test has a 2 GiB JVM heap and a 3 GiB container memory limit.
Absolute host paths identify bind mounts; these must be available to the Docker
host. Run the controller on the host for this probe.

This data-plane probe uses direct internal FHIR access with OAuth issuer settings
empty. TORCH's external proxy route requires Basic auth over locally verified
TLS. Full upstream Keycloak discovery/token exchange, external terminology,
FLARE cohort selection and DSF are integration gaps. Direct local service ports
are for diagnostics; this topology is not a production authentication design.

Extract a successful workbench download and choose exactly one `fhir`
variant directory, then run:

```sh
python3 web/integration/probe_data_node.py /absolute/node-test /absolute/fhir
docker compose -f /absolute/node-test/compose.yml run --rm fhir-data-evaluator
python3 web/integration/verify_fde.py /absolute/node-test 3
```

The probe converts generated bundles into PUT transactions retaining resource
IDs and references, saves import responses, checks unauthenticated TORCH access,
submits the upstream example CRTDL for explicit imported patient IDs, and saves
its asynchronous response, downloads the final NDJSON files and verifies the
extracted patient IDs. Inspect `torch-response.json`, `torch-result.json`,
`torch-downloads/` and `fde-output/`. Use the actual imported patient count as
the final argument to `verify_fde.py`. It must match all Patients currently stored
in that test server (3 for a fresh server loaded with only the bundled demo).
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
| Maven tests | Passed: 113 tests, no failures or errors. |
| Python queue, API and worker tests | Passed: 8 tests. |
| Frontend TypeScript/production image build | Passed; npm audit reported zero known vulnerabilities. |
| Compose workbench lifecycle | Conversion, reconnect, immutable snapshot, logs and ZIP download passed. API recreation with automatic proxy reconnect, queued/running cancellation and SIGKILL recovery passed. |
| Pinned Data Node startup | Blaze, TORCH, file server and TLS proxy started successfully. |
| Demo data import | Three patient bundles imported with HTTP 200; Patient count is 3. |
| Starter data import | Ten patient bundles imported with HTTP 200; all references resolve and all 272 unique resources were read back. |
| TORCH extraction | HTTP 202 submission followed by HTTP 200 completion; downloaded NDJSON contains 3 Patients, 2 Conditions and 5 Provenance resources. |
| FDE | Standard report counted 3 patients; the separate obfuscated report counted 5. Both were uploaded and exported locally. |
| TLS / Basic auth | Trusted local certificate accepted; untrusted certificate rejected; unauthenticated extraction rejected with HTTP 401. |
| Restart and container recreation persistence | Patient count, FDE reports, TORCH job/files and Workbench job/ZIP retained after restart and forced container recreation. |

The starter workbook includes six DocumentReference examples associated with
both cases `1` and `2`. Their `Fall-Nr` cells contain the text list `1,2`.
Blaze referential integrity checking is enabled for both workbook imports.

Full Keycloak/OAuth discovery and token exchange, FLARE cohort selection,
external terminology services and DSF integration remain separate work. These
results establish the isolated prototype data plane and do not establish the
complete upstream authentication workflow.

The implementation follows the [shadcn/ui Button](https://ui.shadcn.com/docs/components/radix/button)
and [Tailwind Vite integration](https://tailwindcss.com/docs/installation/using-vite).
The Data Node services come from the
[pinned upstream commit](https://github.com/medizininformatik-initiative/dataportal/tree/ce654d58f02be004625234504af0315c33d8b294/data-node).
The small FDE Measure uses the structure of the
[v1.3.3 basic Measure example](https://github.com/medizininformatik-initiative/fhir-data-evaluator/blob/v1.3.3/docs/example-measures/example-measure-1.json).

## Module layout

- `frontend/`: browser interface.
- `backend/`: API, persistent storage and conversion worker.
- `catalog/dar/`: reviewed DAR field contract; `source/`, `generator/`, `generated/` and `tests/`.
- `integration/`: workbench and upstream Data Node probes.
- `compose.yml`: local workbench services.

See [DAR catalogue maintenance](catalog/dar/README.md) for generation and verification.

See [configuration implementation TODOs](TODO.md) for agreed behavior and remaining work.
