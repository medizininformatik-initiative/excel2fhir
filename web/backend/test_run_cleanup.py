import io
import json
from pathlib import Path
import shutil
import uuid
import zipfile
from unittest.mock import patch

from fastapi.testclient import TestClient
from api import app
import datasets
import fhir_uploads
import run_cleanup
import store
from test_queue import QueueFixture


class RunCleanupTests(QueueFixture):
    def setUp(self):
        super().setUp()
        shutil.copyfile(Path(__file__).resolve().parents[2] / 'input/FHIR_Testdatengenerator_Vorlage.xlsx',
                        store.APP / store.SOURCES['starter'])

    def completed(self):
        job = store.create('starter', 'default')
        root = store.ROOT / 'jobs' / job
        output = root / 'output/run-test/fhir'
        output.mkdir(parents=True)
        (output / 'Patient.ndjson').write_text('{"resourceType":"Patient","id":"test"}\n')
        (root / 'converter.log').write_text('diagnostics')
        extra = root / 'output/run-test/details/sources'
        extra.mkdir(parents=True)
        (extra / 'unused.json').write_text('{"unused":true}')
        (root / 'result.zip').write_bytes(b'redundant archive')
        with patch.object(datasets, 'inspect', return_value={'patients': 1, 'resourceCounts': {'Patient': 1}}):
            datasets.build(job, json.loads((root / 'snapshot.json').read_text()))
        store.finish(job, 'succeeded', 0)
        return job, root

    def test_cleanup_preserves_bytes_and_downloads_and_rebuilds_identical_dataset(self):
        job, root = self.completed()
        names = ['snapshot.json', 'default.config', 'input.xlsx', 'output/run-test/fhir/Patient.ndjson']
        original = {name: (root / name).read_bytes() for name in names}
        import run_outcomes
        outcomes = run_outcomes.save(root, json.loads((root / 'snapshot.json').read_text()), 'succeeded')
        original_zip = (root / 'dataset-0.zip').read_bytes()
        client = TestClient(app)
        response = client.post(f'/api/jobs/{job}/cleanup')
        self.assertEqual(200, response.status_code, response.text)
        self.assertGreater(response.json()['cleanup']['removedBytes'], 0)
        self.assertEqual(outcomes, response.json()['outcomes'])
        self.assertEqual(outcomes, json.loads((root / 'outcomes.json').read_text()))
        for name, data in original.items():
            self.assertEqual(data, (root / name).read_bytes())
        for name in ['converter.log', 'result.zip', 'dataset-0.zip', 'output/run-test/details/sources/unused.json']:
            self.assertFalse((root / name).exists(), name)
        self.assertEqual('', client.get(f'/api/jobs/{job}/logs').text)
        self.assertEqual(original_zip, client.get(f'/api/datasets/{job}.0/download').content)
        self.assertEqual(200, client.get(f'/api/jobs/{job}/inputs-download').status_code)
        with zipfile.ZipFile(io.BytesIO(client.get(f'/api/jobs/{job}/download').content)) as archive:
            self.assertIn('cleanup.json', archive.namelist())
            self.assertNotIn('converter.log', archive.namelist())
            for name, data in original.items():
                self.assertEqual(data, archive.read(name))
        with patch('inputs.publish', return_value={'id': 'saved-workbook'}):
            self.assertEqual('saved-workbook', store.editor_input(job)['source'])
        upload = fhir_uploads.create(str(uuid.uuid4()), 'blaze', [job + '.0'])
        self.assertEqual(store.digest(root / 'dataset-0.zip'), upload['descriptor']['datasets'][0]['sha256'])

    def test_csv_archive_and_referenced_files_survive_cleanup(self):
        job, root = self.completed()
        attachment = root / 'output/run-test/details/document.txt'
        attachment.write_text('document content')
        archive_path = root / 'output/run-test/csv.zip'
        with zipfile.ZipFile(archive_path, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('case_Dokument.csv', 'File\n' + str(attachment) + '\n')
        original = archive_path.read_bytes()
        run_cleanup.cleanup(job)
        self.assertEqual(original, archive_path.read_bytes())
        self.assertEqual('document content', attachment.read_text())

    def test_blocks_active_work_before_mutation(self):
        job, root = self.completed()
        with store.connect() as db:
            db.execute("UPDATE jobs SET state='running' WHERE id=?", (job,))
        with self.assertRaises(store.SubmissionConflict):
            run_cleanup.cleanup(job)
        self.assertTrue((root / 'result.zip').exists())
        with store.connect() as db:
            db.execute("UPDATE jobs SET state='succeeded' WHERE id=?", (job,))
            db.execute('INSERT INTO uploads(id,state,created,descriptor) VALUES (?,?,?,?)',
                       (str(uuid.uuid4()), 'queued', 1, json.dumps({'datasets': [{'jobId': job}]})))
        with self.assertRaises(store.SubmissionConflict):
            run_cleanup.cleanup(job)
        self.assertFalse((root / 'cleanup.json').exists())

    def test_dependency_and_missing_result_protection(self):
        job, root = self.completed()
        attachment = root / 'output/run-test/details/report.pdf'
        attachment.write_bytes(b'document')
        fhir = root / 'output/run-test/fhir/Patient.ndjson'
        fhir.write_text(json.dumps({'resourceType': 'DocumentReference', 'content': [{'attachment': {'url': str(attachment)}}]}))
        run_cleanup.cleanup(job)
        self.assertEqual(b'document', attachment.read_bytes())
        self.assertIn(attachment.relative_to(root).as_posix(), [item['path'] for item in json.loads((root / 'cleanup.json').read_text())['files']])
        fhir.unlink()
        (root / 'converter.log').write_text('leave intact')
        with self.assertRaises(store.SubmissionConflict):
            run_cleanup.cleanup(job)
        self.assertTrue((root / 'converter.log').exists())

    def test_synthea_sources_removed_generated_excel_and_options_retained(self):
        job, root = self.completed()
        snapshot = root / 'snapshot.json'
        snapshot.chmod(0o644)
        value = json.loads(snapshot.read_text())
        value['inputKind'] = 'synthea'
        snapshot.write_text(json.dumps(value))
        book = root / 'output/run-test/excel/patient.xlsx'
        book.parent.mkdir()
        (root / 'input.xlsx').rename(book)
        (root / 'input.json').write_text('{}')
        options = root / 'output/run-test/details/options/test.config'
        options.parent.mkdir()
        options.write_text('CHECK_INPUT_CONSISTENCY=true')
        run_cleanup.cleanup(job)
        self.assertFalse((root / 'input.json').exists())
        self.assertFalse(store.get(job)['editor_available'])
        self.assertTrue(book.exists())
        self.assertTrue(options.exists())
        with self.assertRaises(store.SubmissionConflict):
            store.editor_input(job)
        run_cleanup.cleanup(job)
        self.assertEqual(0, store.get(job)['cleanup']['removedFiles'])

    def test_shared_reference_and_symlink_guard(self):
        job, root = self.completed()
        other, other_root = self.completed()
        attachment = root / 'output/run-test/details/shared.pdf'
        attachment.write_bytes(b'shared')
        (other_root / 'output/run-test/fhir/Patient.ndjson').write_text(json.dumps({
            'resourceType': 'DocumentReference', 'content': [{'attachment': {'url': str(attachment)}}]}))
        run_cleanup.cleanup(job)
        self.assertTrue(attachment.exists())
        (root / 'linked').symlink_to(store.APP / 'defaults.config')
        (root / 'converter.log').write_text('still here')
        with self.assertRaises(store.SubmissionConflict):
            run_cleanup.cleanup(job)
        self.assertTrue((root / 'converter.log').exists())
        self.assertTrue((store.APP / 'defaults.config').exists())

    def test_legacy_zip_metadata_is_refreshed_and_changed_results_are_rejected(self):
        job, root = self.completed()
        with zipfile.ZipFile(root / 'dataset-0.zip', 'a') as archive:
            archive.comment = b'older archive metadata'
        manifest = datasets.get(job)
        manifest['datasets'][0]['sha256'] = store.digest(root / 'dataset-0.zip')
        (root / 'datasets.json').write_text(json.dumps(manifest))
        previous = manifest['datasets'][0]['sha256']
        run_cleanup.cleanup(job)
        _, item, archive = datasets.resolve_dataset(job + '.0')
        self.assertNotEqual(previous, item['sha256'])
        self.assertEqual(store.digest(archive), item['sha256'])
        self.assertEqual(item['sha256'], datasets.get(job)['datasets'][0]['sha256'])
        run_cleanup.cleanup(job)
        (root / 'output/run-test/fhir/Patient.ndjson').write_text('{}')
        with self.assertRaises(store.SubmissionConflict):
            datasets.resolve_dataset(job + '.0')
        self.assertFalse((root / 'dataset-0.zip').exists())

    def test_upload_submission_detects_cleanup_between_selection_and_queueing(self):
        job, root = self.completed()
        resolve = datasets.resolve_dataset
        def selected(identifier):
            result = resolve(identifier)
            run_cleanup.cleanup(job)
            return result
        with patch.object(datasets, 'resolve_dataset', side_effect=selected):
            with self.assertRaises(ValueError):
                fhir_uploads.create(str(uuid.uuid4()), 'blaze', [job + '.0'])
        self.assertEqual([], fhir_uploads.history())
