"""Deterministic fallback rules for M1/M2/M3.

These rules intentionally mirror the Kotlin engines used by the Android demo so
that the app shows the same numbers whether it runs offline or talks to this
FastAPI service.  When a trained artifact is present the registry can replace
the result and set ``model_source = "trained_model"``.
"""

from __future__ import annotations

import math
import time
from collections.abc import Iterable

from serving.schemas_mobile import (
    AiMessage,
    AiProfile,
    CompatRequest,
    CompatResponse,
    HealthRequest,
    HealthResponse,
    RankItem,
    RankRequest,
    RankResponse,
)

FALLBACK_SOURCE = "fallback_rules"
TRAINED_SOURCE = "trained_model"

GPA_ORDER = {
    "EXCELLENT": 0,
    "GOOD": 1,
    "FAIR": 2,
    "AVERAGE": 3,
}

POSITIVE_WORDS = {
    "vui",
    "thích",
    "hay",
    "đẹp",
    "cười",
    "haha",
    "hehe",
    "cute",
    "ngon",
    "thú vị",
    "tuyệt",
    "ổn",
    "chill",
    "yêu",
    "giỏi",
    "tốt",
    "hạnh phúc",
}

NEGATIVE_WORDS = {
    "buồn",
    "chán",
    "mệt",
    "khó",
    "ghét",
    "bực",
    "căng",
    "lo",
    "không thích",
    "chẳng",
}

DRY_REPLIES = {"chán", "mệt", "buồn", "ừ", "ừm", "ok", "không có gì"}

MIN_MESSAGES_TO_REVEAL = 8
REVEAL_SCORE = 72
MIN_HEALTH_MESSAGES = 4


def _round_half_up(value: float) -> int:
    """Match Kotlin's ``roundToInt`` instead of Python's banker's rounding."""

    return int(math.floor(value + 0.5))


def _clamp(value: float, low: float, high: float) -> float:
    return min(max(value, low), high)


def _normalise_interest(value: str) -> str:
    return " ".join(value.casefold().split())


def _shared_interests(first: Iterable[str], second: Iterable[str]) -> list[str]:
    second_set = {_normalise_interest(item) for item in second}
    result: list[str] = []
    seen: set[str] = set()
    for item in first:
        key = _normalise_interest(item)
        if key and key in second_set and key not in seen:
            seen.add(key)
            result.append(item.strip())
    return result


def _gpa_rank(value: str) -> int:
    return GPA_ORDER.get((value or "").strip().upper(), 1)


def _rank_score(viewer: AiProfile, candidate: AiProfile) -> tuple[int, list[str]]:
    shared = _shared_interests(viewer.interests, candidate.interests)
    viewer_keys = {_normalise_interest(item) for item in viewer.interests if item.strip()}
    candidate_keys = {_normalise_interest(item) for item in candidate.interests if item.strip()}
    union = viewer_keys | candidate_keys
    jaccard = len(shared) / len(union) if union else 0.0

    tier_diff = abs(_gpa_rank(viewer.gpa_tier) - _gpa_rank(candidate.gpa_tier))
    gpa_score = {0: 1.0, 1: 0.72, 2: 0.44}.get(tier_diff, 0.2)
    age_diff = abs(viewer.age - candidate.age)
    age_score = _clamp(1.0 - age_diff / 6.0, 0.0, 1.0)
    distance_score = _clamp(1.0 - candidate.distance_km / 50.0, 0.0, 1.0)
    same_school = viewer.school.casefold() == candidate.school.casefold() and bool(viewer.school)
    same_campus = viewer.campus.casefold() == candidate.campus.casefold() and bool(viewer.campus)
    same_faculty = viewer.faculty.casefold() == candidate.faculty.casefold() and bool(viewer.faculty)
    same_mode = viewer.mode.casefold() == candidate.mode.casefold()

    raw = (
        jaccard * 32.0
        + gpa_score * 20.0
        + (9.0 if same_school else 0.0)
        + (4.0 if same_campus else 0.0)
        + (5.0 if same_faculty else 0.0)
        + age_score * 10.0
        + distance_score * 10.0
        + (10.0 if same_mode else 3.5)
    )
    score = _round_half_up(_clamp(raw + 5.0, 12.0, 99.0))

    reasons: list[str] = []
    if shared:
        reasons.append(f"{len(shared)} sở thích chung")
    if same_campus:
        reasons.append("Cùng campus")
    elif same_school:
        reasons.append("Cùng trường")
    if tier_diff == 0:
        reasons.append("Cùng tầng GPA")
    elif tier_diff == 1:
        reasons.append("Gần tầng GPA")
    if same_faculty:
        reasons.append("Cùng khoa")
    if candidate.distance_km <= 3.0:
        reasons.append(f"Cách bạn {candidate.distance_km:.1f} km")
    if not same_mode:
        reasons.append("Khác chế độ kết nối")
    if not reasons:
        reasons.append("Hồ sơ phù hợp để bắt đầu trò chuyện")
    return score, reasons


