# Reproducing checked-in test data

This repository contains the generator. The generated test data live in the separate `kerndatensatz-testdaten` repository.

## Expected checkout layout

The helper script works without configuration when both repositories are checked out next to each other:

```text
workspace/
  csv2fhir/
  kerndatensatz-testdaten/
```

From the generator repository run:

```bash
./scripts/generate-known-testdata.sh
```

If the test-data repository is elsewhere, set `TESTDATA_ROOT` to its `Test_Data` directory:

```bash
TESTDATA_ROOT=/path/to/kerndatensatz-testdaten/Test_Data ./scripts/generate-known-testdata.sh
```

## What the script generates

The script regenerates the currently migrated workbooks with validation enabled and both result formats:

```text
--result-file-format ZIPJSON --result-file-format NDJSON -v
```

It preserves the committed patient grouping by using the same `-p` values as the existing output files:

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

`Vorhofflimmern/VHF-Testdaten_04-MixedCases.xlsx` is intentionally generated without `-p`, because its committed output names are based on conversion-option variants `a` to `d`, not patient ranges.
