"""Readiness probe inside exactly one environment network, without Docker access."""
import json
import time
import urllib.error
import urllib.request

last = ''
for _ in range(120):
    try:
        with urllib.request.urlopen('http://fhir-server:8080/health', timeout=3) as response:
            assert response.status == 200
        try:
            with urllib.request.urlopen('http://torch:8080/actuator/health', timeout=3) as response:
                assert response.status == 200
        except urllib.error.HTTPError as error:
            if error.code not in {401, 403, 404}:
                raise
            # An HTTP response confirms the TORCH application is accepting requests.
        print(json.dumps({'fhir': 'ready', 'torch': 'responding'}))
        break
    except Exception as error:
        last = str(error)
        time.sleep(1)
else:
    raise SystemExit('Services did not become ready: ' + last)
