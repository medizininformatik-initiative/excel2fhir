"""Context-filtered, event-stable illustrative OPS projection and provenance."""
import copy
import hashlib
import json
from datetime import timedelta

from procedure_mapping import DATA, ENTRIES
from procedure_context import begin, instant, has, SNOMED

OPS = 'http://fhir.de/CodeSystem/bfarm/ops'
ALGORITHM = 'ops-sha256-weighted-v1'
RULES = DATA['contextualRules']
# Some guards restrict source-group membership or use shared candidate/time/dedup
# checks. Keep an explicit implementation inventory so new names cannot pass silently.
SUPPORTED_GUARDS = {
    'access-route-compatible',
    'airway-compatible',
    'allergy-topic-required',
    'anaesthesia-compatible',
    'anatomical-region-compatible',
    'anatomical-region-required',
    'associated-operation-required',
    'bowel-context-compatible',
    'candidate-age-compatible',
    'candidate-method-compatible',
    'cast-region-compatible',
    'chest-access-compatible',
    'context-selects-assessment',
    'contraception-context',
    'contrast-compatible',
    'cooperative-test-feasible',
    'deduplicate-access',
    'deduplicate-birth',
    'deduplicate-course',
    'deduplicate-session',
    'deduplicate-stay',
    'dentition-compatible',
    'developmental-method-age-compatible',
    'ectopic-pregnancy-context',
    'education-topic-compatible',
    'female-reproductive-context',
    'gestational-stage-compatible',
    'hiv-context-required',
    'knee-injury-context',
    'maternal-delivery-episode',
    'medication-route-compatible',
    'metabolic-challenge-compatible',
    'monitoring-context-compatible',
    'neuromuscular-topic-compatible',
    'palliative-context',
    'pathogen-required',
    'pregnant-female-source',
    'renal-transplant-context',
    'skin-incision-context',
    'solid-tumour-context',
    'specimen-compatible',
    'stress-test-compatible',
    'therapy-period-compatible',
    'time-window-compatible',
    'wound-present',
}


