"""Verify local TLS, login, portal catalogue and a real CQL feasibility query.

Usage: python3 web/integration/smoke_portal_query.py CERTIFICATE LOGIN_JSON
LOGIN_JSON contains the username/password from portal-init's credentials.txt.
The local Blaze must contain at least one female Patient. The test creates a
portal query and compares its count with an independent FHIR search.
"""
import json
from pathlib import Path
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'https://localhost:5192'
context = ssl.create_default_context(cafile=sys.argv[1])
credentials = json.loads(Path(sys.argv[2]).read_text())


def request(path, data=None, token=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    with urllib.request.urlopen(req, context=context, timeout=60) as response:
        body = response.read()
        return response.status, response.headers, json.loads(body) if body else None


_, _, discovery = request('/auth/realms/dataportal/.well-known/openid-configuration')
assert discovery['issuer'] == BASE + '/auth/realms/dataportal'
_, _, login = request('/auth/realms/dataportal/protocol/openid-connect/token',
                      urllib.parse.urlencode({'grant_type': 'password', 'client_id': 'dataportal', **credentials}).encode(),
                      content_type='application/x-www-form-urlencoded')
token = login['access_token']
_, _, ui_config = request('/assets/config/config.deploy.json')
assert ui_config['backendBaseUrl'] == BASE + '/backend'
assert ui_config['authBaseUrl'] == BASE + '/auth'
_, _, catalogue = request('/backend/api/v6/terminology/entry/search?searchterm=Geschlecht', token=token)
assert catalogue['totalHits'] > 0, catalogue
_, _, health = request('/backend/api/v6/actuator/health')
assert health['status'] == 'UP', health
query_path = '/backend/api/v6/query/feasibility'
try:
    request(query_path, b'{}')
    raise AssertionError('Portal accepted an unauthenticated query')
except urllib.error.HTTPError as error:
    assert error.code in {401, 403}, error.code
with urllib.request.urlopen('http://localhost:5190/fhir/Patient?gender=female&_summary=count', timeout=30) as response:
    expected = json.load(response)['total']
assert expected > 0, 'Load female test Patients before running this probe'
query = {
    'version': 'http://to_be_decided.com/draft-1/schema#',
    'display': 'Local female patient smoke query',
    'inclusionCriteria': [[{
        'termCodes': [{'code': '263495000', 'display': 'Geschlecht', 'system': 'http://snomed.info/sct'}],
        'context': {'code': 'Patient', 'display': 'Patient', 'system': 'fdpg.mii.cds', 'version': '1.0.0'},
        'valueFilter': {'type': 'concept', 'selectedConcepts': [
            {'code': 'female', 'display': 'Female', 'system': 'http://hl7.org/fhir/administrative-gender'}]},
    }]],
}
try:
    status, headers, _ = request(query_path, json.dumps(query).encode(), token)
except urllib.error.HTTPError as error:
    raise AssertionError(f'Query HTTP {error.code}: {error.read().decode()}') from error
assert status == 201, status
location = headers['Location']
assert location.startswith(BASE + query_path + '/'), location
path = location[len(BASE):] + '/summary-result'
for attempt in range(30):
    try:
        status, _, result = request(path, token=token)
        if status == 200 and result is not None and result.get('totalNumberOfPatients') == expected:
            break
    except urllib.error.HTTPError as error:
        if error.code not in {202, 204, 404}:
            raise
    time.sleep(2)
else:
    raise AssertionError(f'Portal query did not return the expected count: {result}')
assert result['totalNumberOfPatients'] == expected, result
print(json.dumps({'tls': 'verified', 'login': 'verified', 'unauthenticatedQuery': 'rejected',
                  'portalPatients': result['totalNumberOfPatients'], 'fhirPatients': expected}))
