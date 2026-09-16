#!/usr/bin/env python3
"""Run the generator as the owner of its mounted output directory."""
import os
from pathlib import Path
import sys
import tempfile


def use_output_owner(output):
    directory = Path(output).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    owner = directory.stat()
    if os.geteuid() == 0 and owner.st_uid != 0:
        os.setgroups([])
        os.setgid(owner.st_gid)
        os.setuid(owner.st_uid)
    # A host UID need not have a passwd entry or a home inside the image.
    os.environ['HOME'] = tempfile.mkdtemp(prefix='synthea-home-')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Usage: run_synthea_container.py OUTPUT_DIRECTORY [native Synthea arguments...]')
    use_output_owner(sys.argv[1])
    os.execv(sys.executable, [sys.executable, str(Path(__file__).with_name('run_synthea_workflow.py')),
                            *sys.argv[1:]])