def choose(patient_id, event_id, source, rule_id, candidates):
    candidates = sorted(candidates, key=lambda c: c['id'])
    if not candidates or any(type(c['weight']) is not int or c['weight'] <= 0 for c in candidates):
        raise ValueError('Selection requires positive integer candidate weights')
    parts = [ALGORITHM, patient_id, event_id, source['system'], source['code'], rule_id]
    digest = hashlib.sha256(json.dumps(parts, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
    total = sum(c['weight'] for c in candidates)
    bucket = int(digest, 16) * total // (1 << 256)
    cursor = 0
    for c in candidates:
        cursor += c['weight']
        if bucket < cursor:
            return c, {'algorithmId': ALGORITHM, 'identity': parts, 'sha256': digest,
                       'bucket': bucket, 'totalWeight': total}
    raise AssertionError('Unreachable weighted interval')


def coding(candidate):
    return {k: candidate[k] for k in ('system', 'code', 'version', 'display')}


def window(ctx, candidate):
    """Materialize inside known encounter boundaries; never move the encounter."""
    if ctx.start is None:
        return None
    end = ctx.end or ctx.start
    if end < ctx.start:
        return None
    minutes = candidate.get('syntheticDurationMinutes')
    course = candidate.get('syntheticCourse')
    if minutes:
        end = ctx.start + timedelta(minutes=minutes)
    if course:
        end = ctx.start + timedelta(days=4, minutes=course['minutesPerUnit'])
    encounter_start = instant(ctx.encounter.get('period', {}).get('start'))
    encounter_end = instant(ctx.encounter.get('period', {}).get('end'))
    if encounter_start and ctx.start < encounter_start:
        return None
    if encounter_end and end > encounter_end:
        return None
    if (minutes or course) and (not encounter_start or not encounter_end):
        return None
    death = instant(ctx.patient.get('deceasedDateTime'))
    if death and end > death:
        return None
    return [(ctx.start, end)]


def candidate_reason(ctx, rule, candidate, override):
    code = candidate['code']
    group = rule['id']
    allowed = override.get('candidateCodes') or override.get('emitTogether')
    if allowed and code not in allowed:
        return 'Explicit source-specific candidate restriction'
    lower = candidate.get('ageYears', {}).get('min')
    if lower is not None and (ctx.age is None or ctx.age < lower):
        return 'Required age is missing or below candidate minimum'
    if ctx.age is not None and ctx.age < 0:
        return 'Procedure precedes birth'
    if group == 'general-assessment':
        if code in ('1-773', '1-774') and not ctx.matches(r'palliat|hospice|terminal illness'):
            return 'Palliative context absent'
        if code == '1-710' and not ctx.matches(r'pulmon|respirat|asthma|copd|lung'):
            return 'Pulmonary context absent'
        if code == '1-760' and not ctx.matches(r'diabet|glucose|metaboli|endocrin'):
            return 'Metabolic context absent'
        if code == '1-901.0' and ctx.matches(r'renal disorder medication|hematologic disorder medication|spinal surgery|orthopedic surgeon|musculoskeletal'):
            return 'Organ-specific review is not a generic psychosocial interview'
    if code in ('1-710', '1-712', '1-715', '1-716', '3-031', '1-798.0', '1-799.0') and ctx.matches(r'acute respiratory (failure|distress)|unconscious|coma\b|cardiogenic shock|tetraplegi|quadriplegi'):
        return 'Cooperative/stress examination conflicts with acute instability or function'
    if group == 'respiratory-testing' and has(ctx.source, 'acute respiratory failure'):
        return 'Acute respiratory failure cannot be replaced by cooperative testing'
    if group == 'movement-assessment':
        if code == '1-799.0' and ctx.regions and 'spine' not in ctx.regions:
            return 'Spinal study conflicts with known region'
        if code == '1-798.0' and ctx.regions.intersection({'hand', 'forearm', 'upper-arm', 'head'}):
            return 'Gait study conflicts with known region'
    if group == 'wound-suture':
        known = override.get('regionByReasonCode', {})
        reason_codes = {c['code'] for cc in ctx.event.get('reasonCode', []) for c in cc.get('coding', []) if c.get('system') == SNOMED}
        fixed = {known[c] for c in reason_codes if c in known}
        region_codes = {'hand': '5-900.09', 'forearm': '5-900.08', 'thigh': '5-900.0e', 'foot': '5-900.0g', 'head': '5-900.04'}
        fixed.update(region_codes[r] for r in ctx.regions if r in region_codes)
        if fixed and (len(fixed) != 1 or code not in fixed):
            return 'Known wound region fixes the target or is ambiguous'
        if ctx.matches(r'\b(lip|eyelid|ear|nose)\b'):
            return 'Special facial structure is outside simple skin closure candidates'
        if not fixed:
            return 'Wound region missing; source preserved rather than generating unrelated anatomy'
    if group == 'chest-access':
        if has(ctx.source, 'sternotomy') and code != '5-340.9':
            return 'Explicit sternotomy fixes access'
        if has(ctx.source, 'opening of chest') and code != '5-340.1':
            return 'Explicit chest opening uses thoracotomy'
    if group in ('imaging-referral', 'spine-pelvis-imaging'):
        wanted = {'3-202': 'chest', '3-203': 'spine', '3-206': 'pelvis'}[code]
        if group == 'imaging-referral' and wanted not in ctx.regions:
            return 'Requested body region missing or incompatible'
    if group == 'swallow-imaging':
        if 'oesophagus' in ctx.regions and code != '3-137':
            return 'Explicit oesophageal question'
        if 'pharynx' in ctx.regions and code != '3-134':
            return 'Explicit pharyngeal question'
    if group == 'nuclear-imaging':
        mapping = {'salivary': '3-707.0', 'oesophagus': '3-707.1', 'stomach': '3-707.2', 'intestine': '3-707.3', 'colon': '3-707.4'}
        fixed = {mapping[r] for r in ctx.regions if r in mapping}
        if fixed and (len(fixed) != 1 or code not in fixed):
            return 'Nuclear study organ determines target'
        if not fixed and code != '3-70x':
            return 'Unknown organ uses other scintigraphy'
    if code == '8-831.00' and not ctx.matches(r'central|intensive|surgery|surgical|operation') and ctx.encounter.get('class', {}).get('code') != 'IMP':
        return 'Central access lacks a compatible setting'
    if group == 'cannulation':
        if ctx.matches(r'peripheral') and code != '8-831.03':
            return 'Known peripheral route'
        if ctx.matches(r'central venous') and code != '8-831.00':
            return 'Known central route'
    if group == 'anaesthesia':
        fixed = {'intravenous': '8-900', 'inhalation': '8-901', 'balanced': '8-902'}
        for term, target in fixed.items():
            if has(ctx.source, term) and code != target:
                return 'Explicit anaesthesia technique'
        if code == '8-903' and has(ctx.history, r'\b(bypass|sternotomy|transplantation|colectomy|hysterectomy)'):
            return 'Sedation-only conflicts with major operation'
    if code == '1-931.1' and not ctx.matches(r'\b(culture|bacteria|pneumoniae|gonorr|tuberculos|streptococc|staphylococc)'):
        return 'Resistance analysis not supported by this organism/assay'
    if group == 'social-consultation':
        if ctx.matches('domestic abuse') and code != '9-401.10':
            return 'Family topic determines counselling'
    if group == 'cardiovascular-exercise':
        if has(ctx.source, 'new york heart association') and code != '1-715':
            return 'Functional grading uses walk test'
    if group == 'mental-assessment' and has(ctx.source, 'questionnaire|screening|score') and code != '1-902.0':
        return 'Explicit questionnaire favours test diagnostics'
    for guard in rule['hardGuards']:
        reason = guard_reason(guard, ctx, candidate)
        if reason:
            return guard + ': ' + reason
    if window(ctx, candidate) is None:
        return 'Missing or incompatible procedure/encounter time window'
    return None


def guard_reason(guard, ctx, candidate):
    """Every configured guard has explicit executable semantics; unknown guards fail."""
    if guard not in SUPPORTED_GUARDS or guard not in DATA['guardDefinitions']:
        raise ValueError('Unknown OPS guard: ' + guard)
    if guard in ('pregnant-female-source', 'female-reproductive-context'):
        if ctx.sex != 'female' or ctx.age is None or not 12 <= ctx.age <= 55:
            return 'Compatible female source sex and age required'
        if has(ctx.history, r'hysterectomy|absence of uterus'):
            return 'Reproductive anatomy conflict'
        if guard == 'pregnant-female-source' and not ctx.matches(r'pregnan|fetal|foetal|antenatal|delivery|parturition|\blabor\b|birth|termination'):
            return 'Pregnancy context absent'
    if guard == 'maternal-delivery-episode':
        if not ctx.matches(r'delivery|parturition|birth|\blabor\b') or (ctx.age is not None and ctx.age < 12):
            return 'Maternal birth episode absent'
        if has(ctx.history, r'cesarean|caesarean'):
            return 'Operative birth conflicts with nonoperative supervision'
    if guard == 'contraception-context' and has(ctx.clinical, r'pregnan|antenatal'):
        return 'Active pregnancy conflicts with contraception procedure'
    if guard == 'ectopic-pregnancy-context' and not ctx.matches(r'ectopic|tubal pregnancy'):
        return 'Ectopic pregnancy absent'
    if guard == 'gestational-stage-compatible' and ctx.matches(r'fetal viability'):
        if has(ctx.clinical, r'first trimester|early pregnancy|gestation period, [1-9] weeks'):
            return 'Early viability assessment is not an anomaly work-up'
    if guard == 'palliative-context' and not ctx.matches(r'palliat|hospice|terminal illness'):
        return 'Palliative context absent'
    if guard == 'renal-transplant-context' and not ctx.matches(r'kidney|renal'):
        return 'Renal transplant topic missing'
    if guard == 'hiv-context-required' and not ctx.matches(r'human immunodeficiency|\bhiv\b|cd4'):
        return 'HIV follow-up missing'
    if guard == 'solid-tumour-context' and not ctx.matches(r'epidermal growth factor receptor 2|her2|carcinoma|breast cancer|malignant'):
        return 'Solid tumour work-up missing'
    if guard == 'allergy-topic-required' and not ctx.matches(r'allerg'):
        return 'Allergy topic missing'
    if guard == 'pathogen-required' and not ctx.matches(r'culture|microb|virus|viral|hepatitis|infection|infectious|pneumoniae|tuberculos|gonorr|syphilis|rubella|varicella|toxoplasma|chlamydia|sputum'):
        return 'Pathogen/infection question missing'
    if guard == 'cast-region-compatible' and (ctx.regions.intersection({'jaw', 'rib', 'hip', 'head'}) or not ctx.regions.intersection({'hand', 'forearm', 'upper-arm', 'thigh', 'foot'})):
        return 'Compatible limb region required'
    if guard == 'skin-incision-context' and not ctx.matches(r'skin|subcut|cutaneous|wound|abscess'):
        return 'Skin/subcutaneous incision not established'
    if guard == 'associated-operation-required' and not any(has(p, r'operation|surgery|surgical|ectomy|repair|replacement') and not has(p, r'reexploration|reoperation') for p in ctx.prior + [ctx.source]):
        return 'Associated operation missing'
    if guard == 'knee-injury-context' and not ctx.matches(r'knee'):
        return 'Knee context missing'
    if guard == 'wound-present' and not ctx.matches(r'wound|ulcer|laceration|burn|pressure sore'):
        return 'Wound evidence missing'
    if guard == 'dentition-compatible':
        if ctx.age is None or ctx.age < 2:
            return 'Dentition age missing or incompatible'
        if candidate['code'] == '5-249.4' and ctx.matches(r'fixed appliance'):
            return 'Fixed appliance cannot become removable'
    if guard == 'developmental-method-age-compatible' and (ctx.age is None or not 4 <= ctx.age <= 18):
        return 'School-age developmental context required'
    if guard == 'cooperative-test-feasible' and (ctx.age is None or ctx.age < 6 or ctx.matches(r'coma\b|unconscious|acute respiratory failure|quadriplegi|tetraplegi')):
        return 'Cooperative examination is incompatible'
    if guard == 'access-route-compatible' and ctx.matches(r'\barterial\b|into artery'):
        return 'Arterial route cannot become venous'
    if guard == 'contrast-compatible' and ctx.matches(r'contrast (allergy|contraindication)|allergy to contrast'):
        return 'Contrast contraindication'
    if guard == 'metabolic-challenge-compatible' and ctx.matches(r'ketoacidosis|hypoglycaemic coma|hypoglycemic coma'):
        return 'Acute metabolic conflict'
    if guard == 'anatomical-region-compatible' and ctx.matches(r'absent|amputat'):
        return 'Known organ/limb absence requires specific review'
    # These constraints are enforced by the shared context, candidate selection,
    # immutable source representation, time planner or final deduplication step.
    return None


def apply_contextual(decisions, contexts):
    for identifier in sorted(decisions):
        d, ctx = decisions[identifier], contexts[identifier]
        r = ctx.event
        if d['status'] == 'context-conflict':
            continue
        entry = ENTRIES.get(d['source'].get('code'), {})
        if d['status'] == 'not-assessed' or not entry.get('contextualRule'):
            continue
        if r.get('status') != 'completed':
            d['contextualFallback'] = 'Synthetic services require a completed source event'
            continue
        rule = RULES[entry['contextualRule']]
        override = entry.get('contextualOverride', {})
        rejected, eligible = [], []
        for c in rule['candidates']:
            reason = candidate_reason(ctx, rule, c, override)
            if reason:
                rejected.append({'id': c['id'], 'code': c['code'], 'reason': reason})
            else:
                eligible.append(c)
        d['contextualSelection'] = {'ruleId': rule['id'], 'ruleRevision': rule['revision'],
                                    'algorithmId': ALGORITHM, 'rejectedCandidates': rejected,
                                    'eligibleCandidates': [{'id': c['id'], 'code': c['code'], 'weight': c['weight']} for c in sorted(eligible, key=lambda c: c['id'])],
                                    'assumptions': [rule['assumption']], 'context': ctx.facts(),
                                    'relationship': override.get('mode', rule['mode']),
                                    'originalTimes': {'start': begin(r), 'end': r.get('performedPeriod', {}).get('end', '')}}
        if not eligible:
            d['contextualFallback'] = 'No compatible OPS candidate; source retained'
            continue
        if override.get('emitTogether'):
            if {c['code'] for c in eligible} != set(override['emitTogether']):
                d['contextualFallback'] = 'All explicit source regions must be representable together'
                continue
            selected = sorted(eligible, key=lambda c: c['id'])
            selection = {'algorithmId': ALGORITHM, 'method': 'explicit-regional-split'}
        else:
            c, selection = choose(ctx.patient_id, identifier, d['source'], rule['id'], eligible)
            selected = [c]
        d['contextualSelection'].update(selection=selection, selectedCandidates=[c['id'] for c in selected])
        companion = override.get('mode', rule['mode']) == 'synthetic-companion'
        original = copy.deepcopy(d['outputs']) if companion else []
        outputs = []
        for c in selected:
            periods = window(ctx, c)
            if c.get('syntheticCourse'):
                d['contextualSelection']['syntheticSchedule'] = [
                    {'start': (ctx.start + timedelta(days=n)).isoformat(),
                     'end': (ctx.start + timedelta(days=n, minutes=c['syntheticCourse']['minutesPerUnit'])).isoformat()}
                    for n in range(c['syntheticCourse']['unitsPerWeek'])]
            for first, last in periods:
                outputs.append({'sourceIds': [identifier], 'codings': [coding(c)], 'label': c['display'],
                                'start': first.isoformat(), 'end': last.isoformat() if last != first else '',
                                'status': 'completed', 'synthetic': True, 'ruleId': rule['id'],
                                'relationship': override.get('mode', rule['mode']),
                                'deduplicationKey': [ctx.patient_id, ctx.encounter.get('id') or identifier, c['code'],
                                                     '' if once_per_stay(c['code']) else first.isoformat()]})
        d.update(status='synthetic-contextual', target=coding(selected[0]) if len(selected) == 1 and not companion else None,
                 outputs=original + outputs, reason=rule['assumption'])
    deduplicate(decisions)
    return decisions


def once_per_stay(code):
    return code.startswith(('8-02', '8-56', '8-93', '9-401', '9-500', '9-26', '8-831'))


def deduplicate(decisions):
    owners = {}
    # Prefer an already represented source-supported OPS in the same episode.
    for identifier in sorted(decisions):
        d = decisions[identifier]
        ctx = d.get('contextEvidence', {})
        for o in d['outputs']:
            if o.get('synthetic') or not o['codings'] or o['codings'][0].get('system') != OPS:
                continue
            code = o['codings'][0]['code']
            key = (ctx.get('patientId'), ctx.get('encounterId') or identifier, code,
                   '' if once_per_stay(code) else instant(o['start']) or o['start'])
            owners.setdefault(key, (identifier, o))
    for identifier in sorted(decisions):
        decision = decisions[identifier]
        kept = []
        for o in decision['outputs']:
            key = o.get('deduplicationKey')
            if key is None:
                kept.append(o)
                continue
            key = tuple(key[:-1]) + (instant(key[-1]) or key[-1],)
            if key in owners:
                owner_id, owner = owners[key]
                owner['sourceIds'] = sorted(set(owner['sourceIds'] + o['sourceIds']))
                decision.setdefault('sharedOutputs', []).append({'ownerSourceId': owner_id, 'code': o['codings'][0]['code']})
            else:
                owners[key] = (identifier, o)
                kept.append(o)
        decision['outputs'] = kept


def summary(decisions):
    counts = {'sourceEvents': len(decisions), 'withOps': 0, 'companionOnly': 0,
              'contextConflicts': 0, 'sourceRetained': 0, 'opsRows': 0,
              'assessedConcepts': len(ENTRIES), 'contextualConcepts': sum('contextualRule' in e for e in ENTRIES.values())}
    for d in decisions.values():
        ops = [o for o in d['outputs'] if any(c.get('system') == OPS for c in o['codings'])]
        covered = bool(ops or d.get('sharedOutputs') or d.get('chemotherapyBlock'))
        counts['withOps'] += covered
        counts['companionOnly'] += covered and d.get('contextualSelection', {}).get('relationship') == 'synthetic-companion'
        counts['contextConflicts'] += d['status'] == 'context-conflict'
        counts['sourceRetained'] += bool(d['outputs']) and not covered
        counts['opsRows'] += len(ops)
    return counts
