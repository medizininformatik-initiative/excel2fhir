"""Run in the workbench worker image; all input and job state stays temporary."""
import io
import json
from pathlib import Path
import tempfile
from uuid import uuid4
import zipfile

import inputs
import store
import synthea_runtime
import worker

with tempfile.TemporaryDirectory(prefix='synthea-workbench-') as directory:
    store.ROOT = Path(directory)
    stream = io.BytesIO()
    fixtures = synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea'
    with zipfile.ZipFile(stream, 'w') as archive:
        for name in ['female.json', 'male.json']:
            archive.writestr(name, (fixtures / name).read_bytes())
    data = stream.getvalue()
    with inputs.incoming() as incoming:
        (incoming / 'input.zip').write_bytes(data)
        item = inputs.publish(incoming, 'Synthea.zip')
    assert item['inspection']['patients'] == 2
    job = store.create(item['id'], 'default', 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n')
    assert store.claim() == job
    worker.execute(job)
    folder = store.ROOT / 'jobs' / job
    assert store.get(job)['state'] == 'succeeded', (folder / 'converter.log').read_text()[-6000:]
    assert (folder / 'input.zip').read_bytes() == data
    report = json.loads(next((folder / 'output').glob('*/details/reports/summary.json')).read_text())
    assert len(report['results']) == 2 and not report['failures'], report
    assert list((folder / 'output').glob('*/details/cases/*/Fall.loss.json'))
    assert len(list((folder / 'output').glob('*/excel/*.xlsx'))) == 2
    assert store.get(job)['download_available']
    manifest = json.loads((folder / 'datasets.json').read_text())
    assert len(manifest['datasets']) == 1
    assert manifest['datasets'][0]['inspection']['patients'] == 2
    repeated = store.repeat(job, str(uuid4()))
    assert store.claim() == repeated
    worker.execute(repeated)
    repeat_folder = store.ROOT / 'jobs' / repeated
    assert store.get(repeated)['state'] == 'succeeded', (repeat_folder / 'converter.log').read_text()[-6000:]
    assert (repeat_folder / 'input.zip').read_bytes() == data
    print('PASS: Synthea ZIP inspection, two real LibreOffice/Java imports, reports, immutable snapshots and repeat')
