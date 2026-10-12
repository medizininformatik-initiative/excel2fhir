"""Audit saved Synthea workbooks through the real worker in isolated temporary state.

Run with: docker compose -f web/compose.yml exec -T worker python < web/integration/smoke_replay_ids.py
No FHIR server is written to. Validation is disabled; this checks generated identities.
Replay and counter assertions fail on regressions, including cross-workbook collisions.
"""
import json
from pathlib import Path
import re
import tempfile
from unittest.mock import patch
import zipfile

import inputs
import run_cleanup
import run_replay
import store
import synthea_runtime
import worker


def resources(value):
    if value.get('resourceType') == 'Bundle':
        for entry in value.get('entry', []):
            yield from resources(entry['resource'])
    else:
        yield value


def references(value):
    if isinstance(value, dict):
        if isinstance(value.get('reference'), str):
            yield value['reference']
        for child in value.values():
            yield from references(child)
    elif isinstance(value, list):
        for child in value:
            yield from references(child)


def read_output(job):
    root = store.ROOT / 'jobs' / job / 'output'
    formats = {}
    for extension in ('json', 'ndjson'):
        found = {}
        files = [p for p in root.rglob('*.' + extension) if 'fhir' in p.parts]
        assert files, (job, extension)
        for path in files:
            values = [json.loads(line) for line in path.read_text().splitlines()] if extension == 'ndjson' else [json.loads(path.read_text())]
            for value in values:
                for resource in resources(value):
                    key = resource['resourceType'] + '/' + resource['id']
                    assert key not in found or found[key] == resource, ('Conflicting duplicate resource', key)
                    found[key] = resource
        formats[extension] = found
    assert formats['json'] == formats['ndjson'], 'JSON and NDJSON differ'
    found = formats['json']
    for key, resource in found.items():
        for ref in references(resource):
            if ref.startswith('#'):
                assert ref[1:] in {r['id'] for r in resource.get('contained', [])}, (key, ref)
            else:
                assert ref in found, ('Unresolved reference', key, ref)
    return found


def execute(job, replay=False):
    assert store.claim() == job
    original = worker.subprocess.Popen
    with patch.object(worker.subprocess, 'Popen', wraps=original) as launched:
        worker.execute(job)
    root = store.ROOT / 'jobs' / job
    assert store.get(job)['state'] == 'succeeded', (root / 'converter.log').read_text()[-6000:]
    if replay:
        commands = [call.args[0] for call in launched.call_args_list]
        assert commands and all(command[0] == 'java' for command in commands), commands
        assert sum('-jar' in command for command in commands) == 1, commands
    return read_output(job)


BASE = ('CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON,NDJSON\n'
        'REFERENCE_PROCEDURE_ENCOUNTER=facility\n'
        'REFERENCE_OBSERVATION_LABORATORY_ENCOUNTER=facility\n'
        'REFERENCE_OBSERVATION_VITAL_SIGNS_ENCOUNTER=facility\n')
CASE_RULE = ('IDENTIFIER_RULE_2_ID=b152e771-3d5a-4cb1-9866-35fa6d91fd83\n'
             'IDENTIFIER_RULE_2_ENABLED=true\nIDENTIFIER_RULE_2_SCOPE=case\n'
             'IDENTIFIER_RULE_2_RESOURCES=Encounter,Observation,Procedure\n'
             'IDENTIFIER_RULE_2_SYSTEM=urn:replay-case\n'
             'IDENTIFIER_RULE_2_PATTERN={patientId}-{caseId}\n')
