# excel2fhir

Generate FHIR R4 test data for the German MII Core Data Set (KDS) from Excel
workbooks or CSV files.

## Usage

```sh
docker compose -f docker/docker-compose.yml run --build --rm excel2fhir
```

`input/` contains a ready-to-run [example workbook](input/FHIR_Testdatengenerator_Vorlage.xlsx).
Edit or replace it with your own cases, keeping the sheet names and column headings.
The converter processes all workbooks in that directory.

Each run writes JSON and NDJSON to `outputGlobal/run-…/fhir/`.
`status.txt` shows the result; `details/` contains reports, effective options and
intermediate files. Add `-v` to enable FHIR profile and terminology validation.

Converter Options control the KDS variant. Use the workbook's options sheet or
select external options files. See [converter usage](docs/converter-usage.md)
for input selection, options and output formats.

## Other inputs

- [CSV files](docs/converter-usage.md#csv-input)
- [Patient histories generated with Synthea](docs/synthea-workflow.md)
- [Existing Synthea bundles](docs/synthea-manual.md)

## Development

```sh
mvn test package
python3 -m unittest discover -s scripts/tests -v
```

[Reproduce test data](docs/reproduce-testdata.md) · [Architecture](docs/architecture.md) · [Local execution](docs/converter-usage.md#local-execution) · [License](LICENSE)
