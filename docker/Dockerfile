FROM maven:3.8.5-openjdk-17 AS maven_tool_chain
COPY pom.xml /tmp/
COPY . /tmp/
WORKDIR /tmp/
RUN --mount=type=cache,id=excel2fhir-maven,target=/root/.m2,sharing=locked \
    mvn -B clean install


FROM amazoncorretto:17.0.20-alpine

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"

COPY --from=maven_tool_chain /tmp/target/excel2fhir.jar /app/excel2fhir.jar
COPY input/FHIR_Testdatengenerator_Vorlage.xlsx /app/input/
COPY docker/docker-entrypoint.sh /app/

WORKDIR /app
RUN apk upgrade --no-cache \
    && apk add --no-cache su-exec \
    && mkdir -p /app/outputGlobal \
    && chown -R 1001:0 /app/outputGlobal \
    && chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["sh", "/app/docker-entrypoint.sh"]
