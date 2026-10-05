"""Persistent prototype queue. Each job owns its files and converter process."""
from contextlib import contextmanager
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


def create(source, profile, configuration_properties=None):
    if source not in SOURCES or profile not in {"default", "workbook"}:
        raise ValueError("Unknown input or configuration profile")
    if profile == "workbook" and configuration_properties is not None:
        raise ValueError("Workbook configuration cannot be combined with editor settings")
    job_id = str(uuid.uuid4())
    directory = ROOT / "jobs" / job_id
    directory.mkdir(parents=True)
    try:
        shutil.copyfile(APP / SOURCES[source], directory / "input.xlsx")
        if profile == "workbook":
            execution = {}
        elif configuration_properties is None:
            shutil.copyfile(APP / "defaults.config", directory / "default.config")
            execution = {"formats": ["JSON", "NDJSON"], "validation": False, "patientsPerFile": 1}
        else:
            if not isinstance(configuration_properties, str) or not 1 <= len(configuration_properties) <= 1_000_000:
                raise ValueError("Invalid configuration size")
            (directory / "default.config").write_text(configuration_properties)
            execution = validate_configuration(directory / "default.config")
        config = {"id": "default" if configuration_properties is None else "editor",
                  "name": "Converter defaults" if configuration_properties is None else "Submitted configuration",
                  "optionsProperties": (directory / "default.config").read_text()} if profile != "workbook" else {
                      "id": "workbook", "name": "Workbook configurations"}
        snapshot = {"schemaVersion": 1, "source": source, "profile": config, "inputSha256": digest(directory / "input.xlsx"), "converterSha256": digest(APP / "excel2fhir.jar"), **execution}
        (directory / "snapshot.json").write_text(json.dumps(snapshot, indent=2))
        for name in ("input.xlsx", "default.config", "snapshot.json"):
            if (directory / name).exists():
                (directory / name).chmod(0o444)
        with connect() as db:
            db.execute("INSERT INTO jobs(id,state,created) VALUES (?, 'queued', ?)", (job_id, time.time()))
    except Exception:
        shutil.rmtree(directory)
        raise
    return job_id


def jobs():
    with connect() as db:
        return [job_result(row) for row in db.execute("SELECT * FROM jobs ORDER BY created DESC")]


def job_result(row):
    job = dict(row)
    job["download_available"] = job["state"] in {"succeeded", "failed"} and (ROOT / "jobs" / job["id"] / "result.zip").is_file()
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
