"""Upload two archived patients into an isolated FHIR server; no Synthea generation."""
import json
import os
import urllib.request
from unittest.mock import patch
from uuid import uuid4

import fhir_uploads as uploads
import worker
from test_bulk_uploads import StreamingTests

url = os.environ['TEST_FHIR_URL']
fixture = StreamingTests()
fixture.setUp()
try:
    ids = ['zip-smoke-' + uuid4().hex for _ in range(2)]
    dataset = fixture.publish([{'resourceType': 'Patient', 'id': pid, 'gender': 'female'} for pid in ids])
    target = {'name': 'Isolated Blaze', 'url': url, 'address': url, 'concurrency': 1}
    with patch.dict(uploads.TARGETS, {'blaze': target}):
        assert uploads.target_status('blaze')['available']
        action = uploads.create(str(uuid4()), 'blaze', [dataset])
        assert uploads.claim() == action['id']
        uploads.execute(action['id'], lambda: False, worker.terminate)
        result = uploads.get(action['id'])
        assert result['state'] == 'succeeded', result
        assert result['result']['acceptedBundles'] == 2
        for pid in ids:
            with urllib.request.urlopen(url + '/Patient/' + pid) as response:
                resource = json.load(response)
            assert resource['id'] == pid and resource['gender'] == 'female'
    print('PASS: verified ZIP source -> patient transactions -> Blaze read-back; no extracted copies')
finally:
    fixture.doCleanups()
