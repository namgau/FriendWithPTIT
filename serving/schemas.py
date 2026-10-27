"""Hợp đồng JSON giữa app/backend và AI Service.

Đây là "ổ cắm" chung: app Android và backend chỉ phụ thuộc vào các schema này.
Khi Module 3 có mô hình thật, chỉ thay phần dự đoán phía sau, schema giữ nguyên.
Đổi schema = phải báo cả nhóm và Người A duyệt (xem docs/02_PHAN_CONG.md, A1).
"""
from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


# ---------- Dùng chung ----------

class Message(BaseModel):
    sender_id: str
    timestamp_ms: int = Field(..., description="Unix time, mili giây")
    text: str | None = Field(None, description="Có thể bỏ trống nếu chỉ gửi siêu dữ liệu")


class GraduationStatus(str, Enum):
    student = "student"
    alumni = "alumni"


class Profile(BaseModel):
    user_id: str
    school_id: str
    faculty_id: str | None = None
    gpa_tier: int = Field(..., ge=0, le=3, description="0 = thấp nhất, 3 = cao nhất")
    graduation_status: GraduationStatus
    age: int = Field(..., ge=16, le=100)
    gender: str
    seeking_genders: list[str]
    interest_tags: list[str] = []
    lat: float
    lon: float


class Preferences(BaseModel):
    """Bộ lọc cứng của người xem (Module 1, bản luật)."""
    age_min: int = 18
    age_max: int = 35
    max_distance_km: float = 30.0
    school_ids: list[str] | None = Field(None, description="None = mọi trường")
    max_gpa_tier_gap: int | None = Field(None, ge=0, le=3, description="None = không lọc theo GPA")
    graduation_statuses: list[GraduationStatus] | None = None


# ---------- Module 1: xếp hạng Swipe (bản luật) ----------

class RankRequest(BaseModel):
    viewer: Profile
    preferences: Preferences = Preferences()
    candidates: list[Profile]


class RankedCandidate(BaseModel):
    user_id: str
    score: float
    distance_km: float
    common_tags: list[str]


class FilteredCandidate(BaseModel):
    user_id: str
    reason: str


class RankResponse(BaseModel):
    ranked: list[RankedCandidate]
    filtered_out: list[FilteredCandidate]
    model_version: str


# ---------- Module 2: độ mờ ảnh (bản luật) ----------

class BlurRequest(BaseModel):
    n_messages: int = Field(..., ge=0, description="Tổng số tin của cả hai bên kể từ khi match")
    days_since_match: float = Field(..., ge=0)
    compat: float | None = Field(None, ge=0, le=1, description="Độ hợp (khi Module 2 có mô hình); None = chỉ dùng luật")


class BlurResponse(BaseModel):
    blur_level: float = Field(..., ge=0, le=1, description="1 = mờ hoàn toàn, 0 = rõ hoàn toàn")
    blur_radius_px: float
    revealed: bool
    model_version: str


# ---------- Module 3 Head A: hội thoại sắp tắt ----------

class HealthLevel(str, Enum):
    healthy = "healthy"
    shallow = "shallow"
    at_risk = "at_risk"


class HealthScoreRequest(BaseModel):
    conversation_id: str
    viewer_id: str = Field(..., description="Người sẽ nhìn thấy gợi ý (gợi ý luôn riêng tư)")
    messages: list[Message]
    now_ms: int = Field(..., description="Thời điểm dự đoán; chỉ dùng tin có timestamp <= now_ms")


class HealthScoreResponse(BaseModel):
    p_disengage: float = Field(..., ge=0, le=1)
    level: HealthLevel
    top_factors: list[str]
    suggestions: list[str]
    model_version: str


# ---------- Module 3 Head B: hội thoại sắp độc hại ----------

class SafetyScoreRequest(BaseModel):
    conversation_id: str
    context: list[Message] = Field([], description="Các tin trước đó")
    draft: Message = Field(..., description="Tin đang soạn, chưa gửi")


class SafetyScoreResponse(BaseModel):
    toxicity: float = Field(..., ge=0, le=1, description="Độ độc hại của tin đang soạn")
    derail_risk: float = Field(..., ge=0, le=1, description="Nguy cơ cả cuộc chat chuyển sang công kích")
    should_nudge: bool = Field(..., description="True → app hỏi người gửi 'Bạn có chắc muốn gửi?'")
    model_version: str
