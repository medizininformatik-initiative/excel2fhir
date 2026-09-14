"""Explicit OPS decisions; source SNOMED remains supplementary or a reported fallback."""
import copy
import hashlib
import json
from pathlib import Path

PATH = Path(__file__).parent / 'mappings/synthea-procedures-ops-2026.json'
RAW = PATH.read_bytes()
DATA = json.loads(RAW)
ENTRIES = {e['sourceCode']: e for e in DATA['entries']}


def select_ops(coding):
    result = {'source': copy.deepcopy(coding), 'target': None, 'status': 'not-assessed',
              'reason': 'Quellkonzept, Version oder Bezeichnung nicht in der geprüften OPS-Tabelle; SNOMED bleibt erhalten.'}
    entry = ENTRIES.get(coding.get('code'))
    if (coding.get('system') != 'http://snomed.info/sct' or coding.get('version') or not entry
            or (coding.get('display') and coding['display'] not in entry['sourceDisplays'])):
        return result
    result.update({key: copy.deepcopy(entry[key]) for key in ['target', 'status', 'reason']})
    if entry.get('internationalReplacement'):
        result['internationalReplacement'] = copy.deepcopy(entry['internationalReplacement'])
        result['evidence'] = copy.deepcopy(entry['evidence'])
    return result


def metadata():
    return {'id': DATA['id'], 'sha256': hashlib.sha256(RAW).hexdigest(), 'targetSource': DATA['targetSource']}
