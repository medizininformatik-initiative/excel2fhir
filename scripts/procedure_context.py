"""Patient/episode facts for synthetic OPS decisions; source resources stay immutable."""
from collections import defaultdict
from datetime import date, datetime
import re

from procedure_mapping import ENTRIES
from diagnosis_mapping import _ENTRIES as DIAGNOSES

SNOMED = 'http://snomed.info/sct'


def begin(r):
    return r.get('performedPeriod', {}).get('start', r.get('performedDateTime', ''))


def instant(value):
    try:
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
        return result if result.tzinfo else None
    except (ValueError, TypeError, AttributeError):
        return None


def age_at(birth, when):
    try:
        b, d = date.fromisoformat(birth), date.fromisoformat(when[:10])
        return d.year - b.year - ((d.month, d.day) < (b.month, b.day))
    except (ValueError, TypeError, AttributeError):
        return None


def labels(cc):
    """Known terminology labels take precedence over mutable exported display text."""
    result = []
    for c in cc.get('coding', []):
        entry = (ENTRIES.get(c.get('code')) or DIAGNOSES.get(c.get('code'))) if c.get('system') == SNOMED else None
        if entry:
            result.extend(entry.get('sourceDisplays', []))
        elif c.get('display'):
            result.append(c['display'])
    return ' '.join(result).lower()


def has(text, pattern):
    return bool(re.search(pattern, text, re.I))


def regions(text):
    patterns = {'hand': r'\b(hand|wrist|finger)', 'forearm': r'\bforearm',
                'upper-arm': r'\b(upper arm|humerus|elbow)', 'thigh': r'\b(thigh|knee|femur)',
                'foot': r'\b(foot|feet|ankle|toe)', 'head': r'\b(head|face|facial|skull)',
                'jaw': r'\b(jaw|mandib|maxill)', 'rib': r'\brib', 'hip': r'\bhip',
                'spine': r'\b(spin|vertebr)', 'pelvis': r'\bpelvi',
                'chest': r'\b(chest|thorax|thoracic|lung|pulmon)',
                'oesophagus': r'\b(esophag|oesophag)', 'pharynx': r'\b(pharyn|oropharyn)',
                'salivary': r'\b(saliv|sialorr)', 'stomach': r'\b(stomach|gastric)',
                'colon': r'\b(colon|colonic)', 'intestine': r'\b(intestin|small bowel)'}
    return {k for k, pattern in patterns.items() if has(text, pattern)}


class ContextIndex:
    def __init__(self, entries):
        self.resources = [e.get('resource', {}) for e in entries]
        self.index = {}
        for e in entries:
            r = e.get('resource', {})
            if r.get('id'):
                self.index[r.get('resourceType', '') + '/' + r['id']] = r
                if e.get('fullUrl'):
                    self.index[e['fullUrl']] = r
        self.conditions = defaultdict(list)
        self.procedures = defaultdict(list)
        for r in self.resources:
            patient = self.resolve(r.get('subject', {}).get('reference'))
            if patient.get('resourceType') != 'Patient':
                continue
            if r.get('resourceType') == 'Condition':
                self.conditions[patient['id']].append(r)
            if r.get('resourceType') == 'Procedure':
                encounter = self.resolve(r.get('encounter', {}).get('reference'))
                self.procedures[(patient['id'], encounter.get('id'))].append(r)

    def resolve(self, ref):
        return self.index.get(ref, {})

    def context(self, r):
        return EventContext(self, r)


