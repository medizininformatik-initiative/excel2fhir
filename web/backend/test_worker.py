import os
from pathlib import Path
import subprocess
import sys
import time

import store
import worker
from test_queue import QueueFixture


class WorkerTests(QueueFixture):
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
