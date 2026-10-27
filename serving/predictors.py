"""Bộ dự đoán Module 3 — chỗ "cắm" mô hình.

Hiện tại dùng **baseline bằng luật** (chính là baseline trong docs/06 §3.2),
nên app chạy được ngay từ đầu. Khi có mô hình thật:
  1. viết class mới có cùng phương thức `score(...)` và thuộc tính `version`,
  2. đổi `get_health_predictor()` / `get_safety_predictor()` trả về class đó.
App và backend không cần sửa gì.
"""
from __future__ import annotations

import re
import unicodedata
from functools import lru_cache

from serving.schemas import HealthLevel, Message

# Ngưỡng 3 mức (sẽ chọn lại trên validation khi có mô hình thật — docs/06 §3.4)
TAU1, TAU2 = 0.3, 0.6
# Ngưỡng nhắc nhẹ người gửi (sẽ chọn theo FPR — docs/06 §3.4)
TAU_NUDGE = 0.5

SHORT_REPLIES = {"ừ", "uk", "ok", "oke", "okela", "ờ", "ừm", "hihi", "haha", "hì", "vâng", "dạ",
                 "k", "lol", "yeah", "sure"}
QUESTION_ENDINGS_VI = ("không", "chưa", "à", "hả", "nhỉ", "sao", "gì", "nào", "đâu", "bao giờ", "mấy")

# Từ điển rất nhỏ chỉ để baseline chạy được; mô hình thật là bộ phân loại của C (docs/06 T5)
TOXIC_TERMS = {
    "ngu", "đần", "óc chó", "đm", "dm", "đmm", "vl", "vcl", "cút", "câm mồm", "mất dạy",
    "stupid", "idiot", "shut up", "fuck", "bitch", "loser",
}

SUGGESTIONS = {
    "long_silence": "Đã lâu chưa ai nhắn, thử hỏi thăm một câu nhẹ nhàng nhé.",
    "short_replies": "Cuộc trò chuyện đang ngắn dần, thử một câu hỏi mở về sở thích chung.",
    "shrinking_messages": "Thử kể một chuyện nhỏ hôm nay của bạn để mở chủ đề mới.",
    "one_sided_questions": "Hãy hỏi lại đối phương để cuộc trò chuyện hai chiều hơn.",
}


def _words(text: str | None) -> list[str]:
    if not text:
        return []
    return re.findall(r"\w+", unicodedata.normalize("NFC", text.lower()))


def is_short_reply(text: str | None) -> bool:
    words = _words(text)
    return len(words) <= 1 or " ".join(words) in SHORT_REPLIES


def is_question(text: str | None) -> bool:
    if not text:
        return False
    return "?" in text or " ".join(_words(text)).endswith(QUESTION_ENDINGS_VI)


def toxicity_score(text: str | None) -> float:
    padded = f" {' '.join(_words(text))} "
    hits = sum(1 for term in TOXIC_TERMS if f" {term} " in padded)
    return min(1.0, 0.6 * hits)


class RuleHealthPredictor:
    """Baseline Head A: thời gian im lặng, tin ngắn dần, câu hỏi một chiều."""

    version = "health-rule-0.1"

    def score(self, messages: list[Message], now_ms: int, viewer_id: str) -> tuple[float, list[str]]:
        past = sorted((m for m in messages if m.timestamp_ms <= now_ms), key=lambda m: m.timestamp_ms)
        if not past:
            return 0.5, ["no_messages"]
        factors: list[str] = []
        silence_h = (now_ms - past[-1].timestamp_ms) / 3_600_000
        p = 0.6 * min(silence_h / 48.0, 1.0)  # im lặng 48h → 0.6
        if silence_h >= 24:
            factors.append("long_silence")

        last = past[-6:]
        if sum(is_short_reply(m.text) for m in last) >= 3:
            p += 0.2
            factors.append("short_replies")
        lengths = [len(_words(m.text)) for m in last]
        if len(lengths) >= 4 and lengths[-1] < lengths[0] and lengths[-2] < lengths[1]:
            p += 0.1
            factors.append("shrinking_messages")
        askers = {m.sender_id for m in last if is_question(m.text)}
        if len(askers) == 1 and len({m.sender_id for m in last}) == 2:
            p += 0.1
            factors.append("one_sided_questions")
        return min(p, 1.0), factors


class RuleSafetyPredictor:
    """Baseline Head B: từ điển từ xúc phạm cho tin đang soạn + vài tin gần nhất."""

    version = "safety-rule-0.1"

    def score(self, context: list[Message], draft: Message) -> tuple[float, float]:
        tox = toxicity_score(draft.text)
        recent = [toxicity_score(m.text) for m in context[-5:]]
        derail = min(1.0, 0.5 * tox + 0.5 * max(recent, default=0.0))
        return tox, derail


def level_from_p(p: float) -> HealthLevel:
    if p < TAU1:
        return HealthLevel.healthy
    if p < TAU2:
        return HealthLevel.shallow
    return HealthLevel.at_risk


@lru_cache
def get_health_predictor() -> RuleHealthPredictor:
    return RuleHealthPredictor()


@lru_cache
def get_safety_predictor() -> RuleSafetyPredictor:
    return RuleSafetyPredictor()
