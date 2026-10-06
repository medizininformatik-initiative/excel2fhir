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

## Uploaded inputs

Use **Upload input** beside the input selector to add an `.xlsx` workbook using
the supported template, a `.zip` containing CSV input groups, or Synthea R4 patient
bundles as `.json` or a ZIP of JSON files. CSV files must
be directly at the archive root; use the converter’s table names and group prefixes.
Each group needs its `Person.csv`. CSV column, row and patient-assignment checks
use the same parser as conversion. Uploads are limited to 64 MiB (256 MiB expanded). The
workbench stores the original bytes in its persistent volume and runs the Java
converter's structural checks before adding the file to the selection. The
inspection lists table names and row counts, excluding each header.
Clinical consistency checks run during conversion according to the selected
configuration.

Uploaded inputs support embedded settings, the editor draft and multiple saved
configurations. Each run receives its own copy and SHA-256 checksum; repeating a
run uses that saved copy. Filenames are display labels, not filesystem paths.
ZIP archives retain their original bytes; each run extracts its own input directory.

Synthea inspection counts patients and resources, rejects duplicate patient IDs
and identifies bundles without patients. The import skips those bundles. Each
patient bundle must contain one Patient with an ID. The worker runs the existing
Synthea-to-Excel-to-FHIR pipeline using LibreOffice, the bundled template and
German mappings. Results include the generated workbooks, source comparison,
projection-loss reports and effective configuration. Choose input configuration
for importer defaults, or the editor/saved configurations for independent KDS
variants. Each snapshot also fingerprints the import scripts, template and
mappings. Repeating uses the saved source with the current importer and records
the original importer fingerprint.

## Generate Synthea inputs

Choose **Generate Synthea data** in the input selector. Set the requested population,
age range, sex, patient and clinician seeds, age reference date, simulation end,
US state/city and exported history. Zero history years exports the full history.
The supplied example uses one patient, ages 30–80, Massachusetts, seeds `20260912`
and 12 September 2026 for both dates, matching the standalone workflow.

Advanced settings provide living/deceased selection, the bundled patient-selection
modules, an optional single-person seed, simulation timestep, attempt limit,
veteran population and disease/care modules. Module and location choices come
from the pinned generator JAR. With no module selection, all modules run; core
Synthea modules always run. Clinical import requires diagnoses, so narrowly
selected modules or history may generate patients that cannot be imported.

Each generator control has an info button explaining its effect on original Synthea
data and on the KDS projection. Disease/care modules determine which processes
are simulated, rather than guaranteeing a diagnosis in every patient. Additional
patient-selection rules retain matching simulated patients; their criteria may
not remain visible after history filtering or KDS projection. Selection rules
have descriptive names and show their actual criteria.

Choose **Synthea FHIR – original data** to generate FHIR R4 JSON without Excel
or KDS conversion. The dataset download retains Synthea patient identities,
US addresses, organizations and clinicians. KDS configuration controls are
inactive for this output. The run records its generator settings and supports
repeat, cancellation, resource inspection and downloads. No KDS validation or
clinical projection is performed. The displayed patient count excludes auxiliary
organization and clinician bundles.

Choose **KDS FHIR – convert Synthea data** to use the clinical import pipeline.
Choose input configuration for importer defaults or the editor/saved configurations
for KDS variants. Each selected configuration receives a separate generation and
conversion run with identical saved generator settings. Seeds, dates, settings,
generator revision and hashes are recorded. Repeats preserve these settings and
use the installed generator/importer, recording their original version hashes.
Runs show requested, actually exported and successfully imported source-patient
counts separately. Extra deceased patients can increase the exported count.

The web workflow accepts 1–1000 requested patients per run and up to 10,000
attempts per patient slot. It uses two Synthea threads, a 4 GiB generator heap
and a 6 GiB worker container; jobs run sequentially. Generated FHIR, editable
workbooks, original Synthea bundles, workflow reports and logs are included in
the download. See the [Synthea workflow](../docs/synthea-workflow.md) for output
layout and seed semantics.

## Run saved configurations and repeat runs

Choose **Saved configurations** as the configuration source and select one or
more entries. Starting creates one independent run per selected configuration,
using the same input. Each snapshot records its configuration name,
revision and settings. The shared submission identifier groups the runs; the
worker processes them sequentially. A stale or invalid selection prevents the
entire submission. Repeated delivery of the same submission returns its existing
runs instead of creating duplicates.

Each finished, failed, cancelled or interrupted run offers **Repeat run**. It
copies the original input and settings, even if the saved configuration or
bundled input has since changed. The new run uses the currently installed
converter and records both its version hash and the source run's version hash.
Existing results remain attached to their original runs. Repeating a run does
not depend on the current editor draft or configuration selection.

## Inspect datasets and reports

Select a run to see **Datasets and reports**. Each embedded configuration variant
has its own dataset archive; independent saved configurations remain separate
runs. The overview shows unique Patient and resource counts, counts by resource
type, repeated resource IDs and resources without IDs. Inspection streams one
representation per output folder in this order: JSON, NDJSON, gzip JSON, bzip2
JSON, ZIP JSON, XML. Additional formats do not increase the counts. Counts do
not establish FHIR conformance or equality of resources sharing an ID.

