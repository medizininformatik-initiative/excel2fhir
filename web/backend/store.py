"""Persistent prototype queue. Each job owns its files and converter process."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import time
import uuid

ROOT = Path(os.environ.get("WORKBENCH_DATA", "/data"))
APP = Path(os.environ.get("CONVERTER_HOME", "/converter"))
SOURCES = {"starter": "input/FHIR_Testdatengenerator_Vorlage.xlsx", "demo": "FHIR_Testdatengenerator_Interpolar_Demo.xlsx"}


@contextmanager
def connect():
    ROOT.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(ROOT / "jobs.sqlite", timeout=15)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, state TEXT NOT NULL, created REAL NOT NULL, cancel INTEGER NOT NULL DEFAULT 0, exit_code INTEGER)")
    try:
        with db:
            yield db
    finally:
        db.close()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def create(source, profile):
    if source not in SOURCES or profile != "default":
        raise ValueError("Unknown input or configuration profile")
    job_id = str(uuid.uuid4())
    directory = ROOT / "jobs" / job_id
    directory.mkdir(parents=True)
    try:
        shutil.copyfile(APP / SOURCES[source], directory / "input.xlsx")
        shutil.copyfile(APP / "defaults.config", directory / "default.config")
        config = {"id": "default", "name": "Existing converter defaults", "optionsProperties": (directory / "default.config").read_text()}
        (ROOT / "profiles").mkdir(exist_ok=True)
        # Atomically refresh the bundled profile after an image upgrade.
        profile_path = ROOT / "profiles/default.json"
        temporary_profile = ROOT / "profiles" / f"{job_id}.tmp"
        temporary_profile.write_text(json.dumps(config, indent=2))
        temporary_profile.replace(profile_path)
        snapshot = {"schemaVersion": 1, "source": source, "profile": config, "inputSha256": digest(directory / "input.xlsx"), "converterSha256": digest(APP / "excel2fhir.jar"), "formats": ["JSON", "NDJSON"], "validation": False}
        (directory / "snapshot.json").write_text(json.dumps(snapshot, indent=2))
        for name in ("input.xlsx", "default.config", "snapshot.json"):
            (directory / name).chmod(0o444)
        with connect() as db:
            db.execute("INSERT INTO jobs(id,state,created) VALUES (?, 'queued', ?)", (job_id, time.time()))
    except Exception:
        shutil.rmtree(directory)
        raise
    return job_id


def jobs():
    with connect() as db:
        return [dict(row) for row in db.execute("SELECT * FROM jobs ORDER BY created DESC")]


def get(job_id):
    with connect() as db:
        row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
    return dict(row) if row else None


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
