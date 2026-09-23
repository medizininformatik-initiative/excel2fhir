FROM maven:3.8.5-openjdk-17 AS synthea-build
COPY scripts/synthea-version.txt /synthea-version.txt
WORKDIR /synthea
RUN git init && git remote add origin https://github.com/astruebi/synthea.git \
    && git fetch --depth 1 origin "$(cat /synthea-version.txt)" \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "$(cat /synthea-version.txt)" \
    && ./gradlew --no-daemon shadowJar

FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY FHIR_Testdatengenerator_Vorlage.xlsx FHIR_Testdatengenerator_Interpolar_Demo.xlsx ./
RUN --mount=type=cache,id=excel2fhir-maven,target=/root/.m2,sharing=locked \
    mvn -B test package

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends \
    ca-certificates python3 openjdk-17-jdk-headless libreoffice-calc \
    libreoffice-java-common fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/*
ENV LANG=C.UTF-8
WORKDIR /app
COPY --from=build /build/target/excel2fhir.jar ./target/excel2fhir.jar
COPY scripts ./scripts
COPY src/main/resources/workbook-absent-reasons.json ./src/main/resources/workbook-absent-reasons.json
COPY third-party ./third-party
COPY LICENSE FHIR_Testdatengenerator_Vorlage.xlsx ./
ENTRYPOINT ["python3", "/app/scripts/run_synthea_cases.py"]

# Opt-in build target for the full workflow. The default target below retains
# the existing interface used by the importer and its CI job.
FROM runtime AS workflow
COPY --from=synthea-build /synthea/build/libs/synthea-with-dependencies.jar ./target/synthea.jar
COPY --from=synthea-build /synthea-version.txt ./target/synthea-revision.txt
COPY --from=synthea-build /synthea/LICENSE /synthea/NOTICE ./third-party/synthea/
ENTRYPOINT ["python3", "/app/scripts/run_synthea_container.py"]

FROM runtime AS importer
