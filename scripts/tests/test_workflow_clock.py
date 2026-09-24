"""Run names use the system clock independently of clinical timestamps."""
from datetime import datetime
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from zoneinfo import ZoneInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import workflow_layout


class WorkflowClockTest(unittest.TestCase):
    def test_local_date_including_daylight_saving(self):
        for month in (1, 7):
            clock = datetime(2026, month, 23, 0, 15, 30, tzinfo=ZoneInfo('Europe/Berlin'))
            with self.subTest(month=month), tempfile.TemporaryDirectory() as output:
                with patch.object(workflow_layout, 'run_time', return_value=clock):
                    run = workflow_layout.create_run(output, 'synthea')
                    duplicate = workflow_layout.create_run(output, 'synthea')
                self.assertEqual(run.name, f'run-2026{month:02d}23_00-15-30-synthea')
                self.assertEqual(duplicate.name, run.name + '-2')

    def test_clinical_zone_does_not_change_run_clock(self):
        code = '''
import os, time
from workflow_layout import run_time
os.environ['TZ'] = 'Europe/Berlin'
time.tzset()
assert run_time().strftime('%z') == '+0545'
'''
        subprocess.run([sys.executable, '-c', code], check=True,
                       env={**os.environ, 'TZ': 'Asia/Kathmandu',
                            'PYTHONPATH': str(Path(workflow_layout.__file__).parent)})
