"""Explicit observation code correction; quantities are never recalculated."""
import hashlib
import json
from pathlib import Path

PATH = Path(__file__).parent / 'mappings/synthea-observations-loinc.json'
RAW = PATH.read_bytes()
DATA = json.loads(RAW)
ENTRIES = {e['sourceCode']: e for e in DATA['entries']}


def decision(resource):
    codes = resource.get('code', {}).get('coding', [])
    if not codes: return None
    first = codes[0]; entry = ENTRIES.get(first.get('code'))
    if not entry or first.get('system') != 'http://snomed.info/sct' or first.get('version'): return None
    if first.get('display') not in (None, entry['sourceDisplay']): return None
    q = resource.get('valueQuantity', {})
    if q.get('code') != '{logmar}' or q.get('system') != 'http://unitsofmeasure.org': return None
    if len(codes) > 1 and (len(codes) != 2 or codes[1].get('system') != 'http://loinc.org'
                           or codes[1].get('code') != entry['sourceAdditionalCode'] or codes[1].get('version')): return None
    return {'sourceId': resource.get('id'), 'sourceCodings': codes,
            'targetCodings': [{'system': 'http://loinc.org', 'code': entry['targetCode']}, first],
            'reason': entry['reason'], 'evidence': entry['evidence'], 'valueChange': False}


def metadata():
    return {'id': DATA['id'], 'sha256': hashlib.sha256(RAW).hexdigest()}
