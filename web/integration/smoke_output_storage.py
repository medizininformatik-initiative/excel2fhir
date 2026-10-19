"""Exercise Linux ownership, default ACLs and standard exports in an isolated worker."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from unittest.mock import patch
import zipfile

from init_output import prepare, writable
import result_exports
import store
import worker


with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    root.chmod(0o755)
    checkout = root / 'checkout'
    checkout.mkdir()
    os.chown(checkout, 12345, 12345)
    output = root / 'outputGlobal'
    output.mkdir(mode=0o755)  # Reproduce Docker's root-owned bind directory.
    assert not writable(output)
    prepare(output, checkout.stat())
    prepare(output, checkout.stat())  # Repeated startup is safe.
    assert output.stat().st_uid == 12345
    assert writable(output)
    assert output.stat().st_mode & 0o002 == 0, 'Output must not be world-writable'
    data = root / 'data'
    data.mkdir()
    os.chown(data, 10001, 10001)
    script = '''
import json
import store, worker
job = store.create('starter', 'default')
assert store.claim() == job
worker.execute(job)
result = store.get(job)
assert result['state'] == 'succeeded', result
print(job)
'''
    environment = {**os.environ, 'WORKBENCH_DATA': str(data), 'WORKBENCH_OUTPUT': str(output),
                   'PYTHONPATH': '/app'}
    def service_user():
        os.setgroups([])
        os.setgid(10001)
        os.setuid(10001)
    job = subprocess.check_output(['python', '-c', script], env=environment,
                                  preexec_fn=service_user, text=True).strip().splitlines()[-1]
    folder = data / 'jobs' / job
    snapshot = json.loads((folder / 'snapshot.json').read_text())
    destination = output / snapshot['resultOutputName']
    files = list(destination.iterdir())
    assert files and all(file.suffix == '.zip' for file in files), files
    for index, file in enumerate(sorted(files)):
        assert file.read_bytes() == (folder / f'dataset-{index}.zip').read_bytes()
        with zipfile.ZipFile(file) as archive:
            assert archive.namelist()
            assert all('details/' not in name for name in archive.namelist())
    # The host owner can manage files produced by the distinct container UID.
    def host_user():
        os.setgroups([])
        os.setgid(12345)
        os.setuid(12345)
    subprocess.run(['python', '-c',
                    'from pathlib import Path; import sys; p=Path(sys.argv[1]); '
                    'p.write_text("host access"); p.unlink()', str(destination / 'host-probe')],
                   preexec_fn=host_user, check=True)
    with patch.object(store, 'ROOT', data):
        store.delete(job)
    assert all(file.exists() for file in files), 'Deleting job metadata must retain exports'
    print('PASS: root-owned Linux output, service/host access, standard ZIP export and retention')
