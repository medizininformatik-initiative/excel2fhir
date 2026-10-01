"""Independent catalogue and serialization checks for annual synthetic targets."""
import copy
import json
from pathlib import Path

CATALOGUES = json.loads((Path(__file__).with_name('mappings') / 'annual-catalogues.json').read_text())['catalogues']
ANNUAL = {key.split('|')[0] for key in CATALOGUES}
DAR_URL = 'http://hl7.org/fhir/StructureDefinition/data-absent-reason'


def annual_table(value, year):
    value = copy.deepcopy(value)
    def visit(item):
        if isinstance(item, dict):
            if item.get('system') in ANNUAL and item.get('code'):
                row = CATALOGUES[item['system'] + '|' + year]['targets'][item['code']]
                item.update(code=row['code'], version=year)
                if 'display' in item: item['display'] = row['display']
            for child in item.values(): visit(child)
        elif isinstance(item, list):
            for child in item: visit(child)
    visit(value)
    return value


def expected_version(year, mode):
    if mode == 'Jahr': return year, None
    if not mode: return None, None
    assert mode.startswith('!dar:')
    return None, {'extension': [{'url': DAR_URL, 'valueCode': mode[5:]}]}


def coding_signature(coding):
    return coding.get('system'), coding.get('code'), coding.get('version'), json.dumps(coding.get('_version'), sort_keys=True)


def expected_signature(coding, year, mode):
    coding = dict(coding)
    if coding.get('system') in ANNUAL:
        version, absent = expected_version(year, mode)
        coding['version'] = version
        if absent: coding['_version'] = absent
    return coding_signature(coding)
