"""Run original Synthea FHIR generation and repeat in isolated worker storage."""
import json
from pathlib import Path
import tempfile
from uuid import uuid4
import zipfile

import generation
import store
import worker

with tempfile.TemporaryDirectory(prefix='native-generation-workbench-') as directory:
    store.ROOT = Path(directory)
    settings = generation.normalize({'outputMode': 'synthea', 'population': 1,
                                     'minAge': 20, 'maxAge': 20, 'patientFilter': 'alive',
                                     'modules': ['appendicitis'], 'yearsOfHistory': 1})
    job = store.create('synthea-generation', 'workbook', generation_settings=settings)
    original = None
    for attempt in range(2):
        assert store.claim() == job
        worker.execute(job)
        folder = store.ROOT / 'jobs' / job
        assert store.get(job)['state'] == 'succeeded', (folder / 'converter.log').read_text()[-6000:]
        assert store.get(job)['generation_result']['generatedPatients'] == 1
        assert not list((folder / 'output').rglob('*.xlsx'))
        manifest = json.loads((folder / 'datasets.json').read_text())
        assert len(manifest['datasets']) == 1 and 'error' not in manifest['datasets'][0], manifest
        with zipfile.ZipFile(folder / 'dataset-0.zip') as archive:
            resources = [e['resource'] for name in archive.namelist() for e in json.loads(archive.read(name)).get('entry', [])]
        patients = [r for r in resources if r['resourceType'] == 'Patient']
        assert len(patients) == 1
        assert any(r['resourceType'] == 'Practitioner' for r in resources)
        assert any(r['resourceType'] == 'Organization' for r in resources)
        assert any(a.get('country') != 'DE' for a in patients[0].get('address', []))
        if original is not None:
            assert patients == original, 'Repeat changed original patient data'
        original = patients
        if attempt == 0:
            job = store.repeat(job, str(uuid4()))
    print('PASS: native Synthea generation, original identities and clinicians, dataset download, count and repeat; no Excel/KDS conversion')
