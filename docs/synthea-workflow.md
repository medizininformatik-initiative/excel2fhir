# Fill Excel with Synthea

Synthea generates patient histories. The importer fills the Excel template and
runs the shared Excel-to-FHIR converter. The resulting workbooks can be edited
and converted again with the desired KDS variants.

## Run with Docker

```sh
docker compose -f compose.synthea.yml run --build --rm synthea
```

With this command, the supplied Compose settings request one living patient aged
30–80 in Massachusetts, with their full simulated history through 12 September
2026. Patient and clinician seeds are both `20260912`. Synthea may also export
patients who died during generation. Histories can include encounters, diagnoses,
medications, procedures, observations and immunizations; the simulated life
course determines which occur.

## Configure Synthea generation

Place Synthea arguments after `--`:

| Argument | Controls |
| --- | --- |
| `-p COUNT` | Requested population size. |
| `-a MIN-MAX` | Patient age range. |
| `-g F` or `-g M` | Patient sex. |
| `-s SEED` | Random seed for patient generation. |
| `STATE [CITY]` | US location used for the population. |
| `--exporter.years_of_history=YEARS` | Exported history length; `0` includes the full history. |

A seed is the starting value for Synthea's random choices. The same patient seed
(`-s`), clinician seed (`-cs`), Synthea version, settings and simulation dates
reproduce the same patients and histories. When a seed is omitted, Synthea uses
the current system time, so runs normally produce different patients. The Compose
defaults above supply fixed seeds for a repeatable example.

For example, generate five patients aged 30–80:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea -- -p 5 -a 30-80
```

Generate two female patients aged 60–70, exporting the last five years:

```sh
docker compose -f compose.synthea.yml run --build --rm synthea \
  -- -p 2 -g F -a 60-70 --exporter.years_of_history=5
```

Explicit arguments replace the Compose command, including its seeds and dates.
Synthea supplies defaults for omitted arguments; the workflow exports full
histories unless a history length is specified. To reproduce the supplied sample,
use the seeds and dates in [compose.synthea.yml](../compose.synthea.yml).

See Synthea's [command-line reference](https://github.com/synthetichealth/synthea/wiki/Basic-Setup-and-Running#running-synthea)
and [configuration reference](https://github.com/synthetichealth/synthea/wiki/Common-Configuration)
for further settings.

## Choose a KDS variant

Place [converter options](converter-usage.md#common-options) before `--`, for
example `--converter-options options/KDS-A.config`, `-r XML` or `-v`.
Repeat `--converter-options` for additional KDS variants. Generated workbooks
contain the shared converter defaults; external files select the variants for
the current invocation. Before `--`, `-p` sets patients per bundle; after `--`,
it sets the Synthea population size.

## Output

Each invocation creates `outputGlobal/run-…-synthea/`:

| Path | Content |
| --- | --- |
| `excel/` | One editable workbook per source patient. |
| `fhir/` | Selected formats, grouped by input and KDS variant. |
| `details/options-input/` | Copies of selected external options files. |
| `details/cases/` | Per-source converter runs, effective options, import and requested validation reports. |
| `details/reports/` | Overall results, source comparisons and tool versions. |
| `details/sources/` / `details/logs/` | Original Synthea output and logs. |
| `status.txt` | Overall result. |

`NOT_VALIDATED` with exit code 0 means import and source comparison succeeded
with FHIR validation disabled. With `-v`, `COMPLETE` indicates success and
`NOT_CHECKED` indicates unavailable terminology checks, with exit code 1.
`FAILED` identifies a failed step; inspect the reports and retained output.

Under Linux, the complete workflow uses the mounted output directory owner's
UID/GID so the generated files remain editable by that user.

## Edit a generated workbook

Open the workbook, edit its data sheets, then pass its path to Excel2FHIR using
`-f`. Apply the desired options sheets or external files as described in
[converter usage](converter-usage.md).

## Further reading

- [Import existing bundles and local execution](synthea-manual.md)
- [Supported data and mappings](synthea-clinical-import.md)
- [Hospital example](../examples/synthea-hospital/README.md)
- [FHIR validation](fhir-validation.md)
