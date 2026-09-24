# Convert Excel and CSV

The [template](../input/FHIR_Testdatengenerator_Vorlage.xlsx) and
[demo](../FHIR_Testdatengenerator_Interpolar_Demo.xlsx) provide the supported
columns and dropdowns. Keep their German sheet names and column headings.
`Patient-ID` links rows across sheets; `Fall-Nr` assigns them to a case.
A workbook can contain several patients. See [input fields](template-input-contracts.md)
and [selection lists](clinical-selections.md) for field-level guidance.

## Run with Docker

Edit or replace the supplied workbook in `input/`, then run:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir
```

The Compose file mounts the project at `/workspace` and writes results to
`outputGlobal/`. `-f docker/docker-compose.yml` selects the Compose file in its
subdirectory. To select one workbook, append `-f input/MyCase.xlsx` after
`excel2fhir`; this second `-f` is an Excel2FHIR option.

## Output

Each invocation creates a fresh run directory:

```text
outputGlobal/run-YYYYMMDD_HH-mm-ss-excel-to-fhir/
  fhir/
    Konvertierungsoptionen/  JSON bundles and patients.ndjson
  status.txt                Overall result
  details/
    csv/                    Extracted workbook data
    reports/                Import and requested validation reports
    options/                Effective Converter Options
    logs/                   Converter log
    pending/                Incomplete output for diagnosis
```

Run names use local system time and a numeric suffix when needed.
Previous runs remain available.
Each KDS variant has its own output directory. Multiple inputs receive additional
input directories.

JSON bundles contain all patients of an input by default; `-p 1` creates one
patient per bundle. NDJSON contains one complete patient bundle per line in each
input/variant directory. Both representations contain the same patient data.

## Common options

| Option | Purpose |
| --- | --- |
| `-f FILE` | Select one workbook. |
| `-i DIRECTORY` | Select an input directory; default `input/`. Use either `-f` or `-i`. |
| `-o DIRECTORY` | Output root; default `outputGlobal/`. |
| `--converter-options FILE` | Select an external options file; repeat for multiple KDS variants. |
| `-r FORMATS` | Comma-separated output formats; default `JSON,NDJSON`. |
| `-p COUNT` | Maximum patients per bundle; default all patients of an input. |
| `-v` | Enable FHIR profile and terminology validation. |

Formats are `JSON`, `NDJSON`, `XML`, `JSONGZIP`, `JSONBZ2` and `ZIPJSON`.
NDJSON and ZIPJSON contain individual patient bundles. Use `--help` for logging
and intermediate-file settings. Relative paths are resolved from the working
directory. Custom Docker output paths need a writable volume mount.

For example, explicitly select `input/` and generate one patient per JSON bundle:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir -i input -p 1
```

## Converter Options

The data sheets describe the cases. Converter Options determine their FHIR
representation, including reference directions and patient-ID generation.

Each sheet whose name contains `Konvertierungsoptionen` defines an independent
KDS variant. For example, `Konvertierungsoptionen_A` and
`Konvertierungsoptionen_B` generate two variants of the same cases.
External options files select the variants for an invocation:

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir \
  --converter-options options/KDS-A.config \
  --converter-options options/KDS-B.config
```

Each file contains Properties text, for example:

```properties
SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER = true
SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION = false
```

When external files are selected, they supply the run's options sets. Otherwise,
the converter uses the workbook's options sheets. Missing values use shared
defaults; an input with no options set uses the `default` variant.

Variant directories use the sheet name or options filename without its extension.
Spaces and special characters become `_`; names within an input must be unique.
`details/options/` records every effective setting, including defaults.

`CHECK_INPUT_CONSISTENCY=true` enables the workbook consistency checks. With
`false`, the workbook precheck covers structure and options. Shared converter
checks also apply during CSV processing; see [input checks](contact-input-checks.md).

## CSV input

CSV tables follow the workbook's columns. A run's `details/csv/` directory provides
examples. An options file such as `Case_Konvertierungsoptionen_A.csv` contains
Properties text from the options sheet.

Place the CSV files in `input/` and run:

```sh
docker compose -f docker/docker-compose.yml run --build --rm --entrypoint java excel2fhir \
  -cp /app/excel2fhir.jar de.uni_leipzig.life.csv2fhir.Main
```

The converter groups files by their prefix: `Case_Person.csv` and `Case_Fall.csv`
belong to the same input. Multiple inputs in one directory are processed together.
Ambiguous prefixes such as `Case_Person.csv` and `Case-Person.csv` produce an error.
CSV runs use the same options and output conventions, with the suffix `csv-to-fhir`.

## Results and errors

| Run status | Exit code | Meaning |
| --- | --- | --- |
| `NOT_VALIDATED` | 0 | Conversion completed with FHIR validation disabled. |
| `COMPLETE` | 0 | Conversion and requested validation completed successfully. |
| `NOT_CHECKED` | 1 | Some requested terminology checks could not run. |
| `FAILED` | 1 | An input, conversion or validation error occurred. |

Incomplete imports remain in `details/pending/`. Completed FHIR output remains
available when validation finds errors. Inspect `status.txt` and the reports for
the affected input and KDS variant.

See [import reports](import-report.md) and [FHIR validation](fhir-validation.md).

## Local execution

With JDK 17 and Maven 3.x:

```sh
mvn test package
java -jar target/excel2fhir.jar
```

This reads workbooks from `input/`. For CSV input:

```sh
java -cp target/excel2fhir.jar de.uni_leipzig.life.csv2fhir.Main
```
