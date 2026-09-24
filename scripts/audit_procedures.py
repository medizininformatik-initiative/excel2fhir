"""Independent procedure audit: read mapping data, never call projection code."""
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
from zoneinfo import ZoneInfo

MAPS = Path(__file__).with_name('mappings')
SNOMED = 'http://snomed.info/sct'
OPS = 'http://fhir.de/CodeSystem/bfarm/ops'


def audit_procedures(source, target, rows, report):
    table = json.loads((MAPS / 'synthea-procedures-ops-2026.json').read_text())
    mappings = {e['sourceCode']: e for e in table['entries']}
    medications = {e['source']['code']: e for e in json.loads(
        (MAPS / 'synthea-medications-de-2026.json').read_text())['entries']}
    entries = source['entry']
    src = [e['resource'] for e in entries]
    index = {r['resourceType'] + '/' + r['id']: r for r in src}
    index.update({e['fullUrl']: e['resource'] for e in entries if e.get('fullUrl')})
    reported = {m['sourceId']: m for m in report['clinicalMappings'] if 'outputs' in m}
    groups, expected = {}, []

    def begin(r):
        return r.get('performedPeriod', {}).get('start', r.get('performedDateTime', ''))

    def parsed(value):
        return datetime.fromisoformat(value.replace('Z', '+00:00'))

    def day(value):
        return parsed(value).astimezone(ZoneInfo('Europe/Berlin')).date()

    def add(owner, ids, codes, first, last, label=None):
        expected.append((owner, ids, codes, first, last, label))

    for r in src:
        if r['resourceType'] != 'Procedure':
            continue
        coding = r['code']['coding'][0]
        entry = mappings.get(coding['code'], {})
        decision = reported[r['id']]
        if decision.get('status') == 'context-conflict':
            from audit_contextual_procedures import check_conflict
            check_conflict(r, decision, index, table)
            continue
        if (coding.get('system') != SNOMED or coding.get('version') or
                (coding.get('display') and coding['display'] not in entry.get('sourceDisplays', []))):
            entry = {}
        if entry.get('status') == 'excluded':
            continue
        entry = dict(entry)
        if entry.get('contextualRule') and decision.get('contextualSelection'):
            from audit_contextual_procedures import check as check_contextual
            check_contextual(r, entry, decision, index, reported, table)
            if decision.get('status') == 'synthetic-contextual':
                for o in decision['outputs']:
                    add(r, o['sourceIds'], o['codings'], o['start'], o['end'],
                        o['label'] if o.get('synthetic') else None)
                continue
        if entry.get('minimumAge'):
            birth = index[r['subject']['reference']].get('birthDate', '')
            when = begin(r)[:10]
            age = int(when[:4]) - int(birth[:4]) - (when[5:] < birth[5:]) if len(birth) == 10 and len(when) == 10 else -1
            if age < entry['minimumAge']:
                entry['target'] = None
        chemo = entry.get('strategy') == 'chemotherapy-block' and bool(begin(r))
        if chemo:
            key = (r['subject']['reference'], r.get('encounter', {}).get('reference'), r.get('status'))
            if key[1] is None:
                key += (r['id'],)
            groups.setdefault(key, []).append(r)
        targets = entry.get('targets', []) if not entry.get('strategy') or chemo else []
        if targets:
            for c in targets:
                add(r, [r['id']], [c], begin(r), r.get('performedPeriod', {}).get('end', ''), c['display'])
        elif not chemo:
            national = entry.get('target')
            original = entry.get('internationalReplacement', coding)
            add(r, [r['id']], [national, original] if national else [original], begin(r),
                r.get('performedPeriod', {}).get('end', ''), national['display'] if national else None)

    for key, events in groups.items():
        events.sort(key=lambda r: (parsed(begin(r)), r['id']))
        boundaries = [0] + [i for i in range(1, len(events))
                            if (day(begin(events[i])) - day(begin(events[i-1]))).days >= 3] + [len(events)]
        for left, right in zip(boundaries, boundaries[1:]):
            block = events[left:right]
            days = {day(begin(r)) for r in block}
            atcs, admins = set(), []
            for m in src:
                if m['resourceType'] != 'MedicationAdministration' or m.get('subject', {}).get('reference') != key[0]:
                    continue
                if m.get('context', m.get('encounter', {})).get('reference') != key[1]:
                    continue
                timestamp = m.get('effectiveDateTime') or m.get('effectivePeriod', {}).get('start')
                if not timestamp or day(timestamp) not in days:
                    continue
                concept = m.get('medicationCodeableConcept') or index.get(
                    m.get('medicationReference', {}).get('reference'), {}).get('code', {})
                c = (concept.get('coding') or [{}])[0]
                mapped = medications.get(c.get('code'), {}) if c.get('system') == 'http://www.nlm.nih.gov/research/umls/rxnorm' else {}
                atc = (mapped.get('atc') or {}).get('code', '')
                if (atc.startswith('L01') and not atc.startswith(('L01F', 'L01XC'))
                        and any(s in c.get('display', '').lower() for s in ['injection', 'injectable', 'infusion'])):
                    atcs.add(atc)
                    admins.append(m['id'])
            n, count = len(days), max(1, len(atcs))
            if n >= 5 and atcs.intersection({'L01BC01', 'L01BC02', 'L01BC07', 'L01BC08'}):
                ids = {r['id'] for r in block}
                expected = [e for e in expected if e[0]['id'] not in ids]
                for r in block:
                    add(r, [r['id']], r['code']['coding'][:1], begin(r), r.get('performedPeriod', {}).get('end', ''))
                    assert reported[r['id']]['status'] == 'source-preserved'
                continue
            code = ('8-542.1' + str(count) if n == 1 and count < 3 else
                    '8-543.' + str(n) + str(min(7, count)) if n < 5 else
                    '8-543.' + str(min(9, n)) + '1' if count == 1 else '8-544')
            target_code = table['chemotherapyTargets'][code]
            ids = [r['id'] for r in block]
            for r in block:
                evidence = reported[r['id']]['chemotherapyBlock']
                assert evidence['sourceIds'] == ids
                assert evidence['treatmentDays'] == sorted(str(d) for d in days)
                assert evidence['substancesATC'] == sorted(atcs)
                assert evidence['administrationIds'] == admins
                assert evidence['substanceCount'] == count and evidence['target'] == target_code
            end = max((r.get('performedPeriod', {}).get('end') or begin(r) for r in block), key=parsed)
            add(block[0], ids, [target_code], begin(block[0]), end, target_code['display'])

    wanted = Counter()
    matched_rows = set()
    for owner, ids, codes, first, last, label in expected:
        outputs = reported[owner['id']]['outputs']
        found = [o for o in outputs if set(ids).issubset(o['sourceIds']) and o['codings'] == codes and o['start'] == first and o['end'] == last]
        assert len(found) == 1, ('Procedure projection evidence', owner['id'])
        o = found[0]
        for linked_id in set(o['sourceIds']) - set(ids):
            assert any(shared['ownerSourceId'] == owner['id'] and shared['code'] == codes[0]['code']
                       for shared in reported[linked_id].get('sharedOutputs', [])), 'Unexplained shared source'
        row_number = o['row'] - 2
        assert row_number not in matched_rows, 'Duplicate procedure row'
        matched_rows.add(row_number)
        row = rows[row_number]
        assert row['Prozedurencode'] == codes[0]['code']
        assert row.get('Zusatzcode', '') == (codes[1]['code'] if len(codes) == 2 else '')
        assert row.get('Durchführungsbeginn', '') == first and row.get('Ende', '') == last
        assert row.get('Status', '') == owner['status']
        expected_system = 'OPS 2026' if codes[0]['system'] == OPS else 'SNOMED CT (Version nicht angegeben)'
        assert row['Codesystem'] == expected_system
        assert row.get('Zusatzcodesystem', '') == ('SNOMED CT (Version nicht angegeben)' if len(codes) == 2 else '')
        if label is not None:
            assert row['Prozedurentext'] == label == o['label'], 'Wrong OPS description'
        else:
            label = row.get('Prozedurentext', '')
        assert o['status'] == owner['status']
        # Compare FHIR's normalized instants with the original time window.
        wanted[(tuple((c['system'], c['code'], c.get('version')) for c in codes), label,
                owner['status'], parsed(first) if first else None, parsed(last) if last else None)] += 1
    actual = Counter()
    for r in target['entry']:
        r = r['resource']
        if r['resourceType'] != 'Procedure':
            continue
        cs = tuple((c['system'], c['code'], c.get('version')) for c in r['code']['coding'])
        end = r.get('performedPeriod', {}).get('end')
        actual[(cs, r['code'].get('text', ''), r['status'], parsed(begin(r)) if begin(r) else None,
                parsed(end) if end else None)] += 1
    assert matched_rows == set(range(len(rows)))
    assert wanted == actual, {'missingProcedures': list((wanted-actual).items())[:2],
                              'unexpectedProcedures': list((actual-wanted).items())[:2]}
    return {'sourceProcedures': sum(r['resourceType'] == 'Procedure' for r in src),
            'projectedProcedures': len(expected), 'opsProcedures': sum(cs[0]['system'] == OPS for _, _, cs, *_ in expected)}
