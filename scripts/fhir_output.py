"""Read one emitted representation of converter bundles for source comparison."""
import bz2
import gzip
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile


def read_output(directory):
    directory = Path(directory)
    bundles = []
    for suffix in ('.json', '.ndjson', '.json.gz', '.json.bz2', '.json.zip', '.xml'):
        files = sorted(directory.glob('*' + suffix))
        if not files:
            continue
        for path in files:
            if suffix == '.json':
                bundles.append(json.loads(path.read_text()))
            elif suffix == '.ndjson':
                bundles.extend(json.loads(line) for line in path.read_text().splitlines() if line.strip())
            elif suffix in ('.json.gz', '.json.bz2'):
                opener = gzip.open if suffix.endswith('gz') else bz2.open
                with opener(path, 'rt', encoding='utf-8') as stream:
                    bundles.append(json.load(stream))
            elif suffix == '.json.zip':
                with zipfile.ZipFile(path) as archive:
                    bundles.extend(json.loads(archive.read(name)) for name in archive.namelist() if name.endswith('.json'))
        if suffix == '.xml':
            root = Path(__file__).resolve().parents[1]
            with tempfile.TemporaryDirectory() as temporary:
                output = Path(temporary) / 'bundles.json'
                subprocess.run(['java', '-cp', str(root / 'target/excel2fhir.jar'),
                                str(root / 'scripts/ReadXmlBundles.java'), str(output)],
                               input=json.dumps([str(p) for p in files]), text=True, capture_output=True, check=True)
                bundles = json.loads(output.read_text())
        break
    if not bundles:
        raise ValueError('FHIR-Ausgabe fehlt: ' + str(directory))
    entries = []
    shared = {}
    for bundle in bundles:
        for entry in bundle['entry']:
            resource = entry['resource']
            if resource['resourceType'] in {'Location', 'Medication'}:
                key = (resource['resourceType'], resource['id'])
                if key in shared:
                    if shared[key] != resource:
                        raise ValueError('Widersprüchliche gemeinsame Ressource: ' + str(key))
                    continue
                shared[key] = resource
            entries.append(entry)
    return {'resourceType': 'Bundle', 'entry': entries}
