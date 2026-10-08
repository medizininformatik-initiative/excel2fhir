"""Exercise the isolated Compose prototype, including a worker container crash.

Run after `docker compose -f web/compose.yml up -d --build`.
Only the worker in the excel2fhir-web-prototype project is stopped/restarted.
"""
import io
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import zipfile

BASE = 'http://127.0.0.1:5184/api'
COMPOSE = ['docker', 'compose', '-f', str(Path(__file__).resolve().parents[2] / 'web/compose.yml')]


def request(path, data=None):
    payload = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(BASE + path, data=payload, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read()


def create():
    return json.loads(request('/jobs', {'source': 'starter'}))['id']


def wait(job_id, states, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        job = next(j for j in json.loads(request('/jobs')) if j['id'] == job_id)
        if job['state'] in states:
            return job
        if job['state'] in {'failed', 'succeeded', 'cancelled', 'interrupted'}:
            raise AssertionError(job)
        time.sleep(0.1)
    raise TimeoutError(job_id)


# A fresh HTTP connection on every poll models a browser reload/disconnect.
job = create()
snapshot = request(f'/jobs/{job}/snapshot')
assert wait(job, {'succeeded'})['exit_code'] == 0
assert snapshot == request(f'/jobs/{job}/snapshot')
archive = zipfile.ZipFile(io.BytesIO(request(f'/jobs/{job}/download')))
bundles = [name for name in archive.namelist() if '/fhir/' in name and name.endswith('.json')]
assert bundles and all(json.loads(archive.read(name))['resourceType'] == 'Bundle' for name in bundles)
assert request(f'/jobs/{job}/logs')
print(f'PASS conversion, reconnect, immutable snapshot, logs, FHIR download: {job}', flush=True)

subprocess.run(COMPOSE + ['up', '-d', '--no-deps', '--force-recreate', 'api'], check=True)
deadline = time.monotonic() + 30
while True:
    try:
        assert snapshot == request(f'/jobs/{job}/snapshot')
        break
    except OSError:
        if time.monotonic() >= deadline:
            raise
        time.sleep(0.5)
print('PASS API recreation and automatic web proxy reconnect', flush=True)

subprocess.run(COMPOSE + ['stop', 'worker'], check=True)
try:
    queued = create()
    request(f'/jobs/{queued}/cancel', {})
    wait(queued, {'cancelled'})
finally:
    subprocess.run(COMPOSE + ['start', 'worker'], check=True)
print(f'PASS queued cancellation: {queued}', flush=True)

running = create()
wait(running, {'running'})
request(f'/jobs/{running}/cancel', {})
wait(running, {'cancelled'})
print(f'PASS running cancellation: {running}', flush=True)

crashed = create()
wait(crashed, {'running'})
subprocess.run(COMPOSE + ['kill', '-s', 'SIGKILL', 'worker'], check=True)
subprocess.run(COMPOSE + ['up', '-d', 'worker'], check=True)
wait(crashed, {'interrupted'})
assert snapshot == request(f'/jobs/{job}/snapshot')
assert request(f'/jobs/{job}/download')
print(f'PASS crash recovery and previous result persistence: {crashed}', flush=True)
