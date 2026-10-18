"""Admission check for bulk generation; no checks in the conversion hot path."""
from pathlib import Path

REQUIRED = 3 * 1024**3
MEMINFO = Path('/proc/meminfo')
CGROUP = Path('/sys/fs/cgroup')


def status():
    try:
        values = dict(line.split(':', 1) for line in MEMINFO.read_text().splitlines())
        available = int(values['MemAvailable'].split()[0]) * 1024
        if available < 0:
            raise ValueError('Invalid memory value')
    except (OSError, KeyError, ValueError, IndexError):
        available = None
    return {'availableBytes': available, 'requiredBytes': REQUIRED,
            'ready': available is not None and available >= REQUIRED}


def require_available(*, container=False):
    memory = status()
    if not memory['ready']:
        available = 'unknown' if memory['availableBytes'] is None else f"{memory['availableBytes'] / 1024**3:.2f} GiB"
        raise ValueError(f'Bulk generation requires 3 GiB available Docker RAM (available: {available}). '
                         'Run bash web/prepare-bulk.sh from the project directory before starting.')
    if container:
        try:
            limit = (CGROUP / 'memory.max').read_text().strip()
            current = int((CGROUP / 'memory.current').read_text())
            stats = dict(line.split() for line in (CGROUP / 'memory.stat').read_text().splitlines())
            # Inactive file cache is reclaimable; include the existing worker in the budget.
            headroom = int(limit) - current + int(stats['inactive_file']) if limit != 'max' else REQUIRED
        except (OSError, KeyError, ValueError):
            raise ValueError('Cannot determine the bulk worker memory limit; check the Docker deployment.')
        if headroom < REQUIRED:
            raise ValueError('Bulk worker requires at least 3 GiB memory headroom. Check its Docker memory limit.')


def require_idle():
    import store
    with store.connect() as db:
        jobs = db.execute("SELECT 1 FROM jobs WHERE state IN ('queued','running') LIMIT 1").fetchone()
        uploads = db.execute("SELECT 1 FROM uploads WHERE state IN ('queued','preparing','uploading') LIMIT 1").fetchone()
    if jobs or uploads:
        raise ValueError('Wait for queued/running generations and FHIR transfers to finish before changing services.')
