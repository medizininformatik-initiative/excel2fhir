from uuid import UUID
from urllib.parse import urlsplit

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, PlainTextResponse
from starlette.middleware.trustedhost import TrustedHostMiddleware
from pydantic import BaseModel, ConfigDict, Field
import store
import configurations
import inputs
import datasets
import service_status
import fhir_uploads
import generation
from generation import Settings as GenerationSettings
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
    datasetName: str | None = Field(default=None, max_length=200)
    generation: GenerationSettings | None = None
    source: str = "starter"
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
    return {"sources": list(store.SOURCES), "profiles": ["default"]}


@app.get("/api/jobs")
def jobs():
    return store.jobs()


@app.post("/api/jobs", status_code=201)
def create(request: JobRequest):
    try:
        job_id = store.create(request.source, "default", request.configurationProperties, str(request.requestId) if request.requestId else None,
                              request.generation.model_dump(mode='json') if request.generation else None, request.datasetName)
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


@app.delete('/api/jobs/{job_id}', status_code=204)
def delete_job(job_id: UUID):
    try:
        store.delete(str(job_id))
    except FileNotFoundError as error:
        raise HTTPException(404, str(error))
    except store.SubmissionConflict as error:
        raise HTTPException(409, str(error))


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


@app.post('/api/jobs/{job_id}/editor')
def load_job_editor(job_id: str):
    directory(job_id)
    return configuration_call(store.editor_input, job_id)


@app.get('/api/inputs')
def list_inputs():
    return inputs.summaries()


@app.post('/api/inputs', status_code=201)
async def upload_input(request: Request, filename: str):
    try:
        name = inputs.checked_name(filename)
        with inputs.incoming() as directory:
            size = 0
            with (directory / inputs.input_filename(inputs.kind_for_name(name))).open('wb') as target:
                async for chunk in request.stream():
                    size += len(chunk)
                    if size > inputs.MAX_UPLOAD:
                        raise HTTPException(413, 'The input exceeds 64 MiB')
                    target.write(chunk)
            return await run_in_threadpool(inputs.publish, directory, name)
    except ValueError as error:
        raise HTTPException(422, str(error))


@app.get('/api/generation-catalogue')
def generation_catalogue():
    return generation.catalogue()


@app.get('/api/jobs/{job_id}/artifacts')
def job_artifacts(job_id: str):
    _, job = directory(job_id)
    return datasets.get(job_id) if job['state'] in {'succeeded', 'failed'} else {'datasets': [], 'artifacts': []}


@app.get('/api/jobs/{job_id}/artifacts/{artifact_id}')
def artifact_download(job_id: str, artifact_id: str):
    _, job = directory(job_id)
    if job['state'] not in {'succeeded', 'failed'}:
        raise HTTPException(409, 'Run has not ended')
    try:
        path = datasets.resolve_artifact(job_id, artifact_id)
        return FileResponse(path, filename=path.name)
    except FileNotFoundError as error:
        raise HTTPException(404, str(error))


@app.get('/api/datasets')
def list_datasets():
    return [{**item, 'jobId': job['id'], 'state': job['state'], 'created': job['created'],
             'datasetName': job.get('dataset_name'), 'source': job.get('source'), 'sourceName': job.get('source_name'), 'configuration': job.get('configuration')}
            for job in store.jobs() if job['state'] in {'succeeded', 'failed'} for item in datasets.get(job['id'])['datasets']]


@app.get('/api/datasets/{dataset_id}/download')
def dataset_download(dataset_id: str):
    try:
        _, item, path = datasets.resolve_dataset(dataset_id)
        if not path.is_file():
            raise HTTPException(409, 'Dataset archive is not available')
        return FileResponse(path, filename='dataset-' + dataset_id + '.zip')
    except FileNotFoundError as error:
        raise HTTPException(404, str(error))


class UploadRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    requestId: UUID
    target: str
    datasets: list[str] = Field(min_length=1, max_length=100)


@app.get('/api/services')
def services():
    return service_status.services()


@app.get('/api/fhir-targets')
def fhir_targets():
    return fhir_uploads.targets()


@app.get('/api/fhir-uploads')
def fhir_upload_history():
    return fhir_uploads.history()


@app.post('/api/fhir-uploads', status_code=202)
def fhir_upload(request: UploadRequest):
    return configuration_call(fhir_uploads.create, str(request.requestId), request.target, request.datasets)


@app.post('/api/fhir-uploads/{upload_id}/cancel', status_code=202)
def cancel_fhir_upload(upload_id: UUID):
    configuration_call(fhir_uploads.cancel, str(upload_id))
    return fhir_uploads.get(str(upload_id))


@app.get('/api/fhir-uploads/{upload_id}/logs', response_class=PlainTextResponse)
def fhir_upload_logs(upload_id: UUID):
    return configuration_call(fhir_uploads.logs, str(upload_id))
