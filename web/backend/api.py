from uuid import UUID
from urllib.parse import urlsplit

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, PlainTextResponse
from starlette.middleware.trustedhost import TrustedHostMiddleware
from pydantic import BaseModel, ConfigDict, Field
import store
import configurations
import inputs
from starlette.concurrency import run_in_threadpool

app = FastAPI(title="Excel2FHIR workbench prototype")
app.add_middleware(TrustedHostMiddleware, allowed_hosts=["localhost", "127.0.0.1", "api", "testserver"])


@app.middleware("http")
async def local_mutations(request: Request, call_next):
    origin = request.headers.get("origin")
    if request.method not in {"GET", "HEAD", "OPTIONS"} and origin:
        if urlsplit(origin).netloc != request.headers.get("host"):
            return PlainTextResponse("Cross-origin changes are not allowed", status_code=403)
    return await call_next(request)



class JobRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    requestId: UUID | None = None
    source: str = "starter"
    profile: str = "default"
    configurationProperties: str | None = Field(default=None, min_length=1, max_length=1_000_000)


def directory(job_id):
    try:
        UUID(job_id)
    except ValueError:
        raise HTTPException(404, "Job not found")
    job = store.get(job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    return store.ROOT / "jobs" / job_id, job


@app.get("/api/catalog")
def catalog():
    return {"sources": list(store.SOURCES), "profiles": ["default", "workbook"]}


@app.get("/api/jobs")
def jobs():
    return store.jobs()


@app.post("/api/jobs", status_code=201)
def create(request: JobRequest):
    try:
        job_id = store.create(request.source, request.profile, request.configurationProperties, str(request.requestId) if request.requestId else None)
    except store.SubmissionConflict as error:
        raise HTTPException(409, str(error))
    except ValueError as error:
        raise HTTPException(422, str(error))
    return store.get(job_id)


@app.post("/api/jobs/{job_id}/cancel", status_code=202)
def cancel(job_id: str):
    directory(job_id)
    store.cancel(job_id)
    return store.get(job_id)


@app.get("/api/jobs/{job_id}/logs", response_class=PlainTextResponse)
def logs(job_id: str):
    folder, _ = directory(job_id)
    path = folder / "converter.log"
    if not path.exists():
        return "Waiting for worker…"
    with path.open("rb") as stream:
        stream.seek(max(0, path.stat().st_size - 128 * 1024))
        return stream.read().decode("utf-8", errors="replace")


@app.get("/api/jobs/{job_id}/snapshot")
def snapshot(job_id: str):
    folder, _ = directory(job_id)
    return FileResponse(folder / "snapshot.json", filename="snapshot.json")


@app.get("/api/jobs/{job_id}/download")
def download(job_id: str):
    folder, job = directory(job_id)
    if not job["download_available"]:
        raise HTTPException(409, "Result archive is not available")
    return FileResponse(folder / "result.zip", filename=f"excel2fhir-{job_id}.zip")


# Configuration content uses the same versioned Properties contract as the editor.


class ConfigurationCreate(BaseModel):
    model_config = ConfigDict(extra='forbid')
    name: str = Field(min_length=1, max_length=120)
    configurationProperties: str = Field(min_length=1, max_length=1_000_000)


class ConfigurationUpdate(BaseModel):
    model_config = ConfigDict(extra='forbid')
    revision: int = Field(ge=1, strict=True)
    name: str | None = Field(default=None, min_length=1, max_length=120)
    configurationProperties: str | None = Field(default=None, min_length=1, max_length=1_000_000)


class ConfigurationDuplicate(BaseModel):
    model_config = ConfigDict(extra='forbid')
    revision: int = Field(ge=1, strict=True)
    name: str = Field(min_length=1, max_length=120)


class ConfigurationDelete(BaseModel):
    model_config = ConfigDict(extra='forbid')
    revision: int = Field(ge=1, strict=True)


def configuration_call(action, *args):
    try:
        return action(*args)
    except FileNotFoundError:
        raise HTTPException(404, 'Configuration not found')
    except configurations.Conflict as error:
        raise HTTPException(409, str(error))
    except store.SubmissionConflict as error:
        raise HTTPException(409, str(error))
    except ValueError as error:
        raise HTTPException(422, str(error))


@app.get('/api/configurations')
def list_configurations():
    return configurations.summaries()


@app.get('/api/configurations/{configuration_id}')
def get_configuration(configuration_id: str):
    return configuration_call(configurations.get, configuration_id)


@app.post('/api/configurations', status_code=201)
def create_configuration(request: ConfigurationCreate):
    return configuration_call(configurations.create, request.name, request.configurationProperties)


@app.patch('/api/configurations/{configuration_id}')
def update_configuration(configuration_id: str, request: ConfigurationUpdate):
    return configuration_call(configurations.update, configuration_id, request.revision,
                              request.name, request.configurationProperties)


@app.post('/api/configurations/{configuration_id}/duplicate', status_code=201)
def duplicate_configuration(configuration_id: str, request: ConfigurationDuplicate):
    return configuration_call(configurations.duplicate, configuration_id, request.revision, request.name)


@app.delete('/api/configurations/{configuration_id}', status_code=204)
def delete_configuration(configuration_id: str, request: ConfigurationDelete):
    configuration_call(configurations.delete, configuration_id, request.revision)


class SavedSelection(BaseModel):
    model_config = ConfigDict(extra='forbid')
    id: UUID
    revision: int = Field(ge=1, strict=True)


class BatchRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    requestId: UUID
    source: str = 'starter'
    configurations: list[SavedSelection] = Field(min_length=1, max_length=100)


class RepeatRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    requestId: UUID


@app.post('/api/job-batches', status_code=201)
def start_batch(request: BatchRequest):
    selections = [{'id': str(item.id), 'revision': item.revision} for item in request.configurations]
    ids = configuration_call(configurations.start_jobs, request.source, selections, str(request.requestId))
    return [store.get(job_id) for job_id in ids]


@app.post('/api/jobs/{job_id}/repeat', status_code=201)
def repeat_job(job_id: str, request: RepeatRequest):
    directory(job_id)
    new_id = configuration_call(store.repeat, job_id, str(request.requestId))
    return store.get(new_id)


@app.get('/api/inputs')
def list_inputs():
    return inputs.summaries()


@app.post('/api/inputs', status_code=201)
async def upload_input(request: Request, filename: str):
    try:
        name = inputs.checked_name(filename)
        with inputs.incoming() as directory:
            size = 0
            with (directory / 'input.xlsx').open('wb') as target:
                async for chunk in request.stream():
                    size += len(chunk)
                    if size > inputs.MAX_UPLOAD:
                        raise HTTPException(413, 'The workbook exceeds 64 MiB')
                    target.write(chunk)
            return await run_in_threadpool(inputs.publish, directory, name)
    except ValueError as error:
        raise HTTPException(422, str(error))
