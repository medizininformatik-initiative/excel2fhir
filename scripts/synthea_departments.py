"""Auditable, approximate department selection for synthetic encounters."""
from collections import defaultdict
from datetime import date, datetime, timezone
import hashlib
import json
import re
from pathlib import Path

from diagnosis_mapping import ICD10GM, map_diagnosis

RULE_BYTES = (Path(__file__).parent / 'mappings/synthea-departments.json').read_bytes()
RULES = json.loads(RULE_BYTES)
DEPARTMENTS = RULES['departments']
SPECIALTIES = {(r['system'], r['code']): r['department'] for r in RULES['specialties']}


def metadata():
    return {'version': RULES['version'], 'sha256': hashlib.sha256(RULE_BYTES).hexdigest()}


def instant(value):
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed
    except (ValueError, TypeError, AttributeError):
        return None


def age_at(patient, start):
    try:
        born = date.fromisoformat(patient.get('birthDate', ''))
        today = start.date()
        age = today.year - born.year - ((today.month, today.day) < (born.month, born.day))
        return age if age >= 0 else None
    except (ValueError, AttributeError):
        return None


def select_department(resources, encounter, refs, operations, rng):
    """Use specific source specialties, operations, then time-compatible diagnoses.

    Unknown codes remain reportable gaps. Generic practice and nonspecific ICD
    categories leave the decision to more informative evidence or the fallback.
    """
    patient = next((r for r in resources if r['resourceType'] == 'Patient'), {})
    start = instant(encounter.get('period', {}).get('start'))
    end = instant(encounter.get('period', {}).get('end'))
    age = age_at(patient, start)
    report = {'sourceEncounter': encounter['id'], 'ageAtAdmission': age,
              'candidates': [], 'rejected': [], 'unmappedCodes': [], 'ignoredEvidence': []}
    evidence = []
    relevant_female_diagnosis = any(department == 'Frauenheilkunde und Geburtshilfe'
                                    for _, _, department in operations)

    def diagnosis(concept, weight, source):
        nonlocal relevant_female_diagnosis
        original = concept.get('coding', [])
        codes = [c for c in original if c.get('system') == ICD10GM]
        if not codes:
            decision = map_diagnosis({'code': concept})
            if decision['status'] == 'excluded':
                return
            if decision['target']:
                codes = [decision['target']]
        matched = False
        for coding in codes:
            category = coding.get('code', '')[:3]
            if not re.fullmatch(r'[A-Z][0-9]{2}', category):
                continue
            if 'N60' <= category <= 'N99' or 'O00' <= category <= 'O99':
                relevant_female_diagnosis = True
            for group in RULES['diagnosisGroups']:
                if group['from'] <= category <= group['through']:
                    for department in group['departments']:
                        evidence.append((department, weight, source))
                    matched = True
        if not matched:
            report['unmappedCodes'].extend({'source': source, 'system': c.get('system'),
                                           'code': c.get('code')} for c in original)

    for concept in encounter.get('reasonCode', []):
        diagnosis(concept, 10, 'Encounter.reasonCode')
    explicit_conditions = {refs.get(d.get('condition', {}).get('reference'))
                           for d in encounter.get('diagnosis', [])}
    explicit_conditions.update(refs.get(r.get('reference')) for r in encounter.get('reasonReference', []))
    for resource in resources:
        if resource['resourceType'] != 'Condition':
            continue
        linked = (refs.get(resource.get('encounter', {}).get('reference')) == encounter['id']
                  or resource.get('id') in explicit_conditions - {None})
        onset = instant(resource.get('onsetDateTime') or resource.get('onsetPeriod', {}).get('start'))
        abatement = instant(resource.get('abatementDateTime') or resource.get('abatementPeriod', {}).get('end'))
        incompatible = (onset and end and onset >= end) or (abatement and start and abatement <= start)
        if incompatible:
            if linked:
                report['ignoredEvidence'].append({'source': resource.get('id'),
                                                   'reason': 'Diagnosis period is outside the encounter'})
            continue
        statuses = {c.get('code') for c in resource.get('verificationStatus', {}).get('coding', [])}
        if statuses & {'refuted', 'entered-in-error'}:
            continue
        if linked:
            weight = 8
        elif onset and start and end and start <= onset < end:
            weight = 6
        elif onset and start and onset < start and (abatement is None or abatement > start):
            weight = 1
        else:
            continue
        diagnosis(resource.get('code', {}), weight, 'Condition/' + resource.get('id', ''))

    # A service type or a role referenced by this encounter can carry a specialty.
    # Synthea's generic practice code intentionally contributes no specialty.
    concepts = [('Encounter.serviceType', encounter.get('serviceType', {}))]
    index = {r.get('id'): r for r in resources}
    for participant in encounter.get('participant', []):
        resource = index.get(refs.get(participant.get('individual', {}).get('reference')), {})
        if resource.get('resourceType') == 'PractitionerRole':
            concepts.extend(('PractitionerRole/' + resource['id'], c) for c in resource.get('specialty', []))
    for source, concept in concepts:
        for coding in concept.get('coding', []):
            key = (coding.get('system'), coding.get('code'))
            if key in SPECIALTIES:
                evidence.append((SPECIALTIES[key], 40, source))
            elif coding.get('code') != '208D00000X':
                report['unmappedCodes'].append({'source': source, 'system': key[0], 'code': key[1]})
    evidence.extend((department, 20, 'Procedure/' + source_id) for _, source_id, department in operations)

    def eligible(department):
        rule = DEPARTMENTS.get(department)
        if rule is None:
            return None, 'Unknown department'
        if age is not None and age < 18 and rule.get('pediatricDepartment'):
            department = rule['pediatricDepartment']; rule = DEPARTMENTS[department]
        if 'minAge' in rule and (age is None or age < rule['minAge']):
            return None, 'Minimum age requirement'
        if 'maxAge' in rule and (age is None or age > rule['maxAge']):
            return None, 'Maximum age requirement'
        if rule.get('requiresFemaleOrRelevantDiagnosis') and patient.get('gender') != 'female' and not relevant_female_diagnosis:
            return None, 'Requires female sex or relevant clinical evidence'
        return department, None

    candidates = defaultdict(lambda: {'weight': 0, 'evidence': []})
    for department, weight, source in evidence:
        selected, rejection = eligible(department)
        if rejection:
            report['rejected'].append({'department': department, 'source': source, 'reason': rejection})
            continue
        candidate = candidates[selected]
        candidate['weight'] = max(candidate['weight'], weight)
        candidate['evidence'].append(source)
    # Select within the strongest evidence tier: incidental chronic disease must
    # not displace a procedure, explicit specialty or acute admission diagnosis.
    strongest = max((c['weight'] for c in candidates.values()), default=0)
    chosen = {name: c for name, c in sorted(candidates.items())
              if (c['weight'] >= 40 if strongest >= 40 else c['weight'] >= 20 if strongest >= 20
                  else c['weight'] >= 6 if strongest >= 6 else True)}
    report['candidates'] = [dict(department=name, eligibleForSelection=name in chosen, **c) for name, c in sorted(candidates.items())]
    if chosen:
        names = list(chosen)
        department = rng.choices(names, weights=[chosen[n]['weight'] for n in names], k=1)[0]
        report['fallback'] = False
    else:
        department = 'Pädiatrie' if age is not None and age < 18 else 'Innere Medizin'
        report['fallback'] = True
    report['department'] = department
    # Apply the same eligibility rules to secondary operating-room departments.
    operation_departments = {}
    for _, source_id, op_department in operations:
        selected, rejection = eligible(op_department)
        operation_departments[source_id] = selected or department
        if rejection:
            report['rejected'].append({'department': op_department, 'source': 'Procedure/' + source_id,
                                      'reason': rejection})
    report['operationDepartments'] = operation_departments
    return department, report
