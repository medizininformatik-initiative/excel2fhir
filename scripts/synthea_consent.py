"""Optional, reproducible consent enrichment for generated Synthea patients."""
import hashlib
from datetime import date

VERSION = 'synthea-consent-v1'
GROUPS = ('PDAT Einwilligung', 'KKDAT retro Einwilligung', 'KKDAT Einwilligung',
          'BIOMAT Einwilligung', 'BIOMAT Zusatz Einwilligung')
VARIANTS = ('full', 'patient-data', 'patient-and-insurance', 'deny', 'absent')
WEIGHTS = (50, 15, 15, 10, 10)
VALUES = ((True, True, True, True, True), (True, False, False, False, False),
          (True, True, True, False, False), (False, False, False, False, False), None)


def assignments(patient_ids, seed):
    """Largest-remainder quotas; hash ranking avoids dependence on file order."""
    ids = list(patient_ids)
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate patient identity in consent assignment')
    counts = [len(ids) * weight // 100 for weight in WEIGHTS]
    remainder = len(ids) - sum(counts)
    order = sorted(range(5), key=lambda i: (-(len(ids) * WEIGHTS[i] % 100), i))
    for i in order[:remainder]:
        counts[i] += 1
    ranked = sorted(ids, key=lambda pid: (hashlib.sha256(f'{VERSION}|{seed}|{pid}'.encode()).digest(), pid))
    result = {}
    start = 0
    for variant, count in zip(VARIANTS, counts):
        result.update((pid, variant) for pid in ranked[start:start + count])
        start += count
    return result


def history_window(end_date, years_of_history):
    end = date.fromisoformat(end_date) if end_date else None
    if years_of_history < 0 or (years_of_history and end is None):
        raise ValueError('Consent history requires a simulation end date and nonnegative years')
    start = None
    if years_of_history:
        try:
            start = end.replace(year=end.year - years_of_history)
        except ValueError:  # February 29 in a non-leap target year.
            start = end.replace(year=end.year - years_of_history, day=28)
    return start, end


def enrich(rows, bundle, variant, seed, *, end_date=None, years_of_history=0):
    person = rows['Person'][0]
    if any(person.get(key) for key in ('Datum Einwilligung', *GROUPS)):
        return {'version': VERSION, 'source': 'preserved'}
    record = {'version': VERSION, 'seed': str(seed), 'variant': variant, 'synthetic': True}
    values = VALUES[VARIANTS.index(variant)]
    if values is None:
        return record
    history_start, history_end = history_window(end_date, years_of_history)
    record.update(datePolicy='first-contact-within-history-v1',
                  historyStart=history_start.isoformat() if history_start else None,
                  historyEnd=history_end.isoformat() if history_end else None)
    patient = next((e['resource'] for e in bundle.get('entry', [])
                    if e.get('resource', {}).get('resourceType') == 'Patient'), {})
    birth = date.fromisoformat(patient['birthDate']) if patient.get('birthDate') else None
    death = date.fromisoformat(patient['deceasedDateTime'][:10]) if patient.get('deceasedDateTime') else None
    lower = max(filter(None, (history_start, birth)), default=None)
    upper = min(filter(None, (history_end, death)), default=None)
    if lower and upper and lower > upper:
        return {**record, 'dateSource': 'none', 'reason': 'no-lifetime-within-history'}
    encounters = [e['resource'] for e in bundle.get('entry', [])
                  if e.get('resource', {}).get('resourceType') == 'Encounter'
                  and e['resource'].get('period', {}).get('start')]
    encounters = [e for e in encounters
                  if (lower is None or date.fromisoformat(e['period']['start'][:10]) >= lower)
                  and (upper is None or date.fromisoformat(e['period']['start'][:10]) <= upper)]
    inpatient = [e for e in encounters if e.get('class', {}).get('code') in ('IMP', 'SS')]
    dates = [e['period']['start'][:10] for e in inpatient or encounters]
    if dates:
        consent_date = min(dates)
        source = 'first-inpatient-encounter' if inpatient else 'first-encounter'
    else:
        # Consent can be documented without an encounter, but never outside the known lifetime.
        fallback = lower if history_start else upper
        if fallback is None or (patient.get('deceasedBoolean') is True and death is None):
            return {**record, 'dateSource': 'none', 'reason': 'no-plausible-date'}
        consent_date = fallback.isoformat()
        source = 'synthetic-date-within-history-and-lifetime'
    person['Datum Einwilligung'] = consent_date
    person.update({key: 'ja' if permitted else 'nein' for key, permitted in zip(GROUPS, values)})
    return {**record, 'date': consent_date, 'dateSource': source}
