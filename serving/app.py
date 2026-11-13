"""FwP AI Service (FastAPI).

Run locally::

    uvicorn serving.app:app --reload --host 0.0.0.0 --port 8000

Endpoints:

* App ``android/`` (schema ``serving/schemas_mobile.py``): ``/m1/rank``,
  ``/m2/compat``, ``/m3/health``. Luật fallback giống các engine Kotlin trong app;
  ``/v1/models`` quản lý model registry.
* Module 3 (schema ``serving/schemas.py``, đầu vào là tin nhắn có thời gian):
  ``/health/score`` (Head A) và ``/safety/score`` (Head B). Đây là chỗ cắm mô hình
  sau khi huấn luyện (``serving/predictors.py``); Head B chưa được nối vào app.

Android emulator base URL: ``http://10.0.2.2:8000/``.
"""

from __future__ import annotations

import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from serving import engines
from serving.model_registry import ModelRegistry
from serving.predictors import (
    SUGGESTIONS,
    TAU1,
    TAU2,
    TAU_NUDGE,
    get_health_predictor,
    get_safety_predictor,
    level_from_p,
)
from serving.schemas import (
    HealthLevel,
    HealthScoreRequest,
    HealthScoreResponse,
    SafetyScoreRequest,
    SafetyScoreResponse,
)
from serving.schemas_mobile import (
    CompatRequest,
    CompatResponse,
    HealthRequest,
    HealthResponse,
    ModelListResponse,
    ModelVersion,
    RankRequest as AndroidRankRequest,
    RankResponse as AndroidRankResponse,
)
from serving.silent_checker import (
    ConversationScanItem,
    SilenceTier,
    SilentCheckerJob,
    SilentConversationResult,
    SilentScanRequest,
    SilentScanResponse,
    scan_silent_conversations,
)

SERVICE_VERSION = "0.2.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    enable_loop = os.getenv("ENABLE_SILENT_CHECKER_LOOP", "0").lower() in ("1", "true")
    job_task = None
    if enable_loop:
        job = SilentCheckerJob(interval_s=float(os.getenv("SILENT_CHECKER_INTERVAL", "3600.0")))
        job_task = asyncio.create_task(job.start())
    yield
    if job_task:
        job.stop()
        job_task.cancel()
        try:
            await job_task
        except asyncio.CancelledError:
            pass


app = FastAPI(
    title="FwP AI Service",
    description=(
        "M1 ranking, M2 compatibility / reveal, M3 conversation health and safety. "
        "Rule-based fallbacks keep both Android clients running without trained artifacts."
    ),
    version=SERVICE_VERSION,
    lifespan=lifespan,
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


@app.get("/healthz")
def healthz() -> dict:
    return {
        "status": "ok",
        "service": "fwp-ml-serving",
        "models_loaded": any(
            registry.active(module) is not None for module in ("m1", "m2", "m3")
        ),
        "health": get_health_predictor().version,
        "safety": get_safety_predictor().version,
    }


# ------------------------------------------------- Module 3 (chỗ cắm mô hình)

@app.post("/health/score", response_model=HealthScoreResponse)
def health_score(req: HealthScoreRequest) -> HealthScoreResponse:
    predictor = get_health_predictor()
    p, factors = predictor.score(req.messages, req.now_ms, req.viewer_id)
    tau1 = getattr(predictor, "tau1", TAU1)
    tau2 = getattr(predictor, "tau2", TAU2)
    level = level_from_p(p, tau1=tau1, tau2=tau2)
    suggestions = (
        []
        if level == HealthLevel.healthy
        else [SUGGESTIONS[f] for f in factors if f in SUGGESTIONS]
    )
    return HealthScoreResponse(
        p_disengage=round(p, 4),
        level=level,
        top_factors=factors,
        suggestions=suggestions,
        model_version=predictor.version,
    )


@app.post("/safety/score", response_model=SafetyScoreResponse)
def safety_score(req: SafetyScoreRequest) -> SafetyScoreResponse:
    predictor = get_safety_predictor()
    tox, derail = predictor.score(req.context, req.draft)
    tau_nudge = getattr(predictor, "tau_nudge", TAU_NUDGE)
    return SafetyScoreResponse(
        toxicity=round(tox, 4),
        derail_risk=round(derail, 4),
        should_nudge=max(tox, derail) >= tau_nudge,
        model_version=predictor.version,
    )


# ---------------------------------- Silent Conversation Checker (Module 3 §7)

@app.post("/silent-checker/scan", response_model=SilentScanResponse)
def scan_silent_conversations_endpoint(req: SilentScanRequest) -> SilentScanResponse:
    results = scan_silent_conversations(req.conversations, now_ms=req.now_ms)
    if req.min_tier is not None:
        tier_hierarchy = {
            SilenceTier.ACTIVE: 0,
            SilenceTier.SILENT_12H: 1,
            SilenceTier.SILENT_24H: 2,
            SilenceTier.SILENT_48H: 3,
        }
        min_lvl = tier_hierarchy.get(req.min_tier, 0)
        results = [r for r in results if tier_hierarchy.get(r.tier, 0) >= min_lvl]

    scanned_count = len(req.conversations)
    silent_count = sum(1 for r in results if r.tier != SilenceTier.ACTIVE)
    return SilentScanResponse(
        scanned_count=scanned_count,
        silent_count=silent_count,
        results=results,
    )


# --------------------------------------------------------------- android/ API

@app.post("/m1/rank", response_model=AndroidRankResponse)
def rank_android(request: AndroidRankRequest) -> AndroidRankResponse:
    return engines.rank(request)


@app.post("/m2/compat", response_model=CompatResponse)
@app.post("/compat/update", response_model=CompatResponse, include_in_schema=False)
def update_compatibility(request: CompatRequest) -> CompatResponse:
    return engines.compatibility(request)


@app.post("/m3/health", response_model=HealthResponse)
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