Expand the report section for import, validation and projection summaries, then
use each report link for the complete file. Summaries cover reports up to 2 MiB;
all reports remain accessible through the file list. Search the file list for
workbooks, sources, options, reports or logs. File and dataset downloads stream
from disk. The complete run ZIP remains available alongside the separate FHIR
dataset archives. Failed runs retain available artifacts for diagnosis.

The worker records a persistent dataset manifest and archive checksum. It indexes
finished runs when idle and publishes each manifest atomically. The inspection
JVM has a 512 MiB heap and a five-minute timeout; an inspection failure remains
visible without changing the converter outcome. Run search filters by source,
configuration name, run ID or status. **Repeat run** reuses the original input
and settings; existing datasets remain available independently of a new run.

Before starting a run, optionally enter a **Dataset name**. The name appears before
the source and configuration in dataset lists and is searchable in the run history.
An empty field uses the automatic label. Runs started together share the entered
name and retain their configuration labels; repeating a run preserves its name.
The name is display metadata and does not change generated FHIR content or resource IDs.

## Load datasets into a FHIR server

In **Load datasets into FHIR servers**, select an available Blaze or HAPI target
and one or more datasets from successful runs. The displayed address identifies
the target. Start **Load datasets** to queue the action; generation jobs and
uploads share the worker and run one at a time.

The worker checks the entire selection before transferring any data. Different
contents under the same resource type and ID within that selection stop the
upload and identify the conflict. Select compatible datasets or load them in
separate actions. Each later action updates existing resources with the same
IDs; resources absent from the selection remain on the server.

Each action retains the selected dataset checksums, run snapshots, target,
preparation result and upload log. Status distinguishes waiting, checking,
uploading, success, conflicts, failure and interruption. **Cancel** stops further
work. An interrupted or failed transfer can leave already uploaded transactions
on the server; it is not rolled back. Select the datasets again to explicitly
start another action. Reloading the browser does not cancel an upload.

The worker uses pinned `blazectl 1.5.1`, with two concurrent transactions for
Blaze and one for HAPI. It prepares PUT transactions preserving resource IDs and
FHIR resources, selects one representation per output directory in the same
order as dataset inspection, and supports JSON, NDJSON, compressed JSON and XML.
Original dataset files and archives remain unchanged. Missing resource IDs are
reported before upload. FHIR server rules, including Blaze referential integrity,
apply to each transaction; failures are retained in the log.

Start the desired services using the [main README](../README.md#start-with-docker).
The workbench checks FHIR R4 transaction support through the fixed Compose
endpoints; server lifecycle remains controlled by Docker Compose.

## Start and use

```sh
docker compose -f web/compose.yml up -d --build
```

Open <http://localhost:5184>, choose an input and a **Configuration source**,
then select **Start conversion**. **Configuration from input** runs included Excel configuration sheets or CSV option
files as separate variants, or converter defaults if none exist.
**Current editor settings** uses the editable draft below. When the input configuration source is
selected, the retained editor draft is greyed out; it does not preview embedded
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
local user. The web service publishes the workbench port; API and worker communicate
through SQLite and persistent files. Neither service receives the Docker socket.
The workbench offers the two bundled inputs and uploaded workbooks, CSV archives and Synthea bundles with embedded,
editor or saved configurations.
Saved configurations and uploaded inputs are stored in the local workbench volume.
Optional FHIR servers and the local Data Portal are selected with Compose profiles; see
[local services](deployment/README.md).

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
queue and a separate worker with one conversion process tree per run. This is the architecture
for the next workbench increments. The shared converter integration comes next;
bounded parallel execution follows the shared-state and resource-budget audit.

1. FastAPI copies the selected input into a UUID job directory and records the
   selected configuration source. For editor settings, it also saves and validates
   the submitted configuration with Java. Workbook settings are read by the
   converter from the saved workbook. It writes the JSON snapshot, then inserts
   the SQLite job.
   The snapshot includes the converter JAR hash. A queued job fails explicitly
   if its converter image changes before execution.
2. SQLite WAL and short `BEGIN IMMEDIATE` claims allow independent jobs without
   double claiming. One supervisor currently owns recovery and executes jobs
   sequentially. An exclusive lock prevents competing recovery supervisors.
3. Each job launches its own process tree with its own working directory and output root.
   The worker includes LibreOffice for the existing Synthea import pipeline;
   the API uses a smaller Java/Python image. Both use the same converter build
   and import assets. Input inspection runs serially with a 256 MiB JVM or Python
   address-space budget and a 60-second timeout.
   The worker gets two CPUs and 6 GiB RAM; the JVM heap is capped at 3 GiB
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
Persistent job execution and saved configurations are tracked in #78; expanded sources in #79. Data Node
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
host. Run the preparation and probe scripts on the host.

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

Use a separate project/target per dataset when resource IDs overlap. Start and stop services with Docker Compose. The web application communicates
with configured services over HTTP and has no Docker socket access.

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
