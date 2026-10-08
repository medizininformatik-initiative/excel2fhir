"""Read-only structural inspection of uploaded Synthea R4 JSON bundles."""
import json
from pathlib import Path
import sys


def inspect(source):
    files = [source] if source.is_file() else sorted(source.glob('*.json'))
    patients = set()
    tables = []
    skipped = 0
    for path in files:
        try:
            bundle = json.loads(path.read_text(encoding='utf-8'))
            if not isinstance(bundle, dict) or bundle.get('resourceType') != 'Bundle':
                raise ValueError('Expected a FHIR Bundle')
            entries = bundle.get('entry', [])
            if not isinstance(entries, list) or any(not isinstance(e, dict) or not isinstance(e.get('resource'), dict) for e in entries):
                raise ValueError('Expected Bundle entries with resources')
            found = [e['resource'].get('id') for e in entries if e['resource'].get('resourceType') == 'Patient']
            if len(found) > 1 or any(not isinstance(pid, str) or not pid for pid in found):
                raise ValueError('Each patient bundle must contain exactly one Patient with an ID')
            if found:
                if found[0] in patients:
                    raise ValueError('A Patient ID occurs in more than one input bundle')
                patients.add(found[0])
            else:
                skipped += 1
            tables.append({'name': path.name, 'rows': len(entries)})
        except (ValueError, UnicodeError) as error:
            raise ValueError(f'{path.name}: {error}') from error
    if not patients:
        raise ValueError('No patient bundles found')
    return {'valid': True, 'errors': 0, 'warnings': skipped, 'issues': [], 'sheets': tables,
            'patients': len(patients), 'bundlesWithoutPatient': skipped}


if __name__ == '__main__':
    # Keep malformed or unusually large JSON inputs within the inspection budget.
    if sys.platform == 'linux':
        import resource
        resource.setrlimit(resource.RLIMIT_AS, (256 * 1024 * 1024, 256 * 1024 * 1024))
    try:
        result = inspect(Path(sys.argv[1]))
    except (ValueError, MemoryError, RecursionError) as error:
        result = {'valid': False, 'issues': [str(error) or 'Input exceeds the inspection memory budget']}
    Path(sys.argv[2]).write_text(json.dumps(result))
