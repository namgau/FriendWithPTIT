"""FastAPI serving layer for the FwP ML modules.

Run locally::

    uvicorn serving.app:app --reload --host 0.0.0.0 --port 8000

Android emulator base URL: ``http://10.0.2.2:8000/`` and build with
``-PFWP_AI_BASE_URL=http://10.0.2.2:8000/``.

The endpoints expose deterministic, auditable fallback rules (same behaviour as
the Kotlin engines).  The model registry exposes installed versions and supports
activating a version for a future trained adapter, without changing the JSON
contract consumed by the app.
"""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from serving import engines
from serving.model_registry import ModelRegistry
from serving.schemas import (
    CompatRequest,
    CompatResponse,
    HealthRequest,
    HealthResponse,
    HealthzResponse,
    ModelListResponse,
    ModelVersion,
    RankRequest,
    RankResponse,
)

SERVICE_VERSION = "0.1.0"

app = FastAPI(
    title="FwP ML Serving",
    description=(
        "M1 ranking, M2 compatibility / reveal and M3 conversation health. "
        "Fallback rules mirror the Android demo; trained artifacts live under models/."
    ),
    version=SERVICE_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

registry = ModelRegistry(Path(__file__).resolve().parent.parent / "models")


@app.get("/", include_in_schema=False)
def root() -> dict:
    return {
        "service": "fwp-ml-serving",
        "version": SERVICE_VERSION,
        "docs": "/docs",
        "health": "/healthz",
    }


@app.get("/healthz", response_model=HealthzResponse)
def healthz() -> HealthzResponse:
    return HealthzResponse(
        status="ok",
        service="fwp-ml-serving",
        models_loaded=any(registry.active(module) is not None for module in ("m1", "m2", "m3")),
    )


@app.post("/m1/rank", response_model=RankResponse)
@app.post("/rank", response_model=RankResponse, include_in_schema=False)
def rank(request: RankRequest) -> RankResponse:
    return engines.rank(request)


@app.post("/m2/compat", response_model=CompatResponse)
@app.post("/compat/update", response_model=CompatResponse, include_in_schema=False)
def update_compatibility(request: CompatRequest) -> CompatResponse:
    return engines.compatibility(request)


@app.post("/m3/health", response_model=HealthResponse)
@app.post("/health/score", response_model=HealthResponse, include_in_schema=False)
def score_health(request: HealthRequest) -> HealthResponse:
    return engines.health(request)


@app.get("/v1/models", response_model=ModelListResponse)
def list_models() -> ModelListResponse:
    return ModelListResponse(models=registry.list_all())


@app.get("/v1/models/{module}", response_model=list[ModelVersion])
def list_module_models(module: str) -> list[ModelVersion]:
    if module not in ("m1", "m2", "m3"):
        raise HTTPException(status_code=404, detail=f"Unknown module: {module}")
    return registry.versions(module)


@app.post("/v1/models/{module}/activate/{version}", response_model=ModelVersion)
def activate_model(module: str, version: str) -> ModelVersion:
    try:
        artifact = registry.activate(module, version)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return ModelVersion(
        module=artifact.module,
        version=artifact.version,
        path=str(artifact.directory),
        active=True,
        metadata=artifact.metadata,
    )
