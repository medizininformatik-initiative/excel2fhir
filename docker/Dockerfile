FROM maven:3.8.3-openjdk-17 AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/
COPY . /tmp/
WORKDIR /tmp/
RUN mvn clean install


FROM amazoncorretto:17-alpine

COPY --from=MAVEN_TOOL_CHAIN /tmp/target/excel2fhir.jar /app/excel2fhir.jar
COPY FHIR_Testdatengenerator_Vorlage.xlsx /app/
COPY docker/docker-entrypoint.sh /app/

WORKDIR /app
USER 1001

ENTRYPOINT  sh docker-entrypoint.sh
