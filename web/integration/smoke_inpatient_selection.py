"""Run inside the worker image; isolated outputs, no queue or FHIR server writes."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from datetime import datetime
import generation
import worker


def statuses(text):
    return [json.loads(line.removeprefix('WORKBENCH_SELECTION ')) for line in text.splitlines()
            if line.startswith('WORKBENCH_SELECTION ')]


with tempfile.TemporaryDirectory(prefix='inpatient-smoke-') as temporary:
    root = Path(temporary)
    impossible = dict(inpatientOnly=True, inpatientMinDays=36500, maxAttempts=2,
                      minAge=0, maxAge=0, population=3)
    result = subprocess.run(generation.native_command(impossible, root / 'impossible'),
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
    progress = statuses(result.stdout)
    assert result.returncode != 0, result.stdout[-2000:]
    assert progress[-1]['exhausted'] and progress[-1]['attempts'] == 2, progress
    print('Exhaustion: two rejected candidates, nonzero exit, no further patient slots', flush=True)

    settings = dict(inpatientOnly=True, inpatientMinDays=2, maxAttempts=30,
                    minAge=60, maxAge=80, yearsOfHistory=10, population=1)
    result = subprocess.run(generation.native_command(settings, root / 'success'),
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
    assert result.returncode == 0, result.stdout[-2000:]
    progress = statuses(result.stdout)
    assert progress[-1]['matched'] == 1 and not progress[-1]['exhausted'], progress
    patients = 0
    for path in (root / 'success/fhir').glob('*.json'):
        resources = [e['resource'] for e in json.loads(path.read_text()).get('entry', [])]
        if not any(r['resourceType'] == 'Patient' for r in resources):
            continue
        patients += 1
        stays = [r for r in resources if r['resourceType'] == 'Encounter' and r.get('class', {}).get('code') == 'IMP']
        assert any('end' in r.get('period', {}) and
                   (datetime.fromisoformat(r['period']['end'].replace('Z', '+00:00')) -
                    datetime.fromisoformat(r['period']['start'].replace('Z', '+00:00'))).total_seconds() >= 172800
                   for r in stays)
    assert patients == 1, patients
    print('Success: one exported patient has a qualifying stay; attempts=' + str(progress[-1]['attempts']), flush=True)

    impossible['maxAttempts'] = 10000
    process = subprocess.Popen(generation.native_command(impossible, root / 'cancel'),
                               text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        for line in process.stdout:
            if line.startswith('WORKBENCH_SELECTION '):
                worker.terminate(process)
                break
        process.wait(timeout=10)
        assert process.returncode != 0
        print('Cancellation: active candidate search terminates through worker process-group shutdown', flush=True)
    finally:
        if process.poll() is None:
            worker.terminate(process)