class EventContext:
    def __init__(self, index, event):
        self.event = event
        self.patient = index.resolve(event.get('subject', {}).get('reference'))
        self.encounter = index.resolve(event.get('encounter', {}).get('reference'))
        self.encounter_patient = index.resolve(self.encounter.get('subject', {}).get('reference'))
        self.patient_id = self.patient.get('id')
        self.start = instant(begin(event))
        self.end = instant(event.get('performedPeriod', {}).get('end'))
        self.age = age_at(self.patient.get('birthDate'), begin(event))
        self.sex = self.patient.get('gender')
        self.source = labels(event.get('code', {}))
        self.evidence = []
        reasons = [labels(c) for c in event.get('reasonCode', [])]
        for ref in event.get('reasonReference', []):
            reason = index.resolve(ref.get('reference'))
            if reason:
                owner = index.resolve(reason.get('subject', {}).get('reference'))
                if owner.get('id') != self.patient_id:
                    raise ValueError('Cross-patient procedure reason')
                if self.active(reason):
                    reasons.append(labels(reason.get('code', {})))
                    self.evidence.append(reason.get('id'))
        conditions = []
        for condition in index.conditions.get(self.patient_id, []):
            encounter = index.resolve(condition.get('encounter', {}).get('reference'))
            if self.active(condition) and (encounter.get('id') == self.encounter.get('id') or self.in_time(condition)):
                conditions.append(labels(condition.get('code', {})))
                self.evidence.append(condition.get('id'))
        self.reason = ' '.join(reasons)
        self.clinical = ' '.join(reasons + conditions)
        self.body = ' '.join(labels(c) for c in event.get('bodySite', []))
        self.site_text = self.body or self.reason or self.clinical
        self.regions = regions(self.site_text)
        self.text = ' '.join([self.source, self.clinical, self.body])
        self.siblings = index.procedures.get((self.patient_id, self.encounter.get('id')), []) if self.encounter else [event]
        self.prior = []
        for p in self.siblings:
            when = instant(begin(p))
            if p.get('id') != event.get('id') and p.get('status') == 'completed' and when and self.start and when <= self.start:
                self.prior.append(labels(p.get('code', {})))
        # Organ absence is permanent and may precede the current encounter.
        for (pid, _), procedures in index.procedures.items():
            if pid != self.patient_id:
                continue
            for p in procedures:
                when = instant(begin(p))
                text = labels(p.get('code', {}))
                if p.get('id') != event.get('id') and p.get('status') == 'completed' and when and self.start and when <= self.start and has(text, r'hysterectomy|prostatectomy|amputation'):
                    self.prior.append(text)
        self.history = ' '.join(self.prior + conditions)

    def in_time(self, condition):
        onset = condition.get('onsetDateTime', condition.get('onsetPeriod', {}).get('start', ''))
        return bool(onset and begin(self.event) and onset[:10] <= begin(self.event)[:10])

    def active(self, condition):
        status = {c.get('code') for c in condition.get('clinicalStatus', {}).get('coding', [])}
        verification = {c.get('code') for c in condition.get('verificationStatus', {}).get('coding', [])}
        if status.intersection({'inactive', 'resolved', 'remission'}) or verification.intersection({'refuted', 'entered-in-error'}):
            return False
        onset = condition.get('onsetDateTime', condition.get('onsetPeriod', {}).get('start', ''))
        end = condition.get('abatementDateTime', condition.get('abatementPeriod', {}).get('end', ''))
        when = begin(self.event)
        return not ((onset and when and onset[:10] > when[:10]) or (end and when and end[:10] < when[:10]))

    def matches(self, pattern):
        return has(self.text, pattern)

    def facts(self):
        return {'patientId': self.patient_id, 'encounterId': self.encounter.get('id'),
                'ageAtEvent': self.age, 'sourceSex': self.sex,
                'evidenceIds': sorted(set(e for e in self.evidence if e)), 'regions': sorted(self.regions)}

    def contradiction(self):
        if not self.patient_id:
            return 'Missing source patient identity'
        if self.encounter:
            if self.encounter_patient.get('id') != self.patient_id:
                return 'Conflicting encounter patient'
        maternal = has(self.source, r'\b(delivery|parturition|birth|labor|fetal|foetal|antenatal|pregnancy|pregnant|uterine|intrauterine|uterus|fallopian|ovari|vagin|hysterectom|pelvic examination)') and not has(self.source, r'counsel|education|discussion|test|screening|neonatal|newborn care')
        male_organ = has(self.source, r'\b(prostat|testic|vasectomy|penis|penile)')
        if maternal and (self.sex != 'female' or self.age is None or not 12 <= self.age <= 55):
            return 'Maternal/reproductive source event conflicts with patient sex or age, or required facts are missing'
        if maternal and has(self.history, r'hysterectomy|absence of uterus'):
            return 'Reproductive event conflicts with absent uterus'
        if male_organ and self.sex != 'male':
            return 'Male reproductive source event requires compatible source sex'
        if self.age is not None and self.age < 0:
            return 'Procedure occurs before patient birth'
        death = instant(self.patient.get('deceasedDateTime'))
        if death and ((self.start and self.start > death) or (self.end and self.end > death)):
            return 'Procedure occurs after patient death'
        return None
