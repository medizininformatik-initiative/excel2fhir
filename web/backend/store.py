"""Persistent prototype queue. Each job owns its files and converter process."""
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import uuid

ROOT = Path(os.environ.get("WORKBENCH_DATA", "/data"))
APP = Path(os.environ.get("CONVERTER_HOME", "/converter"))
SOURCES = {"starter": "input/FHIR_Testdatengenerator_Vorlage.xlsx", "demo": "FHIR_Testdatengenerator_Interpolar_Demo.xlsx"}


@contextmanager
def connect():
    ROOT.mkdir(parents=True, exist_ok=True)
    db = None
    try:
        # Serialize first-time WAL/schema initialization across API threads and worker.
        with (ROOT / '.database-init.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            db = sqlite3.connect(ROOT / 'jobs.sqlite', timeout=15)
            db.row_factory = sqlite3.Row
            db.execute('PRAGMA journal_mode=WAL')
            db.execute("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, state TEXT NOT NULL, created REAL NOT NULL, cancel INTEGER NOT NULL DEFAULT 0, exit_code INTEGER)")
            db.execute("CREATE TABLE IF NOT EXISTS submissions (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, jobs TEXT NOT NULL)")
        with db:
            yield db
    finally:
        if db is not None:
            db.close()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_configuration(path):
    metadata = path.with_suffix(".validated.json")
    try:
        result = subprocess.run(["java", "-Xmx256m", "-cp", str(APP / "excel2fhir.jar"),
                                 "de.uni_leipzig.life.csv2fhir.ConfigurationPreflight", str(path), str(metadata)],
                                capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise ValueError(result.stderr.strip() or "Configuration validation failed")
        return json.loads(metadata.read_text())
    except subprocess.TimeoutExpired as error:
        raise ValueError("Configuration validation timed out") from error
    finally:
        metadata.unlink(missing_ok=True)


class SubmissionConflict(ValueError):
    pass


def submitted(request_id, fingerprint, db):
    if not request_id:
        return None
    row = db.execute("SELECT fingerprint,jobs FROM submissions WHERE id=?", (request_id,)).fetchone()
    if row:
        if row['fingerprint'] != fingerprint:
            raise SubmissionConflict('This submission ID was already used for different settings')
        return json.loads(row['jobs'])
    return None


def submit(descriptor, request_id, prepare):
    """Publish all prepared snapshots in one transaction; retries return the same jobs."""
    fingerprint = hashlib.sha256(json.dumps(descriptor, sort_keys=True).encode()).hexdigest()
    with connect() as db:
        existing = submitted(request_id, fingerprint, db)
    if existing is not None:
        return existing
    prepared = []
    accepted = False
    try:
        prepare(prepared)
        with connect() as db:
            db.execute("BEGIN IMMEDIATE")
            existing = submitted(request_id, fingerprint, db)
            if existing is not None:
                return existing
            now = time.time()
            for index, job_id in enumerate(prepared):
                db.execute("INSERT INTO jobs(id,state,created) VALUES (?, 'queued', ?)", (job_id, now + index * 0.000001))
            if request_id:
                db.execute("INSERT INTO submissions(id,fingerprint,jobs) VALUES (?,?,?)",
                           (request_id, fingerprint, json.dumps(prepared)))
        accepted = True
        return prepared
    finally:
        if not accepted:
            for job_id in prepared:
                shutil.rmtree(ROOT / 'jobs' / job_id)


def prepare_job(prepared, source, profile, configuration_properties=None, *, input_path=None,
                saved_configuration=None, batch_id=None, repeated_from=None, source_name=None):
    if input_path is None:
        if source in SOURCES:
            input_path = APP / SOURCES[source]
        else:
            from inputs import resolve
            input_path, source_name = resolve(source)
    if profile not in {'default', 'workbook'}:
        raise ValueError('Unknown configuration source')
    if profile == 'workbook' and configuration_properties is not None:
        raise ValueError('Workbook configuration cannot be combined with editor settings')
    job_id = str(uuid.uuid4())
    directory = ROOT / 'jobs' / job_id
    directory.mkdir(parents=True)
    prepared.append(job_id)
    shutil.copyfile(input_path, directory / 'input.xlsx')
    if profile == 'workbook':
        execution = {}
        config = {'id': 'workbook', 'name': 'Workbook configurations'}
    else:
        if configuration_properties is None:
            shutil.copyfile(APP / 'defaults.config', directory / 'default.config')
            execution = {'formats': ['JSON', 'NDJSON'], 'validation': False, 'patientsPerFile': 1}
        else:
            if not isinstance(configuration_properties, str) or not 1 <= len(configuration_properties) <= 1_000_000:
                raise ValueError('Invalid configuration size')
            (directory / 'default.config').write_text(configuration_properties)
            execution = validate_configuration(directory / 'default.config')
        config = {'id': 'default' if configuration_properties is None else 'editor',
                  'name': 'Converter defaults' if configuration_properties is None else 'Submitted configuration',
                  'optionsProperties': (directory / 'default.config').read_text()}
        if saved_configuration:
            config.update(saved_configuration)
    snapshot = {'schemaVersion': 1, 'source': source, 'profile': config,
                'inputSha256': digest(directory / 'input.xlsx'), 'converterSha256': digest(APP / 'excel2fhir.jar'), **execution}
    if source_name:
        snapshot['sourceName'] = source_name
    if batch_id:
        snapshot['batchId'] = batch_id
    if repeated_from:
        snapshot['repeatedFrom'] = repeated_from
    (directory / 'snapshot.json').write_text(json.dumps(snapshot, indent=2))
    for name in ('input.xlsx', 'default.config', 'snapshot.json'):
        if (directory / name).exists():
            (directory / name).chmod(0o444)


def create(source, profile, configuration_properties=None, request_id=None):
    descriptor = {'kind': 'single', 'source': source, 'profile': profile, 'configuration': configuration_properties}
    return submit(descriptor, request_id,
                  lambda prepared: prepare_job(prepared, source, profile, configuration_properties))[0]


def repeat(job_id, request_id):
    def prepare(prepared):
        original = get(job_id)
        if not original:
            raise FileNotFoundError('Run not found')
        if original['state'] in {'queued', 'running'}:
            raise SubmissionConflict('Wait until the original run has ended before repeating it')
        folder = ROOT / 'jobs' / job_id
        snapshot = json.loads((folder / 'snapshot.json').read_text())
        if digest(folder / 'input.xlsx') != snapshot['inputSha256']:
            raise ValueError('The saved input has changed')
        config = snapshot['profile']
        text = None if config['id'] == 'workbook' else config['optionsProperties']
        if text is not None and (folder / 'default.config').read_text() != text:
            raise ValueError('The saved configuration has changed')
        prepare_job(prepared, snapshot['source'], 'workbook' if text is None else 'default', text,
                    input_path=folder / 'input.xlsx', saved_configuration=config if text is not None else None,
                    repeated_from={'id': job_id, 'converterSha256': snapshot['converterSha256']},
                    source_name=snapshot.get('sourceName'))
    return submit({'kind': 'repeat', 'job': job_id}, request_id, prepare)[0]


def jobs():
    with connect() as db:
        return [job_result(row) for row in db.execute("SELECT * FROM jobs ORDER BY created DESC")]


def job_result(row):
    job = dict(row)
    job["download_available"] = job["state"] in {"succeeded", "failed"} and (ROOT / "jobs" / job["id"] / "result.zip").is_file()
    snapshot_path = ROOT / 'jobs' / job['id'] / 'snapshot.json'
    if snapshot_path.is_file():
        snapshot = json.loads(snapshot_path.read_text())
        config = snapshot['profile']
        job.update(source=snapshot['source'], source_name=snapshot.get('sourceName'), configuration={'id': config['id'], 'name': config['name'],
                   'revision': config.get('revision')}, batch_id=snapshot.get('batchId'),
                   repeated_from=snapshot.get('repeatedFrom', {}).get('id'))
    return job


def get(job_id):
    with connect() as db:
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    return job_result(row) if row else None


def cancel(job_id):
    with connect() as db:
        db.execute("UPDATE jobs SET cancel=1, state=CASE WHEN state='queued' THEN 'cancelled' ELSE state END WHERE id=? AND state IN ('queued','running')", (job_id,))


def claim():
    with connect() as db:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute("SELECT id FROM jobs WHERE state='queued' AND cancel=0 ORDER BY created LIMIT 1").fetchone()
        if row:
            db.execute("UPDATE jobs SET state='running' WHERE id=?", (row['id'],))
            return row['id']


def finish(job_id, state, code=None):
    with connect() as db:
        db.execute("UPDATE jobs SET state=CASE WHEN cancel=1 AND ?='succeeded' THEN 'cancelled' ELSE ? END,exit_code=? WHERE id=?", (state, state, code, job_id))
