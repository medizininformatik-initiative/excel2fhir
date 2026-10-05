"""Single supervisor; bounded parallel job processes can be added behind claim()."""
import fcntl
import json
import os
import signal
import subprocess
import time
import zipfile

import store

stopping = False


def stop(*_):
    global stopping
    stopping = True


def terminate(process):
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            process.wait()
            return
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()


def execute(job_id):
    folder = store.ROOT / "jobs" / job_id
    command = ["java", "-Xmx3g", "-jar", str(store.APP / "excel2fhir.jar"), "-f", str(folder / "input.xlsx"), "-o", str(folder / "output")]
    process = None
    try:
        snapshot = json.loads((folder / "snapshot.json").read_text())
        if store.digest(store.APP / "excel2fhir.jar") != snapshot["converterSha256"]:
            raise RuntimeError("Converter image changed after submission; start a new run")
        if store.digest(folder / "input.xlsx") != snapshot["inputSha256"]:
            raise RuntimeError("Input snapshot has changed")
        if snapshot["profile"]["id"] != "workbook":
            if (folder / "default.config").read_text() != snapshot["profile"]["optionsProperties"]:
                raise RuntimeError("Configuration snapshot has changed")
            command.extend(["--converter-options", str(folder / "default.config")])
        with (folder / "converter.log").open("w") as log:
            process = subprocess.Popen(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            while process.poll() is None:
                if stopping or store.get(job_id)["cancel"]:
                    terminate(process)
                    break
                time.sleep(0.2)
        state = "interrupted" if stopping else "cancelled" if store.get(job_id)["cancel"] else "succeeded" if process.returncode == 0 else "failed"
        if state in {"succeeded", "failed"}:
            with zipfile.ZipFile(folder / "result.tmp", "w", zipfile.ZIP_DEFLATED) as archive:
                for path in sorted((folder / "output").rglob("*")):
                    if path.is_file():
                        archive.write(path, path.relative_to(folder))
                archive.write(folder / "snapshot.json", "snapshot.json")
                archive.write(folder / "converter.log", "converter.log")
            (folder / "result.tmp").rename(folder / "result.zip")
        if stopping:
            state = "interrupted"
        elif store.get(job_id)["cancel"]:
            state = "cancelled"
        store.finish(job_id, state, process.returncode)
    except Exception as error:
        if process:
            terminate(process)
        with (folder / "converter.log").open("a") as log:
            log.write(f"\nWorker error: {error}\n")
        store.finish(job_id, "failed")


def main():
    store.ROOT.mkdir(parents=True, exist_ok=True)
    # One supervisor owns recovery. Queue claims are transactional for future slots.
    with (store.ROOT / "worker.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        with store.connect() as db:
            db.execute("UPDATE jobs SET state='interrupted' WHERE state='running'")
        signal.signal(signal.SIGTERM, stop)
        signal.signal(signal.SIGINT, stop)
        while not stopping:
            job_id = store.claim()
            if job_id:
                execute(job_id)
            else:
                time.sleep(0.5)


if __name__ == "__main__":
    main()