def rank(request: RankRequest) -> RankResponse:
    items = [
        RankItem(
            profile_id=candidate.id,
            score=score,
            reasons=reasons,
            model_source=FALLBACK_SOURCE,
        )
        for candidate in request.candidates
        for score, reasons in [_rank_score(request.viewer, candidate)]
    ]
    items.sort(key=lambda item: item.score, reverse=True)
    if request.top_k is not None:
        items = items[: request.top_k]
    return RankResponse(items=items, model_source=FALLBACK_SOURCE)


def compatibility(request: CompatRequest) -> CompatResponse:
    chat = [message for message in request.messages if message.sender != "system"]
    count = len(chat)
    shared = _shared_interests(request.viewer_interests, request.partner_interests)
    viewer_keys = {_normalise_interest(item) for item in request.viewer_interests if item.strip()}
    partner_keys = {_normalise_interest(item) for item in request.partner_interests if item.strip()}
    union = viewer_keys | partner_keys
    overlap = len(shared) / len(union) if union else 0.0

    text = " ".join(message.text.casefold() for message in chat)
    shared_mentions = sum(1 for item in shared if _normalise_interest(item) in text)
    positive = sum(1 for word in POSITIVE_WORDS if word in text)
    negative = sum(1 for word in NEGATIVE_WORDS if word in text)
    questions = sum(1 for message in chat if "?" in message.text)
    viewer_count = sum(1 for message in chat if message.sender == "viewer")
    partner_count = count - viewer_count
    balance = 1.0 - abs(viewer_count - partner_count) / count if count else 0.0
    avg_length = sum(len(message.text) for message in chat) // count if count else 0

    engagement = _clamp(
        balance * 32.0
        + min(questions / max(count, 1) * 26.0, 26.0)
        + min(avg_length / 60.0 * 20.0, 20.0)
        + min(count / 24.0 * 22.0, 22.0),
        0.0,
        100.0,
    )
    interest_axis = _clamp(overlap * 58.0 + shared_mentions * 8.0 + 32.0, 15.0, 99.0)
    emotion_axis = _clamp(52.0 + positive * 7.0 - negative * 9.0 + engagement * 0.2, 15.0, 99.0)
    personality_axis = _clamp(
        56.0 + overlap * 26.0 + engagement * 0.18,
        15.0,
        99.0,
    )
    raw = (
        interest_axis * 0.34
        + emotion_axis * 0.30
        + personality_axis * 0.26
        + engagement * 0.10
    )
    if count < 3:
        penalty = 24.0
    elif count < 6:
        penalty = 14.0
    elif count < 9:
        penalty = 5.0
    else:
        penalty = 0.0
    score = _round_half_up(_clamp(raw - penalty, 8.0, 99.0))

    both_opted_in = request.viewer_opted_in and request.partner_opted_in
    reveal_eligible = both_opted_in and count >= request.min_messages and score >= REVEAL_SCORE
    if reveal_eligible:
        blur = 0.0
    elif both_opted_in:
        blur = _clamp(1.0 - (score - 20) / 82.0, 0.12, 0.92)
    else:
        blur = _clamp(1.0 - score / 135.0, 0.55, 0.96)

    return CompatResponse(
        score=score,
        blur_level=round(blur, 4),
        personality=_round_half_up(personality_axis),
        emotion=_round_half_up(emotion_axis),
        interest=_round_half_up(interest_axis),
        message_count=count,
        reveal_eligible=reveal_eligible,
        model_source=FALLBACK_SOURCE,
    )


def _gap_label(hours: float) -> str:
    if hours >= 48:
        return f"{int(hours / 24)} ngày"
    if hours >= 24:
        return "1 ngày"
    return f"{int(hours)} giờ"


