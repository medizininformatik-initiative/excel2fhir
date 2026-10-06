"""Verify configured contact ends before checking unchanged source facts."""
from calendar import monthrange
from copy import deepcopy
from datetime import datetime, timedelta
import re


def instant(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')) if value else None


def expected_end(start, end, policy, application, missing):
    if policy == 'preserve' or (application == 'missing-input-end' and not missing):
        return end
    if policy == 'open':
        return None
    assert start, 'Contact end policy requires a start'
    if policy == 'start':
        return start
    date = instant(start)
    if policy == 'start-plus-second':
        return (date + timedelta(seconds=1)).isoformat()
    assert policy in ('quarter-end', 'year-end'), policy
    month = 12 if policy == 'year-end' else ((date.month - 1) // 3 + 1) * 3
    day = monthrange(date.year, month)[1]
    if len(start) == 10:
        return f'{date.year:04}-{month:02}-{day:02}'
    fraction = re.search(r'\.(\d+)', start)
    fraction = '.' + '9' * len(fraction[1]) if fraction else ''
    offset = re.search(r'(Z|[+-]\d\d:\d\d)$', start)[1]
    return f'{date.year:04}-{month:02}-{day:02}T23:59:59{fraction}{offset}'


def checked_original_periods(source, target, report):
    options = report.get('converterOptions', {})
    if all(options.get(f'ENCOUNTER_{scope}_END_POLICY', 'preserve') == 'preserve' for scope in ('AMBULATORY', 'INPATIENT')):
        return target
    pid = report.get('outputPatient', report['sourcePatient'].replace('_', '-'))
    periods = {}
    kinds = {'Normalstationär': 'normalstationaer', 'Intensivstationär': 'intensivstationaer',
             'Operation': 'operation', 'Untersuchung und Behandlung': 'ub', 'Konsil': 'konsil'}
    for entry in source['entry']:
        r = entry['resource']
        if r['resourceType'] == 'Encounter':
            periods[(pid + '-E-' + report['encounterNumbers'][r['id']], 'einrichtungskontakt', instant(r.get('period', {}).get('start')), '')] = (r.get('period', {}).get('end'), not r.get('period', {}).get('end'))
    rows = report.get('movements', {}).get('contacts', [])
    phase = None
    primary_end = None
    for row in rows:
        root = pid + '-E-' + row[1]
        secondary = row[10] in ('Operation', 'Untersuchung und Behandlung', 'Konsil')
        if not secondary:
            primary_end = row[3]
            if phase is None or phase[:2] != (row[1], row[5]):
                phase = (row[1], row[5], instant(row[2]), not row[3])
            periods[(root, 'abteilungskontakt', phase[2], '')] = (row[3], phase[3])
        periods[(root, 'versorgungsstellenkontakt', instant(row[2]), kinds.get(row[10], ''))] = (row[3] or primary_end, not row[3])
    result = deepcopy(target)
    encounters = {e['resource']['id']: e['resource'] for e in result['entry'] if e['resource']['resourceType'] == 'Encounter'}
    for r in encounters.values():
        scope = {'AMB': 'AMBULATORY', 'IMP': 'INPATIENT'}.get(r.get('class', {}).get('code'))
        policy = options.get(f'ENCOUNTER_{scope}_END_POLICY', 'preserve')
        if policy == 'preserve':
            continue
        level = next((c['code'] for t in r.get('type', []) for c in t.get('coding', []) if c.get('system') == 'http://fhir.de/CodeSystem/Kontaktebene'), 'einrichtungskontakt')
        roots = [pid + '-E-' + number for number in report['encounterNumbers'].values()]
        root_id = next((root for root in roots if r['id'] == root
                        or r['id'].startswith((root + '-A-', root + '-V-'))), None)
        period = r.get('period', {})
        kind = next((c['code'] for t in r.get('type', []) for c in t.get('coding', []) if c.get('system') == 'http://fhir.de/CodeSystem/kontaktart-de'), '') if level == 'versorgungsstellenkontakt' else ''
        key = (root_id, level, instant(period.get('start')), kind)
        assert key in periods, {'unmatchedContactPeriod': key}
        original_end, missing = periods[key]
        wanted = expected_end(period.get('start'), original_end, policy,
                              options.get(f'ENCOUNTER_{scope}_END_APPLICATION', 'always'), missing)
        assert instant(period.get('end')) == instant(wanted), {'encounter': r['id'], 'policy': policy, 'expectedEnd': wanted, 'actualEnd': period.get('end')}
        assert r.get('status') == ('finished' if wanted else 'in-progress'), 'Contact status differs from configured end'
        for location in r.get('location', []):
            assert location.get('period') == period, 'Location period differs from configured contact period'
            assert location.get('status') == ('completed' if wanted else 'active'), 'Location status differs from configured end'
        if original_end:
            period['end'] = original_end
        else:
            period.pop('end', None)
        for location in r.get('location', []):
            location['period'] = deepcopy(period)
            location['status'] = 'completed' if original_end else 'active'
    return result