with tempfile.TemporaryDirectory(prefix='replay-id-audit-') as directory:
    store.ROOT = Path(directory)
    fixture = synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea'
    with inputs.incoming() as incoming:
        with zipfile.ZipFile(incoming / 'input.zip', 'w') as archive:
            for name in ('female.json', 'male.json'):
                archive.write(fixture / name, name)
        item = inputs.publish(incoming, 'patients.zip')
    source = store.create(item['id'], 'default', BASE)
    baseline = execute(source)
    patients = {r['id'] for r in baseline.values() if r['resourceType'] == 'Patient'}
    assert len(patients) == 2, patients
    loaded = run_replay.editor(source)
    assert loaded['workbookCount'] == 2
    jobs = []
    for prefix, suffix in [('DIZ-A-', ''), ('', '-DIZ-B')]:
        config = BASE + f'PID_PREFIX={prefix}\nPID_SUFFIX={suffix}\nPID_LAST_NUMBER_INCREASE_LOOP_COUNT=2\nSTART_ID_PROCEDURE=700\n'
        config += ('IDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\n'
                   'IDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_RESOURCES=Patient\n'
                   'IDENTIFIER_RULE_1_SYSTEM=urn:replay-audit\n'
                   'IDENTIFIER_RULE_1_PATTERN={patientId}-{count}\nIDENTIFIER_RULE_1_COUNT_START=500\n')
        config += CASE_RULE
        jobs.append((store.create(loaded['source'], 'default', config), prefix, suffix))
    # The copies must be sufficient even after the source and input-library entry disappear.
    original_books = json.loads((store.ROOT / 'jobs' / jobs[0][0] / 'workbooks.json').read_text())['workbooks']
    store.delete(source)
    inputs.delete(loaded['source'].removeprefix('upload:'))
    seen = {}
    for job, prefix, suffix in jobs:
        output = execute(job, replay=True)
        expected = {prefix + patient + suffix + (f'-rep-{n}' if n else '') for patient in patients for n in range(3)}
        actual = {r['id'] for r in output.values() if r['resourceType'] == 'Patient'}
        assert actual == expected, (actual, expected)
        overlap = set(seen).intersection(output)
        conflicts = [key for key in overlap if seen[key] != output[key]]
        print('DIZ overlap:', json.dumps({'identicalSharedResources': sorted(overlap - set(conflicts)), 'conflicting': conflicts}), flush=True)
        assert not conflicts, ('Conflicting DIZ resources', conflicts)
        seen.update(output)
        procedure_numbers = [int(re.search(r'(\d+)$', resource['id'])[1])
                             for resource in output.values() if resource['resourceType'] == 'Procedure']
        assert procedure_numbers and min(procedure_numbers) == 700, procedure_numbers
        identifiers = []
        for resource in output.values():
            if resource['resourceType'] == 'Patient':
                ids = [i['value'] for i in resource['identifier'] if i['system'] == 'urn:replay-audit']
                assert len(ids) == 1 and ids[0].startswith(resource['id'] + '-'), ids
                identifiers.extend(ids)
        assert len(set(identifiers)) == 6
        counts = [int(i.rsplit('-', 1)[1]) for i in identifiers]
        assert sorted(counts) == list(range(500, 506)), counts
        # Every clinical subject remains within its own repetition.
        for resource in output.values():
            subject = resource.get('subject', {}).get('reference')
            if subject and subject.startswith('Patient/'):
                assert subject[8:] in expected
                for ref in references(resource):
                    target = output.get(ref, {})
                    target_subject = target.get('subject', {}).get('reference')
                    if target_subject and target_subject.startswith('Patient/'):
                        assert subject == target_subject, ('Cross-patient reference', resource['id'], ref)
        def case_value(resource):
            return [i['value'] for i in resource.get('identifier', []) if i.get('system') == 'urn:replay-case']
        checked = 0
        for resource in output.values():
            if resource['resourceType'] not in ('Encounter', 'Observation', 'Procedure'):
                assert not case_value(resource)
                continue
            ref = resource.get('partOf' if resource['resourceType'] == 'Encounter' else 'encounter', {}).get('reference')
            if ref and ref in output:
                assert case_value(resource) and case_value(resource) == case_value(output[ref]), (resource['id'], ref)
                checked += 1
        assert checked > 0
        print(f'PASS case identifiers: {checked} hierarchy/clinical associations', flush=True)
        root = store.ROOT / 'jobs' / job
        for book in original_books:
            assert store.digest(root / 'workbooks' / book['name']) == book['sha256']
        run_cleanup.cleanup(job)
        assert read_output(job) == output, 'Cleanup changed clinical output'
        assert run_replay.editor(job)['workbookCount'] == 2
        print(f'PASS {prefix or suffix}: {len(actual)} patients, {len(output)} resources, references, JSON/NDJSON, starts, cleanup', flush=True)
    # Probe unqualified counters separately from patient-qualified identifiers.
    available = run_replay.editor(jobs[0][0])['source']
    probe_config = BASE + ('IDENTIFIER_RULE_1_ID=a152e771-3d5a-4cb1-9866-35fa6d91fd83\n'
        'IDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_RESOURCES=Patient\n'
        'IDENTIFIER_RULE_1_SYSTEM=urn:replay-counter-probe\n'
        'IDENTIFIER_RULE_1_PATTERN={count}\nIDENTIFIER_RULE_1_COUNT_START=500\n')
    probe = execute(store.create(available, 'default', probe_config), replay=True)
    owners = {}
    for key, resource in probe.items():
        for identifier in resource.get('identifier', []):
            if identifier.get('system') == 'urn:replay-counter-probe':
                owners.setdefault(identifier['value'], []).append(key)
    collisions = {value: keys for value, keys in owners.items() if len(keys) > 1}
    assert not collisions, collisions
    assert set(owners) == {'500', '501'}, owners
    print('PASS: unqualified counters remain unique across workbooks: ' + json.dumps(owners), flush=True)
    print('PASS: two-workbook replay without Synthea, independent copies, DIZ namespaces and repetitions')
