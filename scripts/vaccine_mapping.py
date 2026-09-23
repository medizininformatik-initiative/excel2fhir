"""CVX provenance to German ATC classification, without inventing a product."""
import copy
import hashlib
import json
from pathlib import Path

PATH = Path(__file__).parent / 'mappings/synthea-vaccines-atc-2026.json'
RAW = PATH.read_bytes()
DATA = json.loads(RAW)
ENTRIES = {e['sourceCode']: e for e in DATA['entries']}


def map_vaccine(coding):
    result = {'source': copy.deepcopy(coding), 'target': None, 'status': 'unmapped',
              'reason': 'Keine geprüfte Impfstoffklassifikation; Ereignis und Beschreibung bleiben erhalten.'}
    entry = ENTRIES.get(coding.get('code'))
    if (coding.get('system') != 'http://hl7.org/fhir/sid/cvx' or coding.get('version') or not entry
            or (coding.get('display') and coding['display'] not in entry['sourceDisplays'])):
        return result
    result.update(target=copy.deepcopy(entry['target']), status=entry['relation'], reason=entry['reason'])
    return result


def metadata():
    return {'id': DATA['id'], 'sha256': hashlib.sha256(RAW).hexdigest(), 'sources': DATA['sources']}
