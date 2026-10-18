"""Run in the worker image; real Synthea/Excel conversion in isolated temporary state."""
import json
from pathlib import Path
import tempfile

import inputs
import run_cleanup
import store
import synthea_runtime
import worker

with tempfile.TemporaryDirectory(prefix='run-outcomes-') as directory:
    store.ROOT = Path(directory)
    fixture = synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea/female.json'
    with inputs.incoming() as incoming:
        (incoming / 'input.json').write_bytes(fixture.read_bytes())
        item = inputs.publish(incoming, 'female.json')
    job = store.create(item['id'], 'default',
        'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON,NDJSON\nDAR_PATIENT_NAME_FAMILY=made-up\n')
    assert store.claim() == job
    worker.execute(job)
    root = store.ROOT / 'jobs' / job
    result = store.get(job)
    assert result['state'] == 'succeeded', (root / 'converter.log').read_text()[-6000:]
    outcomes = result['outcomes']
    assert outcomes['generation'] == 'succeeded', outcomes
    assert outcomes['validation']['status'] == 'not-requested', outcomes
    assert outcomes['transfer']['checked'] == 1, outcomes
    assert outcomes['transfer']['unavailable'] == 0, outcomes
    assert outcomes['transfer']['differences'] == 0, outcomes
    files = list((root / 'output').glob('*/fhir/*.json'))
    assert files and 'made-up' in files[0].read_text()
    run_cleanup.cleanup(job)
    assert store.get(job)['outcomes'] == outcomes
    print('PASS: real Synthea/Excel conversion, configured DAR comparison, independent outcomes and cleanup')
    print(json.dumps({'generation': outcomes['generation'], 'validation': outcomes['validation'], 'transferNotes': outcomes['transfer']['importNotes'], 'comparisonDifferences': outcomes['transfer']['differences']}))
