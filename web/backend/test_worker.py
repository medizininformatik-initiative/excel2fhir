import os
from pathlib import Path
import subprocess
import sys
import time
import zipfile
from unittest.mock import Mock, patch

import store
import worker
from test_queue import QueueFixture


class WorkerTests(QueueFixture):
    def test_workbook_source_runs_without_external_options(self):
        job = store.create('starter', 'workbook')
        store.claim()
        process = Mock(returncode=0)
        process.poll.return_value = 0
        with patch.object(worker.subprocess, 'Popen', return_value=process) as launch:
            worker.execute(job)
        self.assertNotIn('--converter-options', launch.call_args.args[0])
        self.assertEqual('succeeded', store.get(job)['state'])
        self.assertFalse((store.ROOT / 'jobs' / job / 'default.config').exists())

    def test_failed_validation_keeps_downloadable_reports(self):
        job = store.create('starter', 'default')
        store.claim()
        folder = store.ROOT / 'jobs' / job
        (folder / 'output').mkdir()
        (folder / 'output' / 'status.txt').write_text('NOT_CHECKED')
        process = Mock(returncode=1)
        process.poll.return_value = 1
        with patch.object(worker.subprocess, 'Popen', return_value=process):
            worker.execute(job)
        self.assertEqual('failed', store.get(job)['state'])
        self.assertTrue(store.get(job)['download_available'])
        with zipfile.ZipFile(folder / 'result.zip') as archive:
            self.assertEqual(b'NOT_CHECKED', archive.read('output/status.txt'))
            self.assertIn('snapshot.json', archive.namelist())

    def test_rejects_converter_change_before_execution(self):
        job = store.create('starter', 'default')
        store.claim()
        (store.APP / 'excel2fhir.jar').write_bytes(b'changed converter')
        worker.execute(job)
        self.assertEqual('failed', store.get(job)['state'])
        log = (store.ROOT / 'jobs' / job / 'converter.log').read_text()
        self.assertIn('Converter image changed', log)

    def test_restart_recovers_running_job_without_resuming_it(self):
        job = store.create('starter', 'default')
        store.claim()
        env = dict(os.environ, WORKBENCH_DATA=str(store.ROOT), CONVERTER_HOME=str(store.APP))
        process = subprocess.Popen([sys.executable, str(Path(worker.__file__))], env=env)
        try:
            for _ in range(100):
                if store.get(job)['state'] == 'interrupted':
                    break
                time.sleep(0.02)
            self.assertEqual('interrupted', store.get(job)['state'])
            self.assertFalse((store.ROOT / 'jobs' / job / 'output').exists())
        finally:
            process.terminate()
            process.wait(timeout=5)

    def test_configuration_tampering_is_rejected_before_start(self):
        job = store.create('starter', 'default')
        store.claim()
        config = store.ROOT / 'jobs' / job / 'default.config'
        config.chmod(0o644)
        config.write_text('changed')
        worker.execute(job)
        self.assertEqual('failed', store.get(job)['state'])
        self.assertIn('Configuration snapshot has changed', (config.parent / 'converter.log').read_text())
