"""Exercise Linux ownership across the container/host user boundary."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(sys.platform == 'linux' and os.geteuid() == 0,
                     'requires a Linux container started as root')
class ContainerOutputTest(unittest.TestCase):
    def test_output_owner_can_read_edit_and_remove_private_run_files(self):
        scripts = str(Path(__file__).resolve().parents[1])
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            parent.chmod(0o755)
            output = parent / 'output'
            output.mkdir()
            os.chown(output, 12345, 23456)
            subprocess.run([sys.executable, '-c', '''
import os, pathlib, sys, tempfile
sys.path.insert(0, sys.argv[1])
from run_synthea_container import use_output_owner
use_output_owner(sys.argv[2])
assert (os.getuid(), os.getgid()) == (12345, 23456)
assert os.getgroups() == []
run = pathlib.Path(tempfile.mkdtemp(dir=sys.argv[2], prefix='run-'))
(run / 'workflow.json').write_text('{}')
(run / 'Fall.xlsx').write_bytes(b'workbook')
''', scripts, str(output)], check=True)
            run = next(output.iterdir())
            self.assertEqual(run.stat().st_mode & 0o777, 0o700)
            self.assertEqual((run / 'Fall.xlsx').stat().st_uid, 12345)
            subprocess.run([sys.executable, '-c', '''
import os, pathlib, sys
os.setgroups([])
os.setgid(23456)
os.setuid(12345)
run = pathlib.Path(sys.argv[1])
assert (run / 'workflow.json').read_text() == '{}'
(run / 'Fall.xlsx').write_bytes(b'edited')
for path in run.iterdir(): path.unlink()
run.rmdir()
''', str(run)], check=True)
