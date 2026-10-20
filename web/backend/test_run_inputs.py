import hashlib
import io
import json
import zipfile
from unittest.mock import patch

from fastapi.testclient import TestClient
from api import app
import run_inputs
import store
from test_queue import QueueFixture


class RunInputsTests(QueueFixture):
    def test_download_preserves_inputs_excludes_outputs_and_removes_temporary_archive(self):
        job = store.create('starter', 'default')
        root = store.ROOT / 'jobs' / job
        original = {name: (root / name).read_bytes() for name in ('input.xlsx', 'default.config', 'snapshot.json')}
        output = root / 'output/run-test/fhir'
        output.mkdir(parents=True)
        (output / 'patient.ndjson').write_text('result')
        (root / 'converter.log').write_text('log')
        client = TestClient(app)
        self.assertEqual(409, client.get(f'/api/jobs/{job}/inputs-download').status_code)
        store.finish(job, 'succeeded', 0)
        paths = []
        build = run_inputs.archive
        def capture(identifier):
            path = build(identifier)
            paths.append(path)
            return path
        with patch.object(run_inputs, 'archive', side_effect=capture):
            response = client.get(f'/api/jobs/{job}/inputs-download')
        self.assertEqual(200, response.status_code)
        self.assertFalse(paths[0].exists())
        with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
            self.assertEqual(set(original) | {'README.txt', 'manifest.json'}, set(archive.namelist()))
            manifest = json.loads(archive.read('manifest.json'))
            for item in manifest['files']:
                self.assertEqual(hashlib.sha256(archive.read(item['path'])).hexdigest(), item['sha256'])
            for name, data in original.items():
                self.assertEqual(data, archive.read(name))
                self.assertEqual(data, (root / name).read_bytes())

    def test_failed_synthea_run_downloads_available_workbooks_and_effective_options(self):
        job = store.create('starter', 'default')
        root = store.ROOT / 'jobs' / job
        snapshot = root / 'snapshot.json'
        snapshot.chmod(0o644)
        value = json.loads(snapshot.read_text())
        value['inputKind'] = 'synthea'
        snapshot.write_text(json.dumps(value))
        book = root / 'output/run-import/excel/Case.xlsx'
        book.parent.mkdir(parents=True)
        book.write_bytes(b'generated workbook')
        config = root / 'output/run-import/details/cases/Case/run-excel/details/options/Case.xlsx/default/Converter_Options.config'
        config.parent.mkdir(parents=True)
        config.write_text('effective options')
        store.finish(job, 'failed', 1)
        path = run_inputs.archive(job)
        self.addCleanup(path.unlink, missing_ok=True)
        with zipfile.ZipFile(path) as archive:
            self.assertIn(book.relative_to(root).as_posix(), archive.namelist())
            self.assertIn(config.relative_to(root).as_posix(), archive.namelist())
            self.assertNotIn('input.xlsx', archive.namelist())
        book.unlink()
        with self.assertRaises(store.SubmissionConflict):
            run_inputs.archive(job)
        book.symlink_to(store.APP / 'synthifhir.jar')
        with self.assertRaises(store.SubmissionConflict):
            run_inputs.archive(job)
