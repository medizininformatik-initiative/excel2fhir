# excel2fhir

Generate FHIR R4 test data for the German MII Core Data Set (KDS) from Excel,
CSV or Synthea in the local web workbench. Choose the services you need below.

## Start with Docker

Use Docker Compose **2.24.4 or newer**. All four options include the workbench at
<http://localhost:5184>, with input selection, configuration, generation, logs
and result downloads.

### Data generation with Blaze and Data Portal

```sh
docker compose -f web/compose.yml --profile data-portal up -d --build
```

Data Portal: <https://localhost:5192> · Blaze: <http://localhost:5190/fhir>.
This includes the portal catalogue, authentication, TORCH extraction and FHIR
Data Evaluator. Allow several minutes for the first ontology import.
Follow [portal login and certificate setup](web/deployment/README.md#portal-login-and-architecture).

### Data generation with Blaze

```sh
docker compose -f web/compose.yml --profile blaze up -d --build
```

Blaze: <http://localhost:5190/fhir>.

### Data generation with HAPI

```sh
docker compose -f web/compose.yml --profile hapi up -d --build
```

HAPI: <http://localhost:5191/fhir>.

### Data generation only

```sh
docker compose -f web/compose.yml up -d --build
```

### System requirements

The following are **planning recommendations for small local datasets**, not
verified minimum hardware requirements. RAM means memory available to Docker;
reserve additional host memory for the operating system and other applications.

| Selected services | Docker RAM | Available CPU cores | Free Docker disk space |
| --- | ---: | ---: | ---: |
| Generation, Blaze and Data Portal | 24 GiB | 6 | 60 GB |
| Generation and Blaze | 10 GiB | 4 | 25 GB |
| Generation and HAPI | 12 GiB | 4 | 30 GB |
| Generation only | 8 GiB | 2 | 20 GB |

Images, build cache, databases and retained datasets consume disk space. Large
populations, multiple variants and other Docker workloads need additional capacity.
See [resource sizing and validation limits](web/deployment/README.md#resource-sizing)
for the basis of these budgets and the configurations exercised so far.

## Generate and use data

Open the workbench, select a bundled example or upload an input, choose the
configuration and start generation. Inspect the results and download a dataset
or the complete run archive. See the [workbench guide](web/README.md) for saved
configurations, Synthea generation and reports.

Generation saves downloadable datasets. Loading a dataset into a FHIR server is
a separate operation; see [local services](web/deployment/README.md).
Service data and workbench runs persist in Docker volumes. Selecting fewer
profiles does not stop services already running; see
[stopping services](web/deployment/README.md#stopping-services).

## Further documentation

- [Command-line conversion: Excel and CSV](docs/converter-usage.md)
- [Command-line Synthea workflow](docs/synthea-workflow.md) and [existing Synthea bundles](docs/synthea-manual.md)
- [Ready-to-run workbook](input/FHIR_Testdatengenerator_Vorlage.xlsx), [input fields](docs/template-input-contracts.md) and [selection lists](docs/clinical-selections.md)
- [Local FHIR services, portal setup and evaluation](web/deployment/README.md)

## Development

```sh
mvn test package
python3 -m unittest discover -s scripts/tests -v
```

[Reproduce test data](docs/reproduce-testdata.md) · [Architecture](docs/architecture.md) · [License](LICENSE) · [Third-party data](NOTICE.md)
