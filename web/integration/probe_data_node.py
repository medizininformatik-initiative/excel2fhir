"""Import one generated KDS variant and attempt a TORCH extraction.

Usage: python3 web/integration/probe_data_node.py TEST_DIRECTORY FHIR_VARIANT_DIRECTORY
Only the isolated local smoke environment is addressed.
"""
import base64
import json
from pathlib import Path
import ssl
import sys
import time
import urllib.error
import urllib.request

root, variant = map(Path, sys.argv[1:])
context = ssl.create_default_context(cafile=str(root / 'data-node/auth/cert.pem'))


def call(url, data=None, auth=False):
    headers = {'Content-Type': 'application/fhir+json'}
    if auth:
        headers['Authorization'] = 'Basic ' + base64.b64encode(b'prototype:prototype').decode()
    req = urllib.request.Request(url, data=json.dumps(data).encode() if data is not None else None, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60, context=context) as response:
            return response.status, dict(response.headers), response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode()


patients = []
for file in sorted(variant.glob('*.json')):
    bundle = json.loads(file.read_text())
    if bundle.get('resourceType') != 'Bundle':
        continue
    entries = bundle.get('entry', [])
    # Explicit PUT transaction preserves the converter's resource IDs/references.
    transaction = {'resourceType': 'Bundle', 'type': 'transaction', 'entry': []}
    for entry in entries:
        resource = entry['resource']
        if resource['resourceType'] == 'Patient':
            patients.append(resource['id'])
        item = {'resource': resource, 'request': {'method': 'PUT', 'url': resource['resourceType'] + '/' + resource['id']}}
        if 'fullUrl' in entry:
            item['fullUrl'] = entry['fullUrl']
        transaction['entry'].append(item)
    result = call('http://127.0.0.1:5185/fhir', transaction)
    (root / f'import-{file.stem}.json').write_text(result[2])
    print(f'Import {file.name}: HTTP {result[0]}', flush=True)
    if result[0] != 200:
        raise SystemExit('Import failed; response preserved')
if not patients:
    raise SystemExit('No patients found in the selected variant')
count_status, _, count_body = call('http://127.0.0.1:5185/fhir/Patient?_summary=count')
(root / 'patient-count.json').write_text(count_body)
assert count_status == 200 and json.loads(count_body)['total'] >= len(set(patients))
print(f'Patient count verified: {json.loads(count_body)["total"]}', flush=True)

crtdl = (root / 'data-node/torch/queries/example-crtdl.json').read_bytes()
parameters = {'resourceType': 'Parameters', 'parameter': [{'name': 'crtdl', 'valueBase64Binary': base64.b64encode(crtdl).decode()}] + [{'name': 'patient', 'valueString': patient} for patient in patients]}
url = 'https://localhost:5188/torch/fhir/$extract-data'
unauthorized = call(url, parameters)
assert unauthorized[0] == 401, unauthorized
status, headers, body = call(url, parameters, auth=True)
(root / 'torch-response.json').write_text(json.dumps({'status': status, 'headers': headers, 'body': body}, indent=2))
print(f'TORCH submission with verified certificate and Basic auth: HTTP {status}', flush=True)
location = next((v for k, v in headers.items() if k.lower() == 'content-location'), None)
if location:
    # Never send test credentials to an unexpected upstream-provided host.
    if not location.startswith('https://localhost:5188/torch/'):
        raise SystemExit(f'Unexpected status URL: {location}')
    for _ in range(60):
        status, headers, body = call(location, auth=True)
        if status != 202:
            break
        time.sleep(1)
    (root / 'torch-result.json').write_text(body)
    print(f'TORCH result: HTTP {status}', flush=True)
