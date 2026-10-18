"""Persist independent generation, source-transfer and validation outcomes."""
from collections import Counter
from functools import lru_cache
from pathlib import Path
import json


def collect(folder, snapshot, state):
    native = snapshot.get('generation', {}).get('outputMode') == 'synthea'
    validation = {'status': 'not-requested' if not snapshot.get('validation') or native else 'unavailable',
                  'reports': 0, 'findings': 0, 'incomplete': 0}
    if snapshot.get('validation') and not native:
        counts = Counter()
        for path in (folder / 'output').rglob('*.validation.json'):
            try:
                report = json.loads(path.read_text())
                status = report.get('status', 'UNKNOWN')
                if status not in {'VALID', 'WARNING', 'IGNORED', 'NOT_CHECKED', 'ERROR', 'FATAL'}:
                    status = 'UNKNOWN'
                if report.get('referencesWithoutTargetInBundle'):
                    status = 'ERROR'
                counts[status] += 1
            except (OSError, ValueError):
                counts['UNKNOWN'] += 1
        validation.update(reports=sum(counts.values()), findings=sum(counts[k] for k in ('ERROR', 'FATAL', 'WARNING', 'IGNORED')),
                          incomplete=counts['NOT_CHECKED'] + counts['UNKNOWN'])
        if validation['reports']:
            validation['status'] = 'findings' if validation['findings'] else 'incomplete' if validation['incomplete'] else 'passed'
    transfer = {'status': 'not-applicable', 'checked': 0, 'differences': 0, 'unavailable': 0,
                'importNotes': 0, 'configuredOmissions': 0, 'notes': [], 'details': []}
    if snapshot.get('inputKind', '').startswith('synthea') and not native:
        if snapshot.get('diagnostics') is False:
            transfer['status'] = 'not-requested'
            return {'generation': state, 'validation': validation, 'transfer': transfer}
        transfer['status'] = 'unavailable'
        notes = Counter()
        for path in (folder / 'output').glob('*/details/reports/summary.json'):
            try:
                summary = json.loads(path.read_text())
            except (OSError, ValueError):
                continue
            for result in summary.get('results', []):
                for variant in result.get('variants', []):
                    status = variant.get('comparisonStatus')
                    if status is None:
                        continue  # Historical reports do not establish the independent outcome.
                    transfer['checked'] += status in {'passed', 'differences'}
                    transfer['differences'] += status == 'differences'
                    transfer['unavailable'] += status == 'unavailable'
                    transfer['configuredOmissions'] += len(variant.get('configuredOmissions', []))
                    for note in variant.get('importNotes', []):
                        notes[(note.get('resourceType', ''), note.get('path', ''), note.get('reason', ''))] += 1
                    if status != 'passed':
                        transfer['details'].append({'source': result.get('source', '').replace('\\', '/').rsplit('/', 1)[-1],
                                                    'variant': variant.get('name', ''), 'status': status,
                                                    'message': variant.get('comparisonDetails', '')})
        transfer['notes'] = [{'resourceType': key[0], 'path': key[1], 'reason': key[2], 'count': count}
                             for key, count in sorted(notes.items())]
        transfer['importNotes'] = sum(notes.values())
        if transfer['checked'] or transfer['unavailable']:
            transfer['status'] = 'findings' if transfer['differences'] or transfer['importNotes'] else 'unavailable' if transfer['unavailable'] else 'passed'
    return {'generation': state, 'validation': validation, 'transfer': transfer}


def save(folder, snapshot, state):
    result = collect(folder, snapshot, state)
    temporary = folder / 'outcomes.tmp'
    temporary.write_text(json.dumps(result, ensure_ascii=False))
    temporary.replace(folder / 'outcomes.json')
    return result


@lru_cache(maxsize=128)
def historical(folder, snapshot_json, state, modified):
    return collect(Path(folder), json.loads(snapshot_json), state)


def read(folder, snapshot, state):
    path = folder / 'outcomes.json'
    if path.is_file():
        return json.loads(path.read_text())
    if state in {'succeeded', 'failed', 'cancelled', 'interrupted'}:
        return historical(str(folder), json.dumps(snapshot, sort_keys=True), state, folder.stat().st_mtime_ns)
    return None
