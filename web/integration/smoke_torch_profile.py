"""Extract explicitly named Patients from the local Compose TORCH service.

Usage: python3 web/integration/smoke_torch_profile.py PATIENT_ID [PATIENT_ID ...]
Requires already loaded synthetic Patients. Uses the pinned upstream CRTDL and
verifies downloaded patient IDs; does not load data or start services.
"""
import base64
import json
from pathlib import Path
import sys
import time
import urllib.request

patients = set(sys.argv[1:])
assert patients, 'Provide the explicitly selected patient IDs'
crtdl = Path(__file__).resolve().parents[1] / 'deployment/upstream/data-node/torch/queries/example-crtdl.json'
parameters = {'resourceType': 'Parameters', 'parameter': [
    {'name': 'crtdl', 'valueBase64Binary': base64.b64encode(crtdl.read_bytes()).decode()},
    *[{'name': 'patient', 'valueString': patient} for patient in sorted(patients)],
]}
request = urllib.request.Request('http://localhost:5193/fhir/$extract-data',
                                 data=json.dumps(parameters).encode(),
                                 headers={'Content-Type': 'application/fhir+json'})
with urllib.request.urlopen(request, timeout=60) as response:
    assert response.status == 202, response.status
    location = response.headers['Content-Location']
assert location.startswith('http://localhost:5193/'), location
for attempt in range(60):
    with urllib.request.urlopen(location, timeout=30) as response:
        if response.status == 200:
            result = json.load(response)
            break
        assert response.status == 202, response.status
    time.sleep(1)
else:
    raise AssertionError('TORCH extraction did not complete')
assert result.get('output') and not result.get('error'), result
extracted = set()
counts = {}
for output in result['output']:
    assert output['url'].startswith('http://localhost:5194/'), output['url']
    with urllib.request.urlopen(output['url'], timeout=30) as response:
        for line in response:
            if not line.strip():
                continue
            resource = json.loads(line)
            resources = [e['resource'] for e in resource['entry']] if resource['resourceType'] == 'Bundle' else [resource]
            for item in resources:
                kind = item['resourceType']
                counts[kind] = counts.get(kind, 0) + 1
                if kind == 'Patient':
                    extracted.add(item['id'])
assert extracted == patients, (extracted, patients)
print(json.dumps({'patients': len(extracted), 'resources': counts}))
