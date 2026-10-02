"""Prepare the pinned upstream data plane in a new, isolated test directory.

Usage: python3 web/integration/prepare_data_node.py EXTRACTED_UPSTREAM TEST_DIRECTORY
Upstream must be the archive commit recorded in data-node.lock.json.
"""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

source, target = map(lambda p: Path(p).resolve(), sys.argv[1:])
lock = json.loads(Path(__file__).with_name('data-node.lock.json').read_text())
hash_value = hashlib.sha256()
for file in sorted((source / 'data-node').rglob('*')):
    if file.is_file():
        hash_value.update(file.relative_to(source / 'data-node').as_posix().encode() + b'\0' + file.read_bytes() + b'\0')
if hash_value.hexdigest() != lock['dataNodeTreeSha256']:
    raise SystemExit('Upstream data-node tree does not match the pinned version')
target.mkdir(exist_ok=False, parents=True)
node = target / 'data-node'
shutil.copytree(source / 'data-node', node)
for template in node.rglob('.env.default'):
    shutil.copyfile(template, template.with_name('.env'))
shutil.copyfile(Path(__file__).with_name('data-node.lock.json'), target / 'upstream.json')
auth = node / 'auth'
subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', str(auth / 'cert.key'), '-out', str(auth / 'cert.pem'), '-days', '7', '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], check=True, capture_output=True)
subprocess.run(['keytool', '-importcert', '-storetype', 'PKCS12', '-keystore', str(auth / 'trust-store.p12'), '-storepass', 'insecure', '-alias', 'local-test', '-file', str(auth / 'cert.pem'), '-noprompt'], check=True, capture_output=True)
# Disposable credentials apply only to generated local test data.
password = subprocess.check_output(['openssl', 'passwd', '-apr1', 'prototype'], text=True).strip()
(auth / '.htpasswd').write_text(f'prototype:{password}\n')
(auth / 'cert.key').chmod(0o644)
(target / 'fde-output').mkdir()
shutil.copyfile(Path(__file__).with_name('measure.json'), target / 'measure.json')
# extends preserves upstream image/service definitions; explicit overrides isolate
# ports, resources, mounts and generated-data authentication from other projects.
compose = f'''name: excel2fhir-data-node-74
services:
  fhir-server:
    extends:
      file: {node}/fhir-server/docker-compose.yml
      service: fhir-server
    ports: !override ["127.0.0.1:5185:8080"]
    environment:
      JAVA_TOOL_OPTIONS: -Xmx768m
      DB_BLOCK_CACHE_SIZE: 128
      OPENID_PROVIDER_URL: ""
      LOG_LEVEL: info
    mem_limit: 1280m
    cpus: 1
    restart: "no"
  torch:
    extends:
      file: {node}/torch/docker-compose.yml
      service: torch
    ports: !override ["127.0.0.1:5186:8080"]
    environment:
      JAVA_TOOL_OPTIONS: -Xmx1024m
      TORCH_FHIR_OAUTH_ISSUER_URI: ""
      TORCH_BASE_URL: https://localhost:5188/torch
      TORCH_OUTPUT_FILE_SERVER_URL: https://localhost:5188/torch/fileserver
      TORCH_FHIR_URL: http://fhir-server:8080/fhir
      TORCH_MAXCONCURRENCY: 1
      TORCH_BATCHSIZE: 10
    mem_limit: 1536m
    cpus: 1
    restart: "no"
  torch-nginx:
    extends:
      file: {node}/torch/docker-compose.yml
      service: torch-nginx
    ports: !override ["127.0.0.1:5187:8080"]
    mem_limit: 64m
    restart: "no"
  rev-proxy:
    extends:
      file: {node}/rev-proxy/docker-compose.yml
      service: rev-proxy
    ports: !override ["127.0.0.1:5188:8443"]
    mem_limit: 64m
    restart: "no"
  fhir-data-evaluator:
    extends:
      file: {node}/fhir-data-evaluator/docker-compose.yml
      service: fhir-data-evaluator
    profiles: [evaluation]
    environment:
      JAVA_TOOL_OPTIONS: -Xmx768m
      FHIR_SOURCE_SERVER: http://fhir-server:8080/fhir
      FHIR_REPORT_SERVER: http://fhir-server:8080/fhir
      FHIR_SOURCE_OAUTH_ISSUER_URI: ""
      FHIR_REPORT_OAUTH_ISSUER_URI: ""
    volumes: !override
      - {target}/measure.json:/app/measure.json:ro
      - {target}/fde-output:/app/output
      - {auth}:/app/certs:ro
    mem_limit: 1024m
    cpus: 1
volumes:
  blaze-data:
  triangle-torch-data-store:
'''
(target / 'compose.yml').write_text(compose)
print(target / 'compose.yml')
