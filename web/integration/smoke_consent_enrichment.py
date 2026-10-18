"""Convert two fixed fixtures in both paths; never start Synthea."""
import gzip
import zipfile
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from unittest.mock import patch
import bulk_generation as bulk
import generation
import synthea_runtime

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    sources = root / 'sources'
    sources.mkdir()
    for name in ('female.json', 'male.json'):
        bundle = json.loads((synthea_runtime.ROOT / 'scripts/tests/fixtures/synthea' / name).read_text())
        # A fixed inpatient contact exercises generated ward organizations.
        next(e['resource'] for e in bundle['entry'] if e['resource']['resourceType']=='Encounter')['class']['code']='IMP'
        (sources / name).write_text(json.dumps(bundle))
    job = root / 'job'
    job.mkdir()
    config = ('CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=NDJSON\nCHECKS_FHIR_VALIDATION=false\n'
              'MEDICATION_ADMINISTRATION_TREATMENT=add-statement\nENCOUNTER_STATION_SERVICE_PROVIDER=generate-reference\n')
    (job / 'default.config').write_text(config)
    settings = generation.Settings(executionMode='bulk', population=2, syntheticConsent=True).model_dump(mode='json')
    (job / 'snapshot.json').write_text(json.dumps({'generation': settings, 'bulkOutputName':'consent', 'bulkLayoutVersion':2}))
    real_process = bulk.checked_process
    def fixed(command, log, update=None):
        if 'synthea.jar' in ' '.join(command):
            shutil.copytree(sources, log.parent / 'synthea/fhir')
        else:
            real_process(command, log, update)
    with patch.object(bulk, 'OUTPUT_ROOT', root / 'results'), patch.object(bulk, 'checked_process', side_effect=fixed):
        bulk.run(job)
    archives=list((root/'results').rglob('*.zip'))
    assert len(archives)==1
    with zipfile.ZipFile(archives[0]) as archive:
        compressed = [archive.read(name) for name in archive.namelist() if name.endswith('.json.gz')]
    assert len(compressed)==2 and not list((root/'results').rglob('*.json.gz'))
    resources = [e['resource'] for data in compressed for e in json.loads(gzip.decompress(data))['entry']]
    consents = [r for r in resources if r['resourceType']=='Consent']
    assert len(consents)==2
    assert sum(r['resourceType']=='MedicationStatement' for r in resources)==2
    assert sum(r['resourceType']=='MedicationAdministration' for r in resources)==2
    assert any(r['resourceType']=='Organization' for r in resources)
    assert {p['type'] for r in consents for p in r['provision']['provision']} == {'permit','deny'}
    assert not (job/'consent-provenance').exists()
    progress=json.loads((job/'bulk-progress.json').read_text())
    assert progress['finishedRuns']==1 and progress['remainingSeconds']==0
    assert progress['generatedBytes']==sum(map(len,compressed))
    normal = root/'normal'
    subprocess.run([sys.executable,str(synthea_runtime.ROOT/'scripts/run_synthea_cases.py'),
                    '-i',str(sources),'-o',str(normal),'--converter-options',str(job/'default.config')],
                   env={**os.environ,'EXCEL2FHIR_TABLE_FORMAT':'csv','EXCEL2FHIR_DIAGNOSTICS':'true',
                        'EXCEL2FHIR_CONSENT_SEED':settings['patientSeed'],
                        'EXCEL2FHIR_CONSENT_CONTEXT':json.dumps({'end_date':settings['endDate'], 'years_of_history':settings['yearsOfHistory']})},check=True)
    summary=json.loads(next(normal.rglob('summary.json')).read_text())
    assert not summary['failures'], summary['failures']
    assert all(v['comparisonStatus']=='passed' for r in summary['results'] for v in r['variants'])
    def content(paths, compressed=False):
        return sorted((gzip.decompress(p.read_bytes()) if compressed else p.read_bytes()) for p in paths)
    assert sorted(gzip.decompress(data) for data in compressed)==content(normal.rglob('*.ndjson'))
    print('PASS: deterministic consent in both paths, identical FHIR, optional diagnostics and no bulk provenance files')
