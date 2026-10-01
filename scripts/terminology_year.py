"""Annual target catalogues and independent version output for Synthea imports."""
import copy
import hashlib
import json
from pathlib import Path
from workbook_absent import CODES, LABELS

PATH = Path(__file__).with_name('mappings') / 'annual-catalogues.json'
RAW = PATH.read_bytes()
DATA = json.loads(RAW)
NOTICES = json.loads(Path(__file__).with_name('terminology-notices.json').read_text())
SYSTEMS = {key.split('|')[0] for key in DATA['catalogues']}


def settings(options=None):
    options = options or {}
    year = str(options.get('SYNTHEA_MAPPING_YEAR', '2026'))
    mode = options.get('SYNTHEA_VERSION_OUTPUT', 'Jahr')
    if year not in ('2025', '2026'):
        raise ValueError('SYNTHEA_MAPPING_YEAR: choose 2025 or 2026')
    token = CODES.get(mode, mode)
    if mode not in ('Jahr', '') and token not in ('!dar:' + code for code in LABELS):
        raise ValueError('SYNTHEA_VERSION_OUTPUT: choose Jahr, a Data Absent Reason or an empty value')
    return year, token


def version_value(year, mode):
    return str(year) if mode == 'Jahr' else mode


def target(coding, year=2026, system=None):
    if coding is None: return None
    result = copy.deepcopy(coding)
    system = system or result.get('system')
    if system not in SYSTEMS: return result
    year = str(year)
    if year not in ('2025', '2026'): raise ValueError('Unsupported mapping year: ' + year)
    selected = DATA['catalogues'][system + '|' + year]['targets'].get(result['code'])
    if selected is None:
        raise ValueError('Annual mapping target has not been reviewed: ' + system + '|' + result['code'])
    result.update(code=selected['code'], version=year)
    if 'display' in result or 'system' in result: result['display'] = selected['display']
    return result


def procedure_decisions(decisions, year):
    """Transform evaluated target choices; preserve source and context evidence."""
    for decision in decisions.values():
        if decision.get('target'): decision['target'] = target(decision['target'], year)
        if decision.get('targets'): decision['targets'] = [target(c, year) for c in decision['targets']]
        for output in decision['outputs']:
            output['codings'] = [target(c, year) for c in output['codings']]
            if output['codings'][0]['system'] in SYSTEMS:
                output['label'] = output['codings'][0]['display']
        if decision.get('chemotherapyBlock'):
            block = decision['chemotherapyBlock']
            block['target'] = target(block['target'], year)
    return decisions


def metadata(year, mode):
    return {'mappingYear': str(year), 'versionOutput': mode,
            'snomedNotice': copy.deepcopy(NOTICES['http://snomed.info/sct']),
            'catalogueSubsetSha256': hashlib.sha256(RAW).hexdigest(),
            'catalogues': {system: {k: v for k, v in DATA['catalogues'][system+'|'+str(year)].items()
                                    if k != 'targets'} for system in sorted(SYSTEMS)}}


def version_rows(rows, year, mode):
    """Keep the physical code catalogue independent of the exported version."""
    for sheet, pairs in {'Diagnose': [(3, 4), (5, 6)], 'Prozedur': [(3, 5), (6, 7)],
                         'Impfung': [(4, 5)]}.items():
        for row in rows.get(sheet, []):
            versions = []
            for code_index, system_index in pairs:
                selection = row[system_index]
                annual = selection.startswith(('ICD-10-GM ', 'OPS ', 'ATC '))
                versions.append(version_value(selection.rsplit(' ', 1)[-1], mode) if row[code_index] and annual else '')
                if annual: row[system_index] = selection.rsplit(' ', 1)[0]
            row.extend(versions)
    for row in rows.get('Medikation', []):
        row[7] = version_value(year, mode) if row[6] else ''


def emitted(coding, mode='Jahr'):
    """FHIR expectation for audits; mapping reports retain the real catalogue year."""
    result = copy.deepcopy(coding)
    if result.get('system') in SYSTEMS and mode != 'Jahr':
        result.pop('version', None)
        if mode:
            result['_version'] = {'extension': [{'url': 'http://hl7.org/fhir/StructureDefinition/data-absent-reason',
                                                   'valueCode': CODES.get(mode, mode)[5:]}]}
    return result


def signature(coding):
    return (coding.get('system'), coding.get('code'), coding.get('version'),
            json.dumps(coding.get('_version'), sort_keys=True))
