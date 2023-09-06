#!/bin/bash

INPUT_DIR=${INPUT_DIR:-input}

printf "Staring excel2fhir fhir with the following configurations:\n"
printf "inputdir: $INPUT_DIR\n\n"

java -jar excel2fhir.jar -i $INPUT_DIR
