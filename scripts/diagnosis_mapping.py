"""Versioned, deliberately approximate diagnosis mapping for synthetic test data.

No network or model calls at import time. Improve the table or replace this
function while retaining its explicit mapped/unmapped result contract.
"""
import copy
import hashlib
import json
from pathlib import Path

SNOMED = 'http://snomed.info/sct'
ICD10GM = 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
_PATH = Path(__file__).parent / 'mappings/synthea-diagnoses-icd10gm-2026.json'
_BYTES = _PATH.read_bytes()
_TABLE = json.loads(_BYTES)
_ENTRIES = {entry['sourceCode']: entry for entry in _TABLE['entries']}


def mapping_metadata():
    return {'id': _TABLE['id'], 'sha256': hashlib.sha256(_BYTES).hexdigest(),
            'reviewStatus': _TABLE['reviewStatus']}


def map_diagnosis(condition):
    """Return an auditable decision; target is only an additional coding.

    Existing ICD-10-GM always wins. A changed/unknown display or edition is not
    guessed: the original coding remains independently usable.
    """
    codings = condition.get('code', {}).get('coding', [])
    source = next((c for c in codings if c.get('system') == SNOMED), None)
    result = {'sourceId': condition.get('id'), 'sourceCoding': copy.deepcopy(source),
              'status': 'unmapped', 'target': None, 'reason': ''}
    if any(c.get('system') == ICD10GM for c in codings):
        result.update(status='source-preserved', reason='Vorhandenes ICD-10-GM-Coding unverändert übernommen.')
        return result
    if source is None:
        result['reason'] = 'Kein SNOMED-Quellcoding.'
        return result
    entry = _ENTRIES.get(source.get('code'))
    if source.get('version') or entry is None:
        result['reason'] = 'Quellcode oder explizite Quellversion nicht durch diese Mappingversion abgedeckt.'
        return result
    display = source.get('display') or condition.get('code', {}).get('text', '')
    if ' '.join(display.split()).casefold() != ' '.join(entry['sourceDisplay'].split()).casefold():
        result['reason'] = 'Quellbezeichnung fehlt oder weicht von der beurteilten Bezeichnung ab.'
        return result
    result.update(status=entry['relation'], target=copy.deepcopy(entry['target']), reason=entry['reason'])
    return result
