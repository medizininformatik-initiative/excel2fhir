# excel2fhir

`excel2fhir` converts structured Excel workbooks into synthetic FHIR R4 test data
bundles. It is intended for creating coherent, referenced test data in the context of
the German Medical Informatics Initiative (MII) Kerndatensatz.

The input is an Excel workbook with predefined sheets for patients, encounters,
diagnoses, procedures, observations, medication, clinical documentation and conversion
options. The generator splits the workbook into intermediate CSV files and then creates
FHIR resources and bundles.

## Current Scope

The converter currently supports the following logical data areas:

- Patient data (`Person`)
- Encounter data (`Fall`)
- Diagnoses / conditions (`Diagnose`)
- Procedures (`Prozedur`)
- Laboratory observations (`Laborbefund`)
- Vital signs / clinical documentation (`Klinische Dokumentation`)
- Medication data (`Medikation`)
- Document references (`DocumentReference`)
- Consent data (`Consent`, currently represented on the patient sheet)
- Conversion options (`Konvertierungsoptionen`)

The generated resources aim to be suitable for MII KDS-oriented test data scenarios.
Profile conformance depends on the current converter implementation, bundled validation
resources and the profile versions used by downstream systems.

## Excel Templates

The repository contains two workbooks:

- `FHIR_Testdatengenerator_Vorlage.xlsx` - the default input template.
- `FHIR_Testdatengenerator_Interpolar_Demo.xlsx` - a small demo workbook with coherent
  example data.

The sheet names and many comments in the workbook are German because the template is
aligned with the German MII Kerndatensatz context.

When running the application without input arguments, `FHIR_Testdatengenerator_Vorlage.xlsx`
from the application directory is used as the default input file.

## Requirements

- Java 17
- Maven 3.x

## Quick Start

Build and run the default template:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=de.uni_leipzig.imise.Excel2FhirMain
```

Run the demo workbook explicitly:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=de.uni_leipzig.imise.Excel2FhirMain \
  -Dexec.args="-f FHIR_Testdatengenerator_Interpolar_Demo.xlsx"
```

Run with FHIR resource validation enabled:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=de.uni_leipzig.imise.Excel2FhirMain \
  -Dexec.args="-v -f FHIR_Testdatengenerator_Interpolar_Demo.xlsx"
```

Create the executable JAR:

```bash
mvn package
java -jar target/excel2fhir.jar -f FHIR_Testdatengenerator_Interpolar_Demo.xlsx
```

## Output

By default, output is written next to the input workbook:

- `outputLocal/` contains intermediate CSV files extracted from the Excel workbook.
- `outputGlobal/` contains the generated FHIR bundle files.

The default result format is JSON. Additional output formats can be selected with
`-r` / `--result-file-format`:

- `JSON`
- `XML`
- `NDJSON`
- `JSONGZIP`
- `JSONBZ2`

Example:

```bash
java -jar target/excel2fhir.jar \
  -f FHIR_Testdatengenerator_Interpolar_Demo.xlsx \
  -r JSON,NDJSON
```

## Command Line Options

```text
-f,   --input-file INPUT-File
      Input Excel file. If specified, the input directory is ignored.

-i,   --input-directory INPUT-DIRECTORY
      Directory containing Excel files to convert.

-o,   --output-directory OUTPUT-DIRECTORY
      Directory for generated FHIR result files.

-t,   --temp-directory TEMP-DIRECTORY
      Directory for intermediate CSV files.

-r,   --result-file-format RESULT-FILE-FORMAT
      Comma-separated output formats: JSON, XML, NDJSON, JSONGZIP or JSONBZ2.

-p,   --patients-count PATIENTS-COUNT
      Maximum number of patients per output bundle.

-v,   --validate-bundles / --no-validate-bundles
      Validate generated FHIR resources and include only valid resources.

-vll, --validation-log-level VALIDATION-LOG-LEVEL
      Minimum validation log level. Supported values include ERROR, WARNING, IGNORED and VALID.
```

You can also use the built-in help:

```bash
java -jar target/excel2fhir.jar --help
```

## Validation

There are two validation layers:

1. Excel template validation checks the structure and consistency of the workbook before
   conversion.
2. FHIR resource validation can be enabled with `-v` / `--validate-bundles`.

Strict Excel template validation is enabled by default. It can be configured via
converter options if a test-data scenario intentionally needs more permissive behavior.

When FHIR validation is enabled, invalid resources are not added to the generated bundle.
Validation warnings may still be expected depending on the test-data use case and the
profile versions used.

## Converter Options

Conversion behavior can be configured through the `Konvertierungsoptionen` sheet in the
workbook. Default values are documented in:

```text
src/main/resources/Converter_Options.config
```

Examples include:

- start counters for generated resource identifiers
- whether circular references between encounters and diagnoses/procedures should be generated
- whether missing encounter data should be filled from parent encounters
- whether strict Excel template validation should be active

## Docker

A Docker setup is available for running the default template:

```bash
docker compose -f docker/docker-compose.yml up --build
```

The compose setup mounts `outputGlobal/` and `outputLocal/` from the repository root into
the container.

## Development

Run the test suite:

```bash
mvn test
```

Build the project:

```bash
mvn package
```

The GitHub Actions workflow runs Maven tests, CodeQL analysis, Docker image build and
Trivy vulnerability scanning.

## Repository Layout

```text
FHIR_Testdatengenerator_Vorlage.xlsx          Default Excel template
FHIR_Testdatengenerator_Interpolar_Demo.xlsx  Demo workbook
src/main/java/                                Converter implementation
src/main/resources/                           Mapping and converter option defaults
docker/                                       Docker build and compose setup
.github/workflows/                            CI workflow
```

## License

See [LICENSE](LICENSE).
