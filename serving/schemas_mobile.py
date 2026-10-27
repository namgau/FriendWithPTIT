"""Pydantic contracts shared by the Android client and the ML serving layer.

Field names intentionally use snake_case so they match the JSON emitted by the
Kotlin ``AiApi`` models.  Unknown fields are ignored to keep the service
forward-compatible while the ML modules evolve.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class _Contract(BaseModel):
    model_config = ConfigDict(extra="ignore", str_strip_whitespace=True)


class AiProfile(_Contract):
    id: str = Field(min_length=1, max_length=128)
    age: int = Field(ge=16, le=100)
    gender: str = "other"
    school: str = ""
    campus: str = ""
    faculty: str = ""
    gpa_tier: str = "GOOD"
    interests: list[str] = Field(default_factory=list)
    mode: str = "FRIENDS"
    distance_km: float = Field(default=0.0, ge=0.0)
    alumni: bool = False


class AiMessage(_Contract):
    sender: Literal["viewer", "partner", "system"] = "viewer"
    text: str = Field(default="", max_length=4000)
    created_at: int = Field(
        default=0,
        description="Unix epoch in milliseconds, matching System.currentTimeMillis().",
    )


class RankRequest(_Contract):
    viewer: AiProfile
    candidates: list[AiProfile] = Field(default_factory=list)
    top_k: int | None = Field(default=None, ge=1, le=500)


class RankItem(_Contract):
    profile_id: str
    score: int = Field(ge=0, le=100)
    reasons: list[str] = Field(default_factory=list)
    model_source: str | None = None


class RankResponse(_Contract):
    items: list[RankItem] = Field(default_factory=list)
    model_source: str = "fallback_rules"


class CompatRequest(_Contract):
    viewer_interests: list[str] = Field(default_factory=list)
    partner_interests: list[str] = Field(default_factory=list)
    messages: list[AiMessage] = Field(default_factory=list)
    viewer_opted_in: bool = False
    partner_opted_in: bool = False
    min_messages: int = Field(default=8, ge=1, le=1000)


class CompatResponse(_Contract):
    score: int = Field(ge=0, le=100)
    blur_level: float = Field(ge=0.0, le=1.0)
    personality: int = Field(ge=0, le=100)
    emotion: int = Field(ge=0, le=100)
    interest: int = Field(ge=0, le=100)
    message_count: int = Field(ge=0)
    reveal_eligible: bool
    model_source: str = "fallback_rules"


class HealthRequest(_Contract):
    messages: list[AiMessage] = Field(default_factory=list)
    viewer_interests: list[str] = Field(default_factory=list)
    partner_interests: list[str] = Field(default_factory=list)
    now: int | None = Field(
        default=None,
        description="Current Unix epoch in milliseconds; defaults to server time.",
    )


class HealthResponse(_Contract):
    level: Literal["HEALTHY", "FADING", "AT_RISK"]
    score: int = Field(ge=0, le=100)
    reasons: list[str] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    message_count: int = Field(ge=0)
    model_source: str = "fallback_rules"


class ModelVersion(_Contract):
    module: str
    version: str
    path: str
    active: bool = False
    metadata: dict = Field(default_factory=dict)


class ModelListResponse(_Contract):
    models: list[ModelVersion] = Field(default_factory=list)


class HealthzResponse(_Contract):
    status: Literal["ok"] = "ok"
    service: str = "fwp-ml-serving"
    models_loaded: bool = False
