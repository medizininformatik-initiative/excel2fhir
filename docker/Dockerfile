FROM eclipse-temurin:17-jre

RUN mkdir -p /app/input
COPY target/excel2fhir.jar /app/excel2fhir.jar
COPY src/main/resources/excel/FHIR_Testdatengenerator_Vorlage.xlsx /app/input
COPY docker/docker-entrypoint.sh /app/

WORKDIR /app
USER 1001

ENTRYPOINT  bash docker-entrypoint.sh


