import io
import json
from pathlib import Path
import shutil
from unittest.mock import Mock, patch
import zipfile

from fastapi.testclient import TestClient
from api import app
import datasets
import inputs
import run_cleanup
import run_inputs
import run_replay
import store
import worker
from test_queue import QueueFixture


class ReplayTests(QueueFixture):
    def generated(self):
        job = store.create('starter', 'default')
        root = store.ROOT / 'jobs' / job
        snapshot = root / 'snapshot.json'
        snapshot.chmod(0o644)
        value = json.loads(snapshot.read_text())
        value['inputKind'] = 'synthea'
        snapshot.write_text(json.dumps(value))
        (root / 'input.xlsx').unlink()
        books = root / 'output/run-test/excel'
        books.mkdir(parents=True)
        template = Path(__file__).resolve().parents[2] / 'input/FHIR_Testdatengenerator_Vorlage.xlsx'
        for name in ('a.xlsx', 'b.xlsx'):
            shutil.copyfile(template, books / name)
        store.finish(job, 'succeeded', 0)
        (root / 'datasets.json').write_text(json.dumps({'datasets': [], 'artifacts': []}))
        return job, root

    def test_individual_workbook_download(self):
        job, root = self.generated()
        source = run_replay.editor(job)['source']
        identifier = source.removeprefix('upload:')
        client = TestClient(app)
        url = f'/api/inputs/{identifier}/workbooks'
        response = client.get(url + '/0')
        self.assertEqual(200, response.status_code)
        self.assertEqual((root / 'output/run-test/excel/a.xlsx').read_bytes(), response.content)
        self.assertIn("filename*=UTF-8''a.xlsx", response.headers['content-disposition'])
        for index in (-1, 2):
            self.assertEqual(404, client.get(url + f'/{index}').status_code)
        inputs.delete(identifier)
        self.assertEqual(404, client.get(url + '/0').status_code)

    def test_whole_set_survives_original_deletion_and_supports_independent_configs(self):
        job, root = self.generated()
        original = {p.name: p.read_bytes() for p in run_replay.workbooks(root, {'inputKind': 'synthea'})}
        run_cleanup.cleanup(job)
        client = TestClient(app)
        response = client.post(f'/api/jobs/{job}/excel-editor')
        self.assertEqual(200, response.status_code, response.text)
        loaded = response.json()
        self.assertEqual(2, loaded['workbookCount'])
        self.assertNotIn('generation', loaded)
        self.assertEqual(loaded['source'], run_replay.editor(job)['source'])
        with patch.object(store, 'validate_configuration', return_value={'formats': ['JSON'], 'validation': False}):
            first = store.create(loaded['source'], 'default', 'PID_PREFIX=DIZ-A-', dataset_name='DIZ A')
            second = store.create(loaded['source'], 'default', 'PID_PREFIX=DIZ-B-', dataset_name='DIZ B')
        store.delete(job)
        for identifier, prefix in [(first, 'DIZ-A-'), (second, 'DIZ-B-')]:
            saved = store.ROOT / 'jobs' / identifier
            self.assertEqual('PID_PREFIX=' + prefix, (saved / 'default.config').read_text())
            self.assertEqual(original, {p.name: p.read_bytes() for p in (saved / 'workbooks').glob('*.xlsx')})
            snapshot = json.loads((saved / 'snapshot.json').read_text())
            self.assertEqual('Europe/Berlin', snapshot['clinicalTimeZone'])
            self.assertEqual(job, snapshot['repeatedFrom']['id'])
        process = Mock(returncode=0)
        process.poll.return_value = 0
        with patch.object(worker.subprocess, 'Popen', return_value=process) as launch:
            worker.execute(first)
        command = launch.call_args.args[0]
        self.assertEqual('java', command[0])
        self.assertIn('-i', command)
        self.assertIn('-Duser.timezone=Europe/Berlin', command)
        self.assertFalse(any('synthea' in arg for arg in command))
        self.assertEqual('succeeded', store.get(first)['state'])
        compact = run_inputs.archive(first)
        self.addCleanup(compact.unlink, missing_ok=True)
        with zipfile.ZipFile(compact) as archive:
            self.assertIn('workbooks/a.xlsx', archive.namelist())
            self.assertIn('workbooks/b.xlsx', archive.namelist())
        run_cleanup.cleanup(first)
        self.assertEqual(2, run_replay.editor(first)['workbookCount'])

    def test_missing_workbooks_active_runs_and_tampering_are_rejected(self):
        active = store.create('starter', 'default')
        with self.assertRaises(store.SubmissionConflict):
            run_replay.editor(active)
        job, root = self.generated()
        loaded = run_replay.editor(job)
        path, _, _ = inputs.resolve(loaded['source'])
        book = path.parent / 'workbooks/a.xlsx'
        book.chmod(0o644)
        book.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            store.create(loaded['source'], 'default')
        for book in (root / 'output/run-test/excel').glob('*.xlsx'):
            book.unlink()
        with self.assertRaises(store.SubmissionConflict):
            run_replay.editor(job)

    def test_removed_saved_set_does_not_affect_jobs_and_can_be_recreated(self):
        job, _ = self.generated()
        loaded = run_replay.editor(job)
        repeat = store.create(loaded['source'], 'default')
        inputs.delete(loaded['source'].removeprefix('upload:'))
        self.assertEqual([], inputs.summaries())
        root = store.ROOT / 'jobs' / repeat
        run_replay.verify(root, json.loads((root / 'workbooks.json').read_text()))
        restored = run_replay.editor(job)
        inputs.resolve(restored['source'])
