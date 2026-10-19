"""Real conversion with temporary fixtures: global collisions and actual ZIP counts."""
import gzip
import json
from pathlib import Path
import shutil
import tempfile
from unittest.mock import patch
import zipfile

import bulk_generation as bulk
import generation
import synthea_runtime

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    fixture_root = synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea'
    real_process = bulk.checked_process
    calls = []
    def fixed_source(command, log, update=None):
        if 'synthea.jar' in ' '.join(command):
            destination = log.parent / 'synthea/fhir'
            destination.mkdir(parents=True)
            seed = command[command.index('-s') + 1]
            name = 'female.json' if seed == '100' else 'male.json'
            shutil.copyfile(fixture_root / name, destination / name)
            calls.append(seed)
        else:
            real_process(command, log, update)

    # Tiny subruns keep this regression fast while using the real checkpoint/package path.
    runs = [{'number': 1, 'start': 0, 'total': 1}, {'number': 2, 'start': 1, 'total': 1}]
    settings = generation.Settings(executionMode='bulk', population=2, patientSeed='100',
                                   syntheticConsent=False).model_dump(mode='json')
    configs = {
        'collision': ('IDENTIFIER_RULE_1_ID=aef7e40e-9e7f-437b-9813-e8ffc86b9607\n'
                      'IDENTIFIER_RULE_1_ENABLED=true\nIDENTIFIER_RULE_1_RESOURCES=Patient\n'
                      'IDENTIFIER_RULE_1_SYSTEM=urn:review:patient\nIDENTIFIER_RULE_1_PATTERN=constant\n'),
        'external-patients': 'PATIENT_MODE=reference-only\n',
    }
    with patch.object(bulk, 'OUTPUT_ROOT', root / 'output'), patch.object(bulk, 'CHUNK_SIZE', 1), \
         patch.object(bulk.bulk_plan, 'plan', return_value=runs), \
         patch.object(bulk, 'checked_process', side_effect=fixed_source):
        for name, configuration in configs.items():
            folder = root / name
            folder.mkdir()
            (folder / 'snapshot.json').write_text(json.dumps({'generation': settings,
                'bulkLayoutVersion': 3, 'bulkOutputName': name}))
            (folder / 'default.config').write_text('CONFIGURATION_VERSION=1\n' + configuration)
            output = root / 'output' / name
            if name == 'collision':
                saved = None
                for attempt in range(2):
                    try:
                        bulk.run(folder)
                        raise AssertionError('Expected collision before publishing the second chunk')
                    except ValueError as error:
                        assert 'Identifier collision' in str(error), error
                        assert 'urn:review:patient | constant' in str(error), error
                    archives = list(output.rglob('*.zip'))
                    assert len(archives) == 1
                    first = archives[0]
                    current = first.read_bytes(), first.stat().st_mtime_ns
                    if saved is not None:
                        assert current == saved, 'Resume changed the committed ZIP'
                    saved = current
                    assert not (output / 'run-00002/part-00001').exists()
                assert calls == ['100', '101', '101'], calls
                print('PASS: cross-subrun collision rejected on initial run and resume; first ZIP unchanged')
            else:
                bulk.run(folder)
                items = json.loads((folder / 'datasets.json').read_text())['datasets']
                assert len(items) == 2
                for item in items:
                    assert item['inspection']['patients'] == 0, item
                    assert item['path'].endswith('-0Pat.zip'), item
                    with zipfile.ZipFile(output / item['path']) as archive:
                        bundles = [json.loads(gzip.decompress(archive.read(n)))
                                   for n in archive.namelist() if n.endswith('.json.gz')]
                    assert bundles and any(b['entry'] for b in bundles)
                    assert not any(e['resource']['resourceType'] == 'Patient' for b in bundles for e in b['entry'])
                print('PASS: external-patient output retained; ZIP names and inspection report zero contained Patients')
