"""Persist evidence of memory exhaustion without guessing from SIGKILL alone."""
import json
from pathlib import Path

EVENTS = Path('/sys/fs/cgroup/memory.events')


def oom_kills():
    try:
        return int(dict(line.split() for line in EVENTS.read_text().splitlines())['oom_kill'])
    except (OSError, ValueError, KeyError):
        return None


def record(folder, before, returncode=None):
    after = oom_kills()
    evidence = None
    if before is not None and after is not None and after > before:
        evidence = 'The operating system killed a process because memory was exhausted.'
    else:
        for path in [folder / 'converter.log', *(folder / 'output').rglob('*.log')]:
            if not path.is_file():
                continue
            with path.open(errors='replace') as log:
                if any('OutOfMemoryError' in line or line.startswith('MemoryError')
                       or 'Cannot allocate memory' in line
                       or 'insufficient memory for the Java Runtime Environment' in line for line in log):
                    evidence = 'Memory exhaustion reported in ' + str(path.relative_to(folder))
                    break
    if evidence:
        result = {'kind': 'memory', 'evidence': evidence}
    elif returncode in (-9, 137):
        result = {'kind': 'killed', 'evidence': 'Process was killed; memory exhaustion is not confirmed.'}
    else:
        return None
    (folder / 'failure.json').write_text(json.dumps(result))
    # Do not schedule another memory-intensive inspection of an incomplete run.
    manifest = folder / 'datasets.json'
    if not manifest.exists():
        manifest.write_text(json.dumps({'datasets': [], 'artifacts': [], 'error': result['evidence']}))
    return result
