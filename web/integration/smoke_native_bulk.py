"""Generate a small native cohort and verify packaged bytes against Synthea output."""
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
from unittest.mock import patch
import zipfile

import bulk_archives
import bulk_generation as bulk
import generation

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    folder = root / 'job'
    folder.mkdir()
    settings = generation.Settings(executionMode='bulk', outputMode='synthea', population=2,
        minAge=18, maxAge=18, yearsOfHistory=1, patientSeed='20260912').model_dump(mode='json')
    (folder / 'snapshot.json').write_text(json.dumps({'generation': settings,
        'bulkLayoutVersion': 3, 'bulkOutputName': 'native-smoke'}))
    original_hashes = []
    real_process = bulk.checked_process
    def generate(command, log, update=None):
        assert 'synthea.jar' in ' '.join(command), command
        real_process(command, log, update)
        original_hashes.extend(hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in (log.parent / 'synthea/fhir').glob('*.json'))
    with patch.object(bulk, 'OUTPUT_ROOT', root / 'output'), patch.object(bulk, 'checked_process', side_effect=generate):
        bulk.run(folder)
        archive = next((root / 'output').rglob('*.zip'))
        manifest = bulk_archives.read_manifest(archive)
        with zipfile.ZipFile(archive) as zipped:
            actual = sorted(hashlib.sha256(gzip.decompress(zipped.read(name))).hexdigest()
                            for name in manifest['files'])
        assert actual == sorted(original_hashes)
        assert sum(v.get('role') != 'support' for v in manifest['files'].values()) == 2
        assert sum(v.get('role') == 'support' for v in manifest['files'].values()) >= 2
        assert json.loads((folder / 'datasets.json').read_text())['datasets'][0]['inspection']['patients'] == 2
        saved = archive.read_bytes()
        bulk.run(folder)
        assert archive.read_bytes() == saved
    print('PASS: real Synthea native bulk generation, original byte preservation, provider retention, ZIP counts and resume')
