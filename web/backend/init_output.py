"""Prepare host output access before starting the unprivileged services."""
import os
from pathlib import Path
import subprocess


def writable(path, uid=10001):
    # Check effective access as the service user, including Docker Desktop shares.
    pid = os.fork()
    if pid == 0:
        try:
            os.setgroups([])
            os.setgid(uid)
            os.setuid(uid)
            probe = path / f'.workbench-access-{os.getpid()}'
            with probe.open('x') as stream:
                stream.write('test')
            probe.unlink()
        except OSError:
            os._exit(1)
        os._exit(0)
    return os.waitpid(pid, 0)[1] == 0


def prepare(path, owner):
    path = Path(path)
    # Docker creates missing bind directories as root. Match the checkout owner.
    if path.stat().st_uid == 0 and owner.st_uid != 0:
        os.chown(path, owner.st_uid, owner.st_gid)
    if not writable(path):
        # Grant only the service user access; retain the host owner's permissions.
        subprocess.run(['setfacl', '-m',
                        f'u:10001:rwx,d:u:10001:rwx,d:u:{owner.st_uid}:rwx', str(path)], check=True)
    if not writable(path):
        raise RuntimeError(f'Workbench cannot write {path}')


if __name__ == '__main__':
    owner = Path('/host-project').stat()
    prepare('/global-output', owner)
    prepare('/mass-output', owner)
