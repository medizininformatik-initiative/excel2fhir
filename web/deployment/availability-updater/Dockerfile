FROM ghcr.io/medizininformatik-initiative/dataportal-availability-updater:0.4.1

USER root
COPY patch.py test_layout.py /tmp/ontology-layout-fix/
RUN python /tmp/ontology-layout-fix/test_layout.py \
    && python /tmp/ontology-layout-fix/patch.py \
    && rm -rf /tmp/ontology-layout-fix
USER 10001
