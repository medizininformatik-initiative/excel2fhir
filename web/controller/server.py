"""Internal controller API; only fixed operations on owned environments are exposed."""
from contextlib import asynccontextmanager
import json
from uuid import UUID

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field
from starlette.middleware.trustedhost import TrustedHostMiddleware
import engine


@asynccontextmanager
async def lifespan(app):
    engine.recover()
    yield


app = FastAPI(lifespan=lifespan)
app.add_middleware(TrustedHostMiddleware, allowed_hosts=['controller', 'localhost', '127.0.0.1', 'testserver'])


class Create(BaseModel):
    model_config = ConfigDict(extra='forbid')
    name: str = Field(min_length=1, max_length=120)


def operation(call, *arguments):
    try:
        return call(*arguments)
    except FileNotFoundError as error:
        raise HTTPException(404, str(error))
    except ValueError as error:
        raise HTTPException(409, str(error))


@app.get('/environments')
def list_environments():
    return engine.summaries()


@app.post('/environments', status_code=201)
def create_environment(request: Create):
    return operation(engine.create, request.name)


@app.post('/environments/{identifier}/start', status_code=202)
def start(identifier: UUID):
    return operation(engine.submit, str(identifier), 'start')


@app.post('/environments/{identifier}/stop', status_code=202)
def stop(identifier: UUID):
    return operation(engine.submit, str(identifier), 'stop')


@app.get('/environments/{identifier}/certificate')
def certificate(identifier: UUID):
    operation(engine.read, str(identifier))
    return FileResponse(engine.ROOT / str(identifier) / 'data-node/auth/cert.pem', filename='data-node-' + str(identifier) + '.pem')


@app.get('/environments/{identifier}/credentials')
def credentials(identifier: UUID):
    operation(engine.read, str(identifier))
    return json.loads((engine.ROOT / str(identifier) / 'credentials.json').read_text())
