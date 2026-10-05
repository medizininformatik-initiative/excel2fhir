# Local FHIR servers and Data Portal

Use Docker Compose 2.24.4 or newer. A **Compose profile** selects services;
a **configuration** controls dataset generation; a **FHIR profile** specifies
resource constraints.

```sh
# Workbench and dataset generation (http://localhost:5184)
docker compose -f web/compose.yml up -d --build
# Independent FHIR servers, selectable together
docker compose -f web/compose.yml --profile blaze --profile hapi up -d --build
# Local Data Portal with the shared Blaze; HAPI can also remain selected
docker compose -f web/compose.yml --profile data-portal --profile hapi up -d --build
```

| Service | Local address | Persistent storage |
| --- | --- | --- |
| Workbench | http://localhost:5184 | `workbench` |
| Blaze FHIR R4 | http://localhost:5190/fhir | `blaze-data` |
| HAPI FHIR R4 | http://localhost:5191/fhir | `hapi-data` (PostgreSQL) |
| Data Portal | https://localhost:5192 | Portal PostgreSQL, Elasticsearch, Keycloak PostgreSQL, TLS/login setup |
| TORCH | http://localhost:5193 | `torch-data` |
| TORCH result files | http://localhost:5194 | `torch-data` (read-only file service) |

`blaze` and `data-portal` select the same Blaze service and volume. Changing
between these Compose profiles preserves its resources. HAPI has a separate
database and port. All volume names are scoped to the Compose project. The
services bind published ports to loopback. The application uses HTTP to reach
services; service lifecycle is controlled through Compose.

Generation produces datasets. Loading a dataset into a chosen FHIR server is a
separate, explicit operation. Keep variants with overlapping resource IDs in
separate targets. Stopping services retains their volumes. To stop one server:

```sh
docker compose -f web/compose.yml stop blaze
docker compose -f web/compose.yml stop hapi hapi-db
```

Changing profile flags does not stop already running services. To stop the whole
project while retaining data:

```sh
docker compose -f web/compose.yml --profile data-portal --profile hapi down
```

Blaze enforces referential integrity by default. For datasets intentionally
containing missing reference targets, set `BLAZE_ENFORCE_REFERENTIAL_INTEGRITY=false`
in the Compose environment when starting/recreating `blaze`. This affects the
shared Blaze used by the portal too. Other FHIR validation rules still apply.

## Portal login and architecture

The first portal start creates a local TLS certificate and a random password for
user `workbench`. Trust the certificate for `https://localhost:5192` in the browser.
Read the login and export the certificate with:

```sh
docker compose -f web/compose.yml run --rm --no-deps --entrypoint cat portal-init /setup/credentials.txt
docker compose -f web/compose.yml run --rm --no-deps --entrypoint cat portal-init /setup/cert.pem > portal-cert.pem
```

The setup volume retains both across restarts. Keycloak imports the realm only
when initializing its database. Manage subsequent login changes in Keycloak.
The local login has the portal's user, power-user and administrator roles.

The Data Portal includes the upstream UI, backend, PostgreSQL, Elasticsearch,
ontology initializer, Keycloak and its PostgreSQL database, TLS proxy, and
availability updater. It connects its direct CQL broker to Blaze. The Data Node
side provides Blaze and TORCH extraction plus explicit FHIR Data Evaluator runs.
The ontology initializer downloads ontology version `v5.0.0` on first startup;
backend readiness depends on successful indexing.

This is a local synthetic-data deployment. The portal authenticates through
Keycloak; local FHIR/TORCH diagnostic endpoints use loopback access. Direct
internal CQL queries use Blaze. FLARE is needed for the alternative FHIR Search
cohort path; this deployment uses the CQL path. Central FDPG/DSF registration and
external terminology infrastructure require their own deployment configuration.
HAPI is an independent FHIR target; portal queries use Blaze.

## Explicit evaluation

The FHIR Data Evaluator service has zero default replicas. Run it explicitly
against Blaze after loading data:

```sh
docker compose -f web/compose.yml run --rm fhir-data-evaluator
```

Its bundled Measure counts Patients. Reports are uploaded to Blaze and persist in its data volume. FDE measures data
availability; FHIR profile validation is a separate operation. The availability updater reads FDE reports for Elasticsearch. The pinned
updater 0.4.1 currently expects an `elastic/` subdirectory, while ontology v5.0.0
ships a flat archive. Its availability import therefore fails with an empty
ontology; feasibility queries and FDE evaluation work independently. Its explicit
invocation is:

```sh
docker compose -f web/compose.yml run --rm availability-updater
```

## Upstream maintenance

Service definitions extend the selected files under `upstream/`, pinned to
[Data Portal commit ce654d5](https://github.com/medizininformatik-initiative/dataportal/tree/ce654d58f02be004625234504af0315c33d8b294).
`upstream/source.json` records source paths, checksums and small corrections
to the Keycloak database health-check variables and backend proxy prefix.
The local TLS proxy omits host-wide HSTS to preserve other localhost HTTP services. Overrides in
`web/compose.yml` scope volumes, bind loopback ports, bound memory, configure
local authentication, and connect the portal and extraction services to the
shared Blaze. The local initializer constrains the upstream OAuth client to the
portal origin and creates the local account. HAPI uses the
[8.12.0-2 starter](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/tree/image/v8.12.0-2)
with its documented PostgreSQL configuration.

## Verification

`python3 web/integration/test_compose_profiles.py` checks all eight Compose-profile
combinations, loopback bindings, volume isolation and pinned upstream files.
For a running local portal containing synthetic female Patients:

```sh
python3 web/integration/smoke_portal_query.py /absolute/portal-cert.pem /absolute/credentials.json
python3 web/integration/smoke_torch_profile.py PATIENT_ID
```

The first probe authenticates with Keycloak, checks that anonymous queries are
rejected, and compares a real portal CQL query with an independent Blaze FHIR
search. The second explicitly extracts the selected IDs and checks downloaded
TORCH output. These probes create a portal query and extraction artifacts.

The first ontology import and database migration can take several minutes.
Inspect `docker compose -f web/compose.yml logs init-elasticsearch dataportal-backend`
and check `https://localhost:5192/backend/api/v6/actuator/health` with the generated
CA certificate before running the portal probe. Plan Docker memory for both the
selected services and existing workloads; the full portal plus HAPI and generation
needs more memory than the workbench alone. Individual JVM/container limits are
listed in the Compose file.
