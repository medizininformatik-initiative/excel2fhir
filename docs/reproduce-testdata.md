# Reproducing checked-in test data

This repository contains the generator. The generated test data live in the separate `kerndatensatz-testdaten` repository.

From the generator repository run:

```bash
./scripts/generate-known-testdata.sh
```

The default input is `../kerndatensatz-testdaten/Test_Data`. If it is elsewhere, set `TESTDATA_ROOT` to its `Test_Data` directory:

```bash
TESTDATA_ROOT=/path/to/kerndatensatz-testdaten/Test_Data ./scripts/generate-known-testdata.sh
```

## What the script generates

The script converts the selected workbooks with validation enabled and both result formats:

```text
--result-file-format ZIPJSON --result-file-format NDJSON -v
```

Each workbook produces a new `run-…-excel-to-fhir` directory beside it. Results are
in `fhir/`; reports and intermediate files are in `details/`. The inputs use the
[current workbook schema](template-input-contracts.md).

The script uses these patient counts per bundle:

```text
Vorhofflimmern/VHF-Testdaten_01.xlsx                         -p 1000
Vorhofflimmern/VHF-Testdaten_02-andereDiagnose.xlsx           -p 1000
Vorhofflimmern/VHF-Testdaten_03-andererLaborwert.xlsx         -p 1000
POLAR_WP_1.1_v2/POLAR_WP_1.1_v2.xlsx                         -p 1650
POLAR_WP_1.1_v3_MultipleEncountersOverlappingStartEnd/POLAR_WP_1.1_v3_MultipleEncountersOverlappingStartEnd.xlsx -p 70
POLAR_WP_1.x_v1_MixedTestCasesForAllWorkpackages/POLAR_WP_1.x_v1_MixedTestCasesForAllWorkpackages.xlsx -p 17
POLAR_WP_1.1_v4a_ReferencesConditionsToEncounter/POLAR_WP_1.1_v4a_ReferencesConditionsToEncounter.xlsx -p 165
POLAR_WP_1.1_v4b_ReferencesOnlyConditionsToEncounter/POLAR_WP_1.1_v4b_ReferencesOnlyConditionsToEncounter.xlsx -p 165
Polar/POLAR_Testdaten_Original_UKB.xlsx                       -p 15
Polar/POLAR_Testdaten_Original_UKE.xlsx                       -p 20
Polar/POLAR_Testdaten_Original_UKFAU.xlsx                     -p 11
Polar/POLAR_Testdaten_Original_UKSH.xlsx                      -p 5
Polar/POLAR_Testdaten_Original_UKFR.xlsx                      -p 10
```

`Vorhofflimmern/VHF-Testdaten_04-MixedCases.xlsx` uses the default patient count
and groups its output by conversion-option variants `a` to `d`.
