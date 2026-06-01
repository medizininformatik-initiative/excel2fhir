FROM maven:3.8.3-openjdk-17 AS maven_tool_chain
COPY pom.xml /tmp/
COPY . /tmp/
WORKDIR /tmp/
RUN mvn clean install


FROM amazoncorretto:17-alpine

COPY --from=maven_tool_chain /tmp/target/excel2fhir.jar /app/excel2fhir.jar
COPY FHIR_Testdatengenerator_Vorlage.xlsx /app/
COPY docker/docker-entrypoint.sh /app/

WORKDIR /app
RUN mkdir -p /app/outputGlobal /app/outputLocal \
    && chown -R 1001:0 /app/outputGlobal /app/outputLocal \
    && chmod +x /app/docker-entrypoint.sh
USER 1001

ENTRYPOINT ["sh", "/app/docker-entrypoint.sh"]
