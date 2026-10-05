FROM maven:3.8.5-openjdk-17 AS converter
WORKDIR /build
COPY pom.xml .
COPY src src
COPY web/catalog/options/contract.json web/catalog/options/contract.json
COPY web/catalog/dar/generated/catalog.json web/catalog/dar/generated/catalog.json
COPY web/backend/ExportDefaults.java .
RUN --mount=type=cache,id=excel2fhir-maven,target=/root/.m2,sharing=locked mvn -B -DskipTests package
RUN java -cp target/excel2fhir.jar ExportDefaults.java /defaults

FROM scratch AS runtime-files
COPY scripts /synthea/scripts
COPY src/main/resources/workbook-absent-reasons.json /synthea/src/main/resources/workbook-absent-reasons.json
COPY FHIR_Testdatengenerator_Vorlage.xlsx /synthea/
COPY --from=converter /build/target/excel2fhir.jar /synthea/target/excel2fhir.jar
COPY --from=converter /build/target/excel2fhir.jar /converter/excel2fhir.jar
COPY input/FHIR_Testdatengenerator_Vorlage.xlsx /converter/input/
COPY FHIR_Testdatengenerator_Interpolar_Demo.xlsx /converter/
COPY --from=converter /defaults/converter-options.config /converter/defaults.config

# The existing Synthea importer fills a copy of the template through LibreOffice.
FROM debian:bookworm-slim AS worker
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends \
    ca-certificates tzdata python3 python3-venv openjdk-17-jdk-headless libreoffice-calc \
    libreoffice-java-common fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/* && python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" LANG=C.UTF-8 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY web/backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY web/backend/*.py .
COPY --from=runtime-files / /
RUN useradd --create-home --uid 10001 workbench && mkdir /data && chown workbench /data
USER workbench
CMD ["python", "worker.py"]

FROM amazoncorretto:17.0.20-alpine AS api
RUN apk add --no-cache python3 py3-pip && python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
WORKDIR /app
COPY web/backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY web/backend/*.py .
COPY --from=runtime-files / /
RUN adduser -D -u 10001 workbench && mkdir /data && chown workbench /data
USER workbench
CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
