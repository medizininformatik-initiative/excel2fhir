"""Independent checks of contextual OPS evidence and emitted service contracts.

Does not import or execute the projection, context extraction or selection engine.
"""
import hashlib
import json
import re
from datetime import datetime, date

OPS = 'http://fhir.de/CodeSystem/bfarm/ops'


def moment(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')) if value else None


def check(resource, entry, decision, index, all_decisions, table):
    record = decision['contextualSelection']
    rule = table['contextualRules'][entry['contextualRule']]
    assert record['ruleId'] == rule['id'] and record['ruleRevision'] == rule['revision']
    candidates = {c['id']: c for c in rule['candidates']}
    eligible = record['eligibleCandidates']
    assert len({c['id'] for c in eligible}) == len(eligible)
    assert {c['id'] for c in eligible}.isdisjoint({c['id'] for c in record['rejectedCandidates']})
    assert {c['id'] for c in eligible} | {c['id'] for c in record['rejectedCandidates']} == set(candidates)
    for c in eligible:
        assert c['code'] == candidates[c['id']]['code'] and c['weight'] == candidates[c['id']]['weight']
    assert record['assumptions'] == [rule['assumption']]
    selected = record.get('selectedCandidates', [])
    if not selected:
        assert not any(o.get('synthetic') for o in decision['outputs'])
        return
    patient = index[resource['subject']['reference']]
    encounter = index.get(resource.get('encounter', {}).get('reference'), {})
    start = resource.get('performedPeriod', {}).get('start', resource.get('performedDateTime', ''))
    end = resource.get('performedPeriod', {}).get('end', '')
    assert record['originalTimes'] == {'start': start, 'end': end}
    override = entry.get('contextualOverride', {})
    if override.get('emitTogether'):
        assert {candidates[i]['code'] for i in selected} == set(override['emitTogether'])
    else:
        parts = ['ops-sha256-weighted-v1', patient['id'], resource['id'],
                 decision['source']['system'], decision['source']['code'], rule['id']]
        sha = hashlib.sha256(json.dumps(parts, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
        weighted = sorted(eligible, key=lambda c: c['id'])
        bucket = int(sha, 16) * sum(c['weight'] for c in weighted) // 2**256
        chosen = None
        for c in weighted:
            if bucket < c['weight']:
                chosen = c['id']
                break
            bucket -= c['weight']
        assert selected == [chosen], 'Wrong contextual weighted selection'
        assert record['selection']['sha256'] == sha and record['selection']['identity'] == parts
    if 'pregnant-female-source' in rule['hardGuards'] or 'female-reproductive-context' in rule['hardGuards']:
        born, day = date.fromisoformat(patient['birthDate']), date.fromisoformat(start[:10])
        age = day.year - born.year - ((day.month, day.day) < (born.month, born.day))
        assert patient['gender'] == 'female' and 12 <= age <= 55
    wanted = {candidates[i]['code']: candidates[i] for i in selected}
    represented = set()
    for o in decision['outputs']:
        if not o.get('synthetic'):
            assert record['relationship'] == 'synthetic-companion'
            expected_source = entry.get('internationalReplacement', decision['source'])
            assert o['codings'] == [expected_source]
            assert o['start'] == start and o['end'] == end
            continue
        assert len(o['codings']) == 1 and o['codings'][0]['code'] in wanted
        c = wanted[o['codings'][0]['code']]
        assert o['codings'][0] == {k: c[k] for k in ('system', 'code', 'version', 'display')}
        assert o['label'] == c['display'] and o['status'] == 'completed'
        first, last = moment(o['start']), moment(o['end']) or moment(o['start'])
        assert first == moment(start) and last >= first
        period = encounter.get('period', {})
        if period.get('start'):
            assert first >= moment(period['start'])
        if period.get('end'):
            assert last <= moment(period['end'])
        if c.get('syntheticDurationMinutes'):
            assert (last-first).total_seconds() == c['syntheticDurationMinutes'] * 60
        if c.get('syntheticCourse'):
            schedule = record['syntheticSchedule']
            assert len(schedule) == c['syntheticCourse']['unitsPerWeek']
            assert first == moment(schedule[0]['start']) and last == moment(schedule[-1]['end'])
            for unit in schedule:
                assert (moment(unit['end'])-moment(unit['start'])).total_seconds() == c['syntheticCourse']['minutesPerUnit'] * 60
        assert resource['id'] in o['sourceIds']
        represented.add(c['code'])
    for shared in decision.get('sharedOutputs', []):
        assert shared['code'] in wanted
        owner = all_decisions[shared['ownerSourceId']]
        matching = [o for o in owner['outputs'] if any(c['code'] == shared['code'] and c['system'] == OPS for c in o['codings'])]
        assert any(resource['id'] in o['sourceIds'] for o in matching), 'Missing shared OPS output'
        assert owner['contextEvidence']['patientId'] == patient['id']
        assert owner['contextEvidence']['encounterId'] == encounter.get('id')
        represented.add(shared['code'])
    assert represented == set(wanted), 'Selected OPS missing from own or shared output'


def check_conflict(resource, decision, index, table):
    """Require source evidence for a withheld event, independently of its report."""
    assert not decision['outputs'] and decision.get('reason')
    patient = index.get(resource.get('subject', {}).get('reference'), {})
    encounter = index.get(resource.get('encounter', {}).get('reference'), {})
    if not patient.get('id'):
        return
    if encounter and index.get(encounter.get('subject', {}).get('reference'), {}).get('id') != patient['id']:
        return
    start = resource.get('performedPeriod', {}).get('start', resource.get('performedDateTime', ''))
    end = resource.get('performedPeriod', {}).get('end', '')
    death = patient.get('deceasedDateTime')
    if death and any(value and moment(value) > moment(death) for value in (start, end)):
        return
    age = None
    if patient.get('birthDate') and start:
        try:
            born, day = date.fromisoformat(patient['birthDate']), date.fromisoformat(start[:10])
            age = day.year - born.year - ((day.month, day.day) < (born.month, born.day))
        except ValueError:
            pass
    if age is not None and age < 0:
        return
    mappings = {e['sourceCode']: e for e in table['entries']}
    def title(r):
        return ' '.join(' '.join(mappings.get(c.get('code'), {}).get('sourceDisplays', [c.get('display', '')]))
                        if c.get('system') == 'http://snomed.info/sct' else c.get('display', '')
                        for c in r.get('code', {}).get('coding', [])).lower()
    text = title(resource)
    if re.search(r'\b(prostat|testic|vasectomy|penis|penile)', text) and patient.get('gender') != 'male':
        return
    maternal = re.search(r'\b(delivery|parturition|birth|labor|fetal|foetal|antenatal|pregnancy|pregnant|uterine|intrauterine|uterus|fallopian|ovari|vagin|hysterectom|pelvic examination)', text)
    maternal = maternal and not re.search(r'counsel|education|discussion|test|screening|neonatal|newborn care', text)
    if maternal:
        if patient.get('gender') != 'female' or age is None or not 12 <= age <= 55:
            return
        for r in index.values():
            if index.get(r.get('subject', {}).get('reference'), {}).get('id') != patient['id']:
                continue
            if not re.search('hysterectomy|absence of uterus', title(r)):
                continue
            when = r.get('performedPeriod', {}).get('start', r.get('performedDateTime', ''))
            if r.get('resourceType') == 'Procedure' and r.get('id') != resource.get('id') and r.get('status') == 'completed' and when and start and moment(when) <= moment(start):
                return
            if r.get('resourceType') == 'Condition':
                statuses = {c.get('code') for c in r.get('clinicalStatus', {}).get('coding', [])}
                verification = {c.get('code') for c in r.get('verificationStatus', {}).get('coding', [])}
                onset = r.get('onsetDateTime', r.get('onsetPeriod', {}).get('start', ''))
                abated = r.get('abatementDateTime', r.get('abatementPeriod', {}).get('end', ''))
                if not statuses.intersection({'inactive', 'resolved', 'remission'}) and not verification.intersection({'refuted', 'entered-in-error'}) and (not onset or onset[:10] <= start[:10]) and (not abated or abated[:10] >= start[:10]):
                    return
    raise AssertionError('Withheld procedure lacks contradictory source evidence')
