# Import existing Synthea bundles

The importer reads Synthea R4 patient bundles, fills Excel workbooks and converts
them to FHIR. Each JSON file should contain one patient. Files without a patient
are listed as skipped. See [supported input](synthea-clinical-import.md) for the
clinical fields and mappings.

## Run with Docker

```sh
docker build -f docker/synthea.Dockerfile -t excel2fhir-synthea .
docker run --rm \
  -v /absolute/path/to/synthea/fhir:/input:ro \
  -v /absolute/path/to/results:/output \
  excel2fhir-synthea -i /input -o /output
```

The [common converter options](converter-usage.md#common-options) apply to this
command, including external KDS variants, output formats and optional validation.

## Results

Output follows the [Synthea run layout](synthea-workflow.md#output), with run names
ending in `synthea-import`. Each source has an original bundle, workbook,
projection report and converter log. `details/reports/summary.json` is updated
after every source file and records successful, failed and skipped inputs.

`environment.json` records tool versions and SHA-256 checksums for the JAR,
template, scripts and mapping files. For repeatable runs, retain the Docker image
ID together with the inputs and reports. A new image build may use different
operating-system packages.

## Local execution

Requirements: Python 3.10+, JDK 17+, Maven and LibreOffice with Java/UNO support.
On Debian/Ubuntu the relevant packages include `python3`, `openjdk-17-jdk-headless`,
`libreoffice-calc` and `libreoffice-java-common`. On macOS, the importer detects
LibreOffice at `/Applications/LibreOffice.app`.

```sh
mvn test package
python3 scripts/run_synthea_cases.py -i /path/to/synthea/fhir
```

The output root defaults to `outputGlobal/`; use `-f` for one source file.
The pipeline uses `Europe/Berlin` consistently in Python, LibreOffice and Java.
FHIR timestamps with explicit offsets retain their instant.

## Run the generator locally

Build the pinned generator in an existing Synthea source directory. The source
repository and revision are specified in `docker/synthea.Dockerfile` and
`scripts/synthea-version.txt`. Copy the resulting JAR to `target/synthea.jar` and
record its commit in `target/synthea-revision.txt`.

Then run:

```sh
python3 scripts/run_synthea_workflow.py -- -p 1
```

The workflow checks the recorded revision against the pinned mapping baseline.
The [Docker workflow](synthea-workflow.md) includes this generator setup.
