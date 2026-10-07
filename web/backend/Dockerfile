FROM maven:3.8.5-openjdk-17 AS synthea-build
COPY scripts/synthea-version.txt /synthea-version.txt
WORKDIR /synthea
RUN git init && git remote add origin https://github.com/astruebi/synthea.git \
    && git fetch --depth 1 origin "$(cat /synthea-version.txt)" \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "$(cat /synthea-version.txt)" \
    && ./gradlew --no-daemon shadowJar

FROM maven:3.8.5-openjdk-17 AS converter
WORKDIR /build
COPY pom.xml .
COPY src src
COPY web/catalog/options/contract.json web/catalog/options/contract.json
COPY web/catalog/dar/generated/catalog.json web/catalog/dar/generated/catalog.json
COPY web/backend/ExportDefaults.java .
RUN --mount=type=cache,id=excel2fhir-maven,target=/root/.m2,sharing=locked mvn -B -DskipTests package
RUN java -cp target/excel2fhir.jar ExportDefaults.java /defaults

FROM debian:bookworm-slim AS blazectl
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && case "$TARGETARCH" in \
      amd64) checksum=716fd5cba59ccceeb34b90be66465de05da28de92ccd55d793ab1e8c68d6ac54 ;; \
      arm64) checksum=840b6364414e9f79a439a7512df4c55f747e6a87d446d2ebb77cfb7d238fcfc5 ;; \
      *) exit 1 ;; esac \
    && curl -fsSL "https://github.com/samply/blazectl/releases/download/v1.5.1/blazectl-1.5.1-linux-${TARGETARCH}.tar.gz" -o /tmp/blazectl.tar.gz \
    && echo "$checksum  /tmp/blazectl.tar.gz" | sha256sum -c - \
    && mkdir /blazectl && tar -xzf /tmp/blazectl.tar.gz -C /blazectl

FROM scratch AS runtime-files
COPY --from=synthea-build /synthea/build/libs/synthea-with-dependencies.jar /synthea/target/synthea.jar
COPY --from=synthea-build /synthea-version.txt /synthea/target/synthea-revision.txt
COPY --from=synthea-build /synthea/LICENSE /synthea/NOTICE /synthea/third-party/synthea/
COPY scripts /synthea/scripts
COPY src/main/resources/workbook-absent-reasons.json /synthea/src/main/resources/workbook-absent-reasons.json
COPY src/main/resources/ucum /synthea/src/main/resources/ucum
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
COPY --from=blazectl /blazectl/blazectl /usr/local/bin/blazectl
COPY third-party/blazectl/LICENSE /usr/share/doc/blazectl/LICENSE
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
