"""Deterministic subruns and seed ranges within one sequential bulk job."""
CHUNK_SIZE = 100
PACKAGE_SIZE = 1000


def seed_at(seed, index):
    return str((int(seed) + index + 2**63) % 2**64 - 2**63)


def plan(settings, source_offset=0):
    total = settings['population']
    per_run = settings.get('patientsPerRun', 10000)
    return [{'number': i + 1, 'start': start, 'total': min(per_run, total - start),
             'firstSeed': seed_at(settings['patientSeed'], (source_offset + start) // CHUNK_SIZE),
             'lastSeed': seed_at(settings['patientSeed'], (source_offset + min(start + per_run, total) - 1) // CHUNK_SIZE)}
            for i, start in enumerate(range(0, total, per_run))]


def subruns(settings, progress, state, source_offset=0):
    completed = progress.get('completed', 0)
    active = progress.get('run', 1)
    finished = progress.get('finishedRuns', 0)
    result = []
    for run in plan(settings, source_offset):
        count = min(run['total'], max(0, completed - run['start']))
        status = 'succeeded' if run['number'] <= finished else (
            state if run['number'] == active and state != 'succeeded' else 'queued')
        result.append({**run, 'completed': count, 'state': status})
    return result


def work_progress(total, completed, phase, done, stage_total, *, native=False,
                  packaged=0, package_patients=0, grouped=True):
    """Equal patient-weighted stages; completion includes final result publication."""
    phases = ['generating', 'compressing'] if native else ['generating', 'mapping', 'converting']
    maximum = total * (len(phases) + int(grouped)) + 1
    if phase == 'complete':
        return maximum, maximum
    fraction = min(1, max(0, done / stage_total)) if stage_total else 0
    current = completed * len(phases) + packaged
    if phase in phases:
        size = min(stage_total, max(0, total - completed))
        current += size * (phases.index(phase) + fraction)
    elif phase == 'packaging':
        current += package_patients * fraction
    return min(maximum - 1, max(0, current)), maximum
