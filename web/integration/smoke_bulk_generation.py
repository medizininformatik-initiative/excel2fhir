"""Small deterministic equivalence and chunk failure checks in the Docker worker."""
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from unittest.mock import patch

import bulk_generation as bulk
import generation
import store
import synthea_runtime

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    fixtures = synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea'
    sources = root / 'sources'
    sources.mkdir()
    for name in ('female.json', 'male.json'):
        shutil.copyfile(fixtures / name, sources / name)
    configuration = 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\nCHECKS_FHIR_VALIDATION=false\n'
    job = root / 'job'
    job.mkdir()
    settings = generation.Settings(executionMode='bulk', population=2, syntheticConsent=False).model_dump(mode='json')
    (job / 'snapshot.json').write_text(json.dumps({'generation': settings, 'bulkOutputName': '2026-10-16_Test-job'}))
    (job / 'default.config').write_text(configuration)
    real_process = bulk.checked_process
    calls = []
    def fixed_source(command, log):
        if 'synthea.jar' in ' '.join(command):
            source = sources / ('female.json' if len(calls) == 0 else 'male.json')
            target = log.parent / 'synthea/fhir'
            target.mkdir(parents=True)
            shutil.copyfile(source, target / source.name)
            calls.append(command)
        else:
            real_process(command, log)
    with patch.object(bulk, 'OUTPUT_ROOT', root / 'results'), patch.object(bulk, 'CHUNK_SIZE', 1), patch.object(bulk, 'checked_process', side_effect=fixed_source):
        bulk.run(job)
    result = root / 'results/2026-10-16_Test-job'
    assert len(list(result.glob('part-*'))) == 2
    assert len(list(result.rglob('*.json.gz'))) == 2
    assert all(p.name.endswith('.json.gz') for p in result.rglob('*') if p.is_file())
    assert not (job / 'bulk-work').exists()
    assert json.loads((job / 'bulk-progress.json').read_text())['completed'] == 2
    normal = root / 'normal'
    import os
    subprocess.run([sys.executable, str(synthea_runtime.ROOT / 'scripts/run_synthea_cases.py'),
        '-i', str(sources), '-o', str(normal), '--converter-options', str(job / 'default.config')],
        env={**os.environ, 'SYNTHIFHIR_TABLE_FORMAT': 'csv', 'SYNTHIFHIR_DIAGNOSTICS': 'true'}, check=True)
    def content(paths):
        return sorted(hashlib.sha256(gzip.decompress(p.read_bytes()) if p.name.endswith('.gz') else p.read_bytes()).hexdigest() for p in paths)
    assert content(result.rglob('*.json.gz')) == content(normal.rglob('*.ndjson')), (content(result.rglob('*.json.gz')), content(normal.rglob('*.ndjson')))
    summary = json.loads(next(normal.rglob('summary.json')).read_text())
    assert all(v['comparisonStatus'] == 'passed' for r in summary['results'] for v in r['variants'])
    # A second chunk failing must not publish its partial files or erase the first.
    failed = root / 'failed-job'
    failed.mkdir()
    (failed / 'snapshot.json').write_text(json.dumps({'generation': settings, 'bulkOutputName': '2026-10-16_Failed-job'}))
    shutil.copyfile(job / 'default.config', failed / 'default.config')
    calls.clear()
    def fail_second(command, log):
        if len(calls) == 2 and any(c.endswith('.SyntheaBulkConverter') for c in command):
            raise RuntimeError('deliberate converter failure')
        fixed_source(command, log)
    with patch.object(bulk, 'OUTPUT_ROOT', root / 'results'), patch.object(bulk, 'CHUNK_SIZE', 1), patch.object(bulk, 'checked_process', side_effect=fail_second):
        try:
            bulk.run(failed)
            raise AssertionError('Expected failure')
        except RuntimeError as error:
            assert 'deliberate' in str(error), error
    assert (root / 'results/2026-10-16_Failed-job/part-00001').is_dir()
    assert not (root / 'results/2026-10-16_Failed-job/part-00002').exists()
    assert (failed / 'bulk-work').is_dir()
    print('PASS: two chunks, gzip-only output decompresses to byte-identical NDJSON, source comparisons, cleanup and failed-chunk publication')