def _health_suggestions(
    shared: list[str],
    chat: list[AiMessage],
    *,
    low_activity: bool,
    long_gap: bool,
) -> list[str]:
    suggestions: list[str] = []
    if long_gap:
        suggestions.append(
            'Nhắn nhẹ một câu: "Dạo này cậu thế nào? Mình vừa thấy một thứ chắc cậu thích."'
        )
    if shared:
        suggestions.append(f"Khai thác sở thích chung: {', '.join(shared[:2])}")
    asked_question = bool(chat and "?" in chat[-1].text)
    if not asked_question:
        suggestions.append('Hỏi một câu mở: "Điều gì làm cậu thấy hào hứng nhất tuần này?"')
    suggestions.append(
        "Chia sẻ một chuyện nhỏ vừa xảy ra trong ngày thay vì chỉ trả lời"
    )
    suggestions.append(
        "Gợi ý một hoạt động cụ thể: cà phê, học nhóm hoặc một buổi đi bộ"
    )
    unique: list[str] = []
    for item in suggestions:
        if item not in unique:
            unique.append(item)
    return unique[:3]


def health(request: HealthRequest) -> HealthResponse:
    chat = [message for message in request.messages if message.sender != "system"]
    shared = _shared_interests(request.viewer_interests, request.partner_interests)
    if len(chat) < MIN_HEALTH_MESSAGES:
        suggestions = _health_suggestions(
            shared,
            chat,
            low_activity=True,
            long_gap=False,
        )
        return HealthResponse(
            level="FADING",
            score=38,
            reasons=[
                "Chưa đủ tin nhắn để phân tích sâu",
                "Hãy thử một câu hỏi mở",
            ],
            suggestions=suggestions,
            message_count=len(chat),
            model_source=FALLBACK_SOURCE,
        )

    window = chat[-10:]
    viewer_count = sum(1 for message in window if message.sender == "viewer")
    partner_count = len(window) - viewer_count
    balance = 1.0 - abs(viewer_count - partner_count) / len(window)
    avg_length = sum(len(message.text) for message in window) / len(window)
    question_ratio = sum(1 for message in window if "?" in message.text) / len(window)
    short_ratio = sum(1 for message in window if len(message.text.strip()) <= 4) / len(window)
    last = chat[-1]
    now = request.now if request.now is not None else int(time.time() * 1000)
    gap_hours = max((now - last.created_at) / 3_600_000.0, 0.0)
    negative = (
        sum(
            1
            for message in window
            if message.text.strip().casefold() in DRY_REPLIES
        )
        / len(window)
    )

    risk = 10.0
    if gap_hours >= 48:
        risk += 34.0
    elif gap_hours >= 24:
        risk += 22.0
    elif gap_hours >= 8:
        risk += 10.0
    if short_ratio > 0.45:
        risk += 20.0
    if question_ratio < 0.12:
        risk += 14.0
    if balance < 0.35:
        risk += 16.0
    if avg_length < 13:
        risk += 12.0
    if negative > 0.4:
        risk += 12.0
    if len(window) >= 12 and question_ratio > 0.2 and balance > 0.55:
        risk -= 10.0

    score = _round_half_up(_clamp(risk, 0.0, 100.0))
    if score >= 64:
        level = "AT_RISK"
    elif score >= 36:
        level = "FADING"
    else:
        level = "HEALTHY"

    reasons: list[str] = []
    if gap_hours >= 24:
        reasons.append(f"Tin nhắn cuối đã {_gap_label(gap_hours)}")
    if short_ratio > 0.45:
        reasons.append("Nhiều tin nhắn quá ngắn, dễ tạo cảm giác hời hợt")
    if question_ratio < 0.12:
        reasons.append("Ít câu hỏi mở, cuộc trò chuyện chưa có điểm bật")
    if balance < 0.35:
        reasons.append("Một bên đang nhắn nhiều hơn hẳn")
    if avg_length < 13:
        reasons.append("Độ dài tin nhắn đang giảm")
    if negative > 0.4:
        reasons.append("Có nhiều câu trả lời cụt hoặc thiếu năng lượng")
    if not reasons:
        reasons.append("Hai bạn đang giữ nhịp trò chuyện khá tốt")

    return HealthResponse(
        level=level,
        score=score,
        reasons=reasons,
        suggestions=_health_suggestions(
            shared,
            window,
            low_activity=score >= 36,
            long_gap=gap_hours >= 24,
        ),
        message_count=len(chat),
        model_source=FALLBACK_SOURCE,
    )
