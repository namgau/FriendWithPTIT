"""AI Service của FwP (FastAPI).

Chạy:  uvicorn serving.app:app --reload --port 8000
Tài liệu API tự sinh: http://localhost:8000/docs
Từ máy ảo Android, gọi tới http://10.0.2.2:8000
"""
from __future__ import annotations

from fastapi import FastAPI

from serving.predictors import (
    SUGGESTIONS,
    TAU_NUDGE,
    get_health_predictor,
    get_safety_predictor,
    level_from_p,
)
from serving.rules import RULES_VERSION, blur_level, rank_candidates
from serving.schemas import (
    BlurRequest,
    BlurResponse,
    HealthLevel,
    HealthScoreRequest,
    HealthScoreResponse,
    RankRequest,
    RankResponse,
    SafetyScoreRequest,
    SafetyScoreResponse,
)

app = FastAPI(title="FwP AI Service", version="0.1.0")


@app.get("/healthz")
def healthz() -> dict:
    return {
        "status": "ok",
        "rank": RULES_VERSION,
        "blur": RULES_VERSION,
        "health": get_health_predictor().version,
        "safety": get_safety_predictor().version,
    }


@app.post("/rank", response_model=RankResponse)
def rank(req: RankRequest) -> RankResponse:
    ranked, filtered = rank_candidates(req.viewer, req.preferences, req.candidates)
    return RankResponse(ranked=ranked, filtered_out=filtered, model_version=RULES_VERSION)


@app.post("/blur-level", response_model=BlurResponse)
def blur(req: BlurRequest) -> BlurResponse:
    return blur_level(req)


@app.post("/health/score", response_model=HealthScoreResponse)
def health_score(req: HealthScoreRequest) -> HealthScoreResponse:
    predictor = get_health_predictor()
    p, factors = predictor.score(req.messages, req.now_ms, req.viewer_id)
    level = level_from_p(p)
    suggestions = [] if level == HealthLevel.healthy else [SUGGESTIONS[f] for f in factors if f in SUGGESTIONS]
    return HealthScoreResponse(p_disengage=round(p, 4), level=level, top_factors=factors,
                               suggestions=suggestions, model_version=predictor.version)


@app.post("/safety/score", response_model=SafetyScoreResponse)
def safety_score(req: SafetyScoreRequest) -> SafetyScoreResponse:
    predictor = get_safety_predictor()
    tox, derail = predictor.score(req.context, req.draft)
    return SafetyScoreResponse(toxicity=round(tox, 4), derail_risk=round(derail, 4),
                               should_nudge=max(tox, derail) >= TAU_NUDGE, model_version=predictor.version)
