#!/bin/sh
set -eu

output_directory="outputGlobal"
temp_directory=""
next_argument=""
for argument in "$@"; do
  if [ "$next_argument" = output ]; then
    output_directory="$argument"
    next_argument=""
    continue
  elif [ "$next_argument" = temp ]; then
    temp_directory="$argument"
    next_argument=""
    continue
  fi
  case "$argument" in
    -o|--output-directory) next_argument=output ;;
    -o=*|--output-directory=*) output_directory="${argument#*=}" ;;
    -t|--temp-directory) next_argument=temp ;;
    -t=*|--temp-directory=*) temp_directory="${argument#*=}" ;;
    -h|--help|-V|--version) exec java -jar /app/excel2fhir.jar "$@" ;;
  esac
done

if [ "$(id -u)" -eq 0 ]; then
  mkdir -p "$output_directory"
  owner="$(stat -c '%u:%g' "$output_directory")"
  if [ "$owner" = "0:0" ]; then
    owner="1001:0"
    chown "$owner" "$output_directory"
  fi
  if [ -n "$temp_directory" ]; then
    mkdir -p "$temp_directory"
    chown "$owner" "$temp_directory"
  fi
  exec su-exec "$owner" java -jar /app/excel2fhir.jar "$@"
fi
exec java -jar /app/excel2fhir.jar "$@"
