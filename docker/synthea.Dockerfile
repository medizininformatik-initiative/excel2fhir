FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY FHIR_Testdatengenerator_Vorlage.xlsx FHIR_Testdatengenerator_Interpolar_Demo.xlsx ./
RUN mvn -B test package

FROM debian:bookworm-slim
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends \
    ca-certificates python3 openjdk-17-jdk-headless libreoffice-calc \
    libreoffice-java-common fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/*
ENV LANG=C.UTF-8
WORKDIR /app
COPY --from=build /build/target/excel2fhir.jar ./target/excel2fhir.jar
COPY scripts ./scripts
COPY third-party ./third-party
COPY LICENSE FHIR_Testdatengenerator_Vorlage.xlsx ./
ENTRYPOINT ["python3", "/app/scripts/run_synthea_cases.py"]
