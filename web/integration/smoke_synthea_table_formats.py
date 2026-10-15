"""Compare both Synthea table paths in the Docker worker with isolated storage.

Pass an optional directory containing patient JSON bundles to exercise a larger set.
"""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import time
import zipfile

import inputs
import store
import synthea_runtime
import worker
import run_cleanup


def hashes(paths, base=None):
    values = {}
    for path in paths:
        if path.is_file():
            name = str(path.relative_to(base)).replace('.xlsx/', '/').replace('.csv/', '/') if base else path.name
            assert name not in values, ('Duplicate comparison key', name)
            values[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return values


with tempfile.TemporaryDirectory(prefix='table-formats-') as directory:
    store.ROOT = Path(directory)
    fixtures = Path(sys.argv[1]) if len(sys.argv) > 1 else synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea'
    sources = sorted(fixtures.glob('*.json')) if len(sys.argv) > 1 else [fixtures / name for name in ('female.json', 'male.json')]
    with inputs.incoming() as incoming:
        with zipfile.ZipFile(incoming / 'input.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            for source in sources:
                archive.write(source, source.name)
        item = inputs.publish(incoming, 'Synthea.zip')
    outputs = {}
    for table_format, diagnostics in (('excel', False), ('csv', False), ('excel', True), ('csv', True)):
        started = time.monotonic()
        job = store.create(item['id'], 'default',
            'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON,NDJSON\n', table_format=table_format, diagnostics=diagnostics)
        assert store.claim() == job
        worker.execute(job)
        folder = store.ROOT / 'jobs' / job
        assert store.get(job)['state'] == 'succeeded', (folder / 'converter.log').read_text()[-8000:]
        run = next((folder / 'output').iterdir())
        summary = json.loads((run / 'details/reports/summary.json').read_text())
        assert len(summary['results']) == len(sources) and not summary['failures'], summary
        assert store.editor_input(job)['tableFormat'] == table_format
        assert store.editor_input(job)['diagnostics'] is diagnostics
        assert bool(list(run.rglob('Fall.loss.json'))) is diagnostics
        for result in summary['results']:
            for variant in result['variants']:
                assert variant['comparisonStatus'] == ('passed' if diagnostics else 'not-requested'), variant
        fhir = hashes((run / 'fhir').rglob('*.*'), run / 'fhir')
        if table_format == 'excel':
            assert len(list((run / 'excel').glob('*.xlsx'))) == len(sources)
            csv = hashes(run.rglob('*.csv'))
        else:
            assert not list(run.rglob('*.xlsx')) and not any(path.is_file() for path in run.rglob('*.csv'))
            with zipfile.ZipFile(run / 'csv.zip') as archive:
                assert archive.testzip() is None
                assert all('/' not in name for name in archive.namelist())
                csv = {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()}
            # The download must pass the normal CSV upload inspection as well.
            with inputs.incoming() as incoming:
                import shutil
                shutil.copyfile(run / 'csv.zip', incoming / 'input.zip')
                uploaded = inputs.publish(incoming, 'tables.zip')
                assert uploaded['kind'] == 'csv'
            saved = (run / 'csv.zip').read_bytes()
            run_cleanup.cleanup(job)
            assert (run / 'csv.zip').read_bytes() == saved
        outputs[(table_format, diagnostics)] = {'csv': csv, 'fhir': fhir}
        print(table_format, 'diagnostics=' + str(diagnostics), len(sources), 'cases', round(time.monotonic() - started, 2), 'seconds', flush=True)
    baseline = outputs[('csv', False)]
    for mode, output in outputs.items():
        for kind in ('csv', 'fhir'):
            assert baseline[kind] == output[kind], (mode, kind, 'different output')
    print('PASS: CSV and FHIR identical across both table formats and diagnostic modes', flush=True)
