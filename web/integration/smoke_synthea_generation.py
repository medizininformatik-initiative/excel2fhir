"""Generate through the worker in isolated storage using its pinned Synthea JAR."""
import json
from pathlib import Path
import tempfile
from uuid import uuid4

import generation
import store
import worker

with tempfile.TemporaryDirectory(prefix='generation-workbench-') as directory:
    store.ROOT = Path(directory)
    catalogue = generation.catalogue()
    assert catalogue['revision'] == (generation.synthea_runtime.ROOT / 'scripts/synthea-version.txt').read_text().strip()
    assert catalogue['modules'] and catalogue['keepModules'] and 'Massachusetts' in catalogue['locations']
    settings = generation.normalize({})
    job = store.create('synthea-generation', 'default', 'CONFIGURATION_VERSION=1\nOUTPUT_FORMATS=JSON\n', generation_settings=settings)
    assert store.claim() == job
    worker.execute(job)
    folder = store.ROOT / 'jobs' / job
    assert store.get(job)['state'] == 'succeeded', (folder / 'converter.log').read_text()[-6000:]
    counts = store.get(job)['generation_result']
    assert counts['generatedPatients'] >= 1 and counts['importedPatients'] == counts['generatedPatients'] and counts['failedPatients'] == 0, counts
    originals = sorted((folder / 'output').glob('*/details/sources/synthea/fhir/*.json'))
    assert originals
    loaded = store.editor_input(job)
    repeated = store.create(loaded['source'], 'default', None if (loaded.get('generation') or {}).get('outputMode') == 'synthea' else loaded['configurationProperties'], generation_settings=loaded.get('generation'))
    assert store.claim() == repeated
    worker.execute(repeated)
    repeat_folder = store.ROOT / 'jobs' / repeated
    assert store.get(repeated)['state'] == 'succeeded', (repeat_folder / 'converter.log').read_text()[-6000:]
    repeats = sorted((repeat_folder / 'output').glob('*/details/sources/synthea/fhir/*.json'))
    # Export timestamps may change; patient identities and their clinical histories must not.
    def resources(paths):
        return sorted([json.dumps(entry['resource'], sort_keys=True) for path in paths
                       for entry in json.loads(path.read_text()).get('entry', [])
                       if entry['resource']['resourceType'] not in {'Provenance'}])
    assert resources(originals) == resources(repeats), 'Repeated generator settings changed source resources'
    print('PASS: pinned generator catalogue, generation, import, requested/actual counts and repeatable clinical resources')
