"""Định dạng JSON (schema) của Module 3 (bài nộp môn Học máy): /health/score, /safety/score.

Đầu vào là danh sách tin nhắn có thời gian — đúng dạng dữ liệu dùng để huấn luyện.
Khi Module 3 có mô hình thật, chỉ thay phần dự đoán phía sau, schema giữ nguyên.
Schema của app android/ nằm ở serving/schemas_mobile.py.
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
