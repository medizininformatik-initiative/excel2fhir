#!/bin/sh
set -eu

JAR_FILE="/app/excel2fhir.jar"
DEFAULT_INPUT_FILE="/app/FHIR_Testdatengenerator_Vorlage.xlsx"
DEFAULT_OUTPUT_DIRECTORY="/app/outputGlobal"
DEFAULT_TEMP_DIRECTORY="/app/outputLocal"
RUNTIME_USER="1001:0"

has_input=false
has_output_directory=false
has_temp_directory=false
show_help_or_version=false
input_file=""
input_directory=""
output_directory="$DEFAULT_OUTPUT_DIRECTORY"
temp_directory="$DEFAULT_TEMP_DIRECTORY"
next_argument_is_input_file=false
next_argument_is_input_directory=false
next_argument_is_output_directory=false
next_argument_is_temp_directory=false

run_java() {
  if [ "$(id -u)" -eq 0 ] && command -v su-exec >/dev/null 2>&1; then
    exec su-exec "$RUNTIME_USER" java -jar "$JAR_FILE" "$@"
  fi

  exec java -jar "$JAR_FILE" "$@"
}

prepare_output_directory() {
  if [ "$(id -u)" -eq 0 ]; then
    mkdir -p "$1"
    chmod a+rwX "$1"
  fi
}

for argument in "$@"; do
  if [ "$next_argument_is_input_file" = true ]; then
    input_file="$argument"
    next_argument_is_input_file=false
    continue
  fi
  if [ "$next_argument_is_input_directory" = true ]; then
    input_directory="$argument"
    next_argument_is_input_directory=false
    continue
  fi
  if [ "$next_argument_is_output_directory" = true ]; then
    output_directory="$argument"
    next_argument_is_output_directory=false
    continue
  fi
  if [ "$next_argument_is_temp_directory" = true ]; then
    temp_directory="$argument"
    next_argument_is_temp_directory=false
    continue
  fi

  case "$argument" in
    -f|--input-file)
      has_input=true
      next_argument_is_input_file=true
      ;;
    -f=*|--input-file=*)
      has_input=true
      input_file="${argument#*=}"
      ;;
    -i|--input-directory)
      has_input=true
      next_argument_is_input_directory=true
      ;;
    -i=*|--input-directory=*)
      has_input=true
      input_directory="${argument#*=}"
      ;;
    -o|--output-directory)
      has_output_directory=true
      next_argument_is_output_directory=true
      ;;
    -o=*|--output-directory=*)
      has_output_directory=true
      output_directory="${argument#*=}"
      ;;
    -t|--temp-directory)
      has_temp_directory=true
      next_argument_is_temp_directory=true
      ;;
    -t=*|--temp-directory=*)
      has_temp_directory=true
      temp_directory="${argument#*=}"
      ;;
    -h|--help|-V|--version)
      show_help_or_version=true
      ;;
  esac
done

if [ "$show_help_or_version" = true ]; then
  run_java "$@"
fi

if [ "$#" -eq 0 ]; then
  set -- -f "$DEFAULT_INPUT_FILE"
elif [ "$has_input" = false ]; then
  set -- -f "$DEFAULT_INPUT_FILE" "$@"
fi

if [ "$has_output_directory" = false ]; then
  set -- "$@" -o "$DEFAULT_OUTPUT_DIRECTORY"
fi

if [ "$has_temp_directory" = false ]; then
  set -- "$@" -t "$DEFAULT_TEMP_DIRECTORY"
fi

if [ -n "$input_file" ] && [ ! -f "$input_file" ]; then
  printf "Input file not found inside the container: %s\n" "$input_file" >&2
  printf "Put the workbook in the repository directory and pass it as /app/input/<file-name>.xlsx.\n" >&2
  exit 1
fi

if [ -n "$input_directory" ] && [ ! -d "$input_directory" ]; then
  printf "Input directory not found inside the container: %s\n" "$input_directory" >&2
  printf "Mount or choose a directory below /app/input.\n" >&2
  exit 1
fi

prepare_output_directory "$output_directory"
prepare_output_directory "$temp_directory"

printf "Starting excel2fhir with arguments:\n"
printf "  %s\n" "$*"

run_java "$@"
