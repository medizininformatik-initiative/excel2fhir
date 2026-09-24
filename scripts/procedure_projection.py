"""Project procedure events, including regional splits and chemotherapy blocks."""
from collections import defaultdict
import copy
from datetime import datetime
import json
from pathlib import Path
from zoneinfo import ZoneInfo
from procedure_mapping import DATA, select_ops
from procedure_context import ContextIndex

BERLIN = ZoneInfo('Europe/Berlin')


def start(resource):
    return resource.get('performedPeriod', {}).get('start', resource.get('performedDateTime', ''))


def instant(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def date(value):
    return instant(value).astimezone(BERLIN).date()


def project(entries):
    resources = [e.get('resource', {}) for e in entries]
    index = {r['resourceType'] + '/' + r['id']: r for r in resources if r.get('id')}
    index.update({e['fullUrl']: e['resource'] for e in entries if e.get('fullUrl')})
    context_index = ContextIndex(entries)
    contexts = {}
    result, blocks = {}, defaultdict(list)
    for r in resources:
        if r.get('resourceType') != 'Procedure':
            continue
        if not r.get('id'):
            continue  # The clinical importer reports this unsupported resource.
        coding = (r.get('code', {}).get('coding') or [{}])[0]
        decision = select_ops(coding)
        decision['outputs'] = []
        result[r['id']] = decision
        ctx = contexts[r['id']] = context_index.context(r)
        decision['contextEvidence'] = ctx.facts()
        problem = ctx.contradiction()
        if problem:
            decision.update(status='context-conflict', reason=problem, target=None)
            continue
        if decision.get('minimumAge'):
            patient = index.get(r.get('subject', {}).get('reference'), {})
            birth = patient.get('birthDate', '')
            day = start(r)[:10]
            old_enough = len(birth) == 10 and len(day) == 10 and (
                int(day[:4]) - int(birth[:4]) - (day[5:] < birth[5:])) >= decision['minimumAge']
            if not old_enough:
                decision.update(target=None, status='source-preserved',
                                reason='Geriatrische Konkretisierung erfordert ein belegtes Alter ab 65 Jahren; SNOMED bleibt erhalten.')
        if decision['status'] == 'excluded':
            continue
        if decision.get('strategy') == 'chemotherapy-block' and start(r):
            key = (r.get('subject', {}).get('reference'), r.get('encounter', {}).get('reference'), r.get('status'))
            # Unrelated events without an encounter must never become one block.
            if key[1] is None:
                key += (r['id'],)
            blocks[key].append(r)
        elif decision.get('strategy'):
            decision.pop('strategy')
            decision.pop('targets', None)
            decision.update(status='source-preserved', reason='Ohne Beginn lässt sich kein Therapieblock bestimmen; SNOMED bleibt erhalten.')
        targets = decision.get('targets')
        if targets:
            for target in targets:
                decision['outputs'].append(output(r, [target], target['display']))
        elif not decision.get('strategy'):
            source = decision.get('internationalReplacement', coding)
            target = decision['target']
            codings = [target, source] if target else [source]
            label = target['display'] if target else r.get('code', {}).get('text', coding.get('display', ''))
            decision['outputs'].append(output(r, codings, label))

    medication_table = json.loads(Path(__file__).with_name('mappings').joinpath(
        'synthea-medications-de-2026.json').read_text())
    medications = {e['source']['code']: e for e in medication_table['entries']}
    for group in blocks.values():
        ordered = sorted(group, key=lambda r: (instant(start(r)), r['id']))
        batches = []
        for r in ordered:
            if not batches or (date(start(r)) - date(start(batches[-1][-1]))).days >= 3:
                batches.append([])
            batches[-1].append(r)
        for batch in batches:
            days = {date(start(r)) for r in batch}
            encounter = batch[0].get('encounter', {}).get('reference')
            subject = batch[0].get('subject', {}).get('reference')
            substances, administration_ids = set(), []
            for med in resources:
                if med.get('resourceType') != 'MedicationAdministration':
                    continue
                if med.get('subject', {}).get('reference') != subject:
                    continue
                if med.get('context', med.get('encounter', {})).get('reference') != encounter:
                    continue
                when = med.get('effectiveDateTime', med.get('effectivePeriod', {}).get('start'))
                if not when or date(when) not in days:
                    continue
                cc = med.get('medicationCodeableConcept') or index.get(
                    med.get('medicationReference', {}).get('reference'), {}).get('code', {})
                coding = (cc.get('coding') or [{}])[0]
                if coding.get('system') != 'http://www.nlm.nih.gov/research/umls/rxnorm':
                    continue
                entry = medications.get(coding.get('code'), {})
                atc = (entry.get('atc') or {}).get('code', '')
                # Antibodies and supportive drugs are not counted as cytostatics.
                display = coding.get('display', '').lower()
                if (not atc.startswith('L01') or atc.startswith(('L01F', 'L01XC'))
                        or not any(form in display for form in ('injection', 'injectable', 'infusion'))):
                    continue
                substances.add(atc)
                administration_ids.append(med['id'])
            count = len(substances) or 1
            n = len(days)
            if n >= 5 and substances.intersection({'L01BC01', 'L01BC02', 'L01BC07', 'L01BC08'}):
                # OPS explicitly distinguishes these prolonged infusion/low-dose
                # protocols. A drug name alone cannot establish the regimen.
                for r in batch:
                    d = result[r['id']]
                    d.update(status='source-preserved', reason='Längerer Block mit ARA-C, 5-FU, Azacitidin oder Decitabin: Dosis/Infusionsprotokoll für die OPS-Abgrenzung fehlt; Quellereignis bleibt vollständig erhalten.')
                    d['outputs'] = [output(r, [d['source']], r['code'].get('text', d['source'].get('display', '')))]
                continue
            if n == 1 and count <= 2:
                code = '8-542.1' + str(count)
            elif n <= 4:
                code = '8-543.' + str(n) + str(min(count, 7))
            elif count == 1:
                code = '8-543.' + str(min(n, 9)) + '1'
            else:
                code = '8-544'
            target = DATA['chemotherapyTargets'][code]
            ids = [r['id'] for r in batch]
            facts = {'sourceIds': ids, 'treatmentDays': sorted(str(d) for d in days),
                     'substancesATC': sorted(substances), 'administrationIds': administration_ids,
                     'substanceCount': count, 'target': copy.deepcopy(target),
                     'assumptions': ['Intravenöse Standardtherapie; keine besondere Hochdosis- oder Sonderprotokollannahme.',
                                     'Pausentage werden nicht als protokollgemäße Behandlungstage ergänzt.']}
            if not substances:
                facts['assumptions'].append('Keine zuordenbare parenterale Zytostatikagabe: eine intravenöse Substanz synthetisch angenommen.')
            projected = output(batch[0], [target], target['display'])
            projected['sourceIds'] = ids
            projected['end'] = max((r.get('performedPeriod', {}).get('end') or start(r) for r in batch), key=instant)
            for r in batch:
                result[r['id']]['chemotherapyBlock'] = copy.deepcopy(facts)
            result[batch[0]['id']]['outputs'].append(projected)
    from contextual_procedures import apply_contextual
    return apply_contextual(result, contexts)


def output(resource, codings, label):
    return {'sourceIds': [resource['id']], 'codings': copy.deepcopy(codings), 'label': label,
            'start': start(resource), 'end': resource.get('performedPeriod', {}).get('end', ''),
            'status': resource.get('status', '')}
