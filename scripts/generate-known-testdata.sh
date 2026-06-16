#!/usr/bin/env bash
set -euo pipefail

# Re-generate selected checked-in test data workbooks with excel2fhir.
#
# Expected default layout:
#   <workspace>/csv2fhir
#   <workspace>/kerndatensatz-testdaten
#
# Override TESTDATA_ROOT when the test-data repository is elsewhere:
#   TESTDATA_ROOT=/path/to/kerndatensatz-testdaten/Test_Data ./scripts/generate-known-testdata.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXCEL2FHIR_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TESTDATA_ROOT="${TESTDATA_ROOT:-$(cd "$EXCEL2FHIR_DIR/../kerndatensatz-testdaten/Test_Data" && pwd)}"
MAIN_CLASS="de.uni_leipzig.imise.Excel2FhirMain"
COMMON_ARGS="--result-file-format ZIPJSON --result-file-format NDJSON -v"

run_generator() {
  local args="$1"
  mvn -q compile exec:java \
    -Dexec.mainClass="$MAIN_CLASS" \
    -Dexec.args="$args"
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Missing expected file: $file" >&2
    echo "Set TESTDATA_ROOT to the Test_Data directory of kerndatensatz-testdaten." >&2
    exit 1
  fi
}

cd "$EXCEL2FHIR_DIR"

VHF_DIR="$TESTDATA_ROOT/Vorhofflimmern"
POLAR_DIR="$TESTDATA_ROOT/POLAR_WP_1.1_v2"
POLAR_V3_DIR="$TESTDATA_ROOT/POLAR_WP_1.1_v3_MultipleEncountersOverlappingStartEnd"
POLAR_MIX_DIR="$TESTDATA_ROOT/POLAR_WP_1.x_v1_MixedTestCasesForAllWorkpackages"
POLAR_V4A_DIR="$TESTDATA_ROOT/POLAR_WP_1.1_v4a_ReferencesConditionsToEncounter"
POLAR_V4B_DIR="$TESTDATA_ROOT/POLAR_WP_1.1_v4b_ReferencesOnlyConditionsToEncounter"
POLAR_ORIGINAL_DIR="$TESTDATA_ROOT/Polar"

require_file "$VHF_DIR/VHF-Testdaten_01.xlsx"
require_file "$VHF_DIR/VHF-Testdaten_02-andereDiagnose.xlsx"
require_file "$VHF_DIR/VHF-Testdaten_03-andererLaborwert.xlsx"
require_file "$VHF_DIR/VHF-Testdaten_04-MixedCases.xlsx"
require_file "$POLAR_DIR/POLAR_WP_1.1_v2.xlsx"
require_file "$POLAR_V3_DIR/POLAR_WP_1.1_v3_MultipleEncountersOverlappingStartEnd.xlsx"
require_file "$POLAR_MIX_DIR/POLAR_WP_1.x_v1_MixedTestCasesForAllWorkpackages.xlsx"
require_file "$POLAR_V4A_DIR/POLAR_WP_1.1_v4a_ReferencesConditionsToEncounter.xlsx"
require_file "$POLAR_V4B_DIR/POLAR_WP_1.1_v4b_ReferencesOnlyConditionsToEncounter.xlsx"
require_file "$POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKB.xlsx"
require_file "$POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKE.xlsx"
require_file "$POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKFAU.xlsx"
require_file "$POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKSH.xlsx"
require_file "$POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKFR.xlsx"

run_generator "-f $VHF_DIR/VHF-Testdaten_01.xlsx -o $VHF_DIR -t $VHF_DIR/outputLocal $COMMON_ARGS -p 1000"
run_generator "-f $VHF_DIR/VHF-Testdaten_02-andereDiagnose.xlsx -o $VHF_DIR -t $VHF_DIR/outputLocal $COMMON_ARGS -p 1000"
run_generator "-f $VHF_DIR/VHF-Testdaten_03-andererLaborwert.xlsx -o $VHF_DIR -t $VHF_DIR/outputLocal $COMMON_ARGS -p 1000"

# No -p here: this workbook uses conversion-option variants a/b/c/d and the
# committed file names are option-based, not patient-range based.
run_generator "-f $VHF_DIR/VHF-Testdaten_04-MixedCases.xlsx -o $VHF_DIR -t $VHF_DIR/outputLocal $COMMON_ARGS"

run_generator "-f $POLAR_DIR/POLAR_WP_1.1_v2.xlsx -o $POLAR_DIR -t $POLAR_DIR/outputLocal $COMMON_ARGS -p 1650"

run_generator "-f $POLAR_V3_DIR/POLAR_WP_1.1_v3_MultipleEncountersOverlappingStartEnd.xlsx -o $POLAR_V3_DIR -t $POLAR_V3_DIR/outputLocal $COMMON_ARGS -p 70"

run_generator "-f $POLAR_MIX_DIR/POLAR_WP_1.x_v1_MixedTestCasesForAllWorkpackages.xlsx -o $POLAR_MIX_DIR -t $POLAR_MIX_DIR/outputLocal $COMMON_ARGS -p 17"

run_generator "-f $POLAR_V4A_DIR/POLAR_WP_1.1_v4a_ReferencesConditionsToEncounter.xlsx -o $POLAR_V4A_DIR -t $POLAR_V4A_DIR/outputLocal $COMMON_ARGS -p 165"

run_generator "-f $POLAR_V4B_DIR/POLAR_WP_1.1_v4b_ReferencesOnlyConditionsToEncounter.xlsx -o $POLAR_V4B_DIR -t $POLAR_V4B_DIR/outputLocal $COMMON_ARGS -p 165"

run_generator "-f $POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKB.xlsx -o $POLAR_ORIGINAL_DIR -t $POLAR_ORIGINAL_DIR/outputLocal $COMMON_ARGS -p 15"

run_generator "-f $POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKE.xlsx -o $POLAR_ORIGINAL_DIR -t $POLAR_ORIGINAL_DIR/outputLocal $COMMON_ARGS -p 20"

run_generator "-f $POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKFAU.xlsx -o $POLAR_ORIGINAL_DIR -t $POLAR_ORIGINAL_DIR/outputLocal $COMMON_ARGS -p 11"

run_generator "-f $POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKSH.xlsx -o $POLAR_ORIGINAL_DIR -t $POLAR_ORIGINAL_DIR/outputLocal $COMMON_ARGS -p 5"

run_generator "-f $POLAR_ORIGINAL_DIR/POLAR_Testdaten_Original_UKFR.xlsx -o $POLAR_ORIGINAL_DIR -t $POLAR_ORIGINAL_DIR/outputLocal $COMMON_ARGS -p 10"
