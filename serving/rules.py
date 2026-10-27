"""Bản luật (không ML) cho Module 1 (xếp hạng Swipe) và Module 2 (độ mờ ảnh).

Dùng cho app môn Mobile. Khi có mô hình ML, giữ nguyên chữ ký hàm.
"""
from __future__ import annotations

import math

from serving.schemas import (
    BlurRequest,
    BlurResponse,
    FilteredCandidate,
    Preferences,
    Profile,
    RankedCandidate,
)

RULES_VERSION = "rules-0.1"

# Module 2: tham số luật blur
BLUR_FULL_MESSAGES = 60      # số tin để phần "tin nhắn" giảm hết
BLUR_FULL_DAYS = 7.0         # số ngày để phần "thời gian" giảm hết
BLUR_WEIGHT_MESSAGES = 0.6
BLUR_WEIGHT_DAYS = 0.4
REVEAL_MIN_MESSAGES = 30     # chống reveal quá sớm
MIN_BLUR_BEFORE_REVEAL = 0.05
MAX_BLUR_RADIUS_PX = 25.0


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def filter_reason(viewer: Profile, prefs: Preferences, cand: Profile, dist_km: float) -> str | None:
    """Trả về lý do loại ứng viên, hoặc None nếu ứng viên hợp lệ."""
    if cand.user_id == viewer.user_id:
        return "self"
    if cand.gender not in viewer.seeking_genders or viewer.gender not in cand.seeking_genders:
        return "gender_mismatch"
    if not prefs.age_min <= cand.age <= prefs.age_max:
        return "age"
    if dist_km > prefs.max_distance_km:
        return "distance"
    if prefs.school_ids is not None and cand.school_id not in prefs.school_ids:
        return "school"
    if prefs.max_gpa_tier_gap is not None and abs(cand.gpa_tier - viewer.gpa_tier) > prefs.max_gpa_tier_gap:
        return "gpa_tier"
    if prefs.graduation_statuses is not None and cand.graduation_status not in prefs.graduation_statuses:
        return "graduation_status"
    return None


def rank_candidates(
    viewer: Profile, prefs: Preferences, candidates: list[Profile]
) -> tuple[list[RankedCandidate], list[FilteredCandidate]]:
    ranked, filtered = [], []
    viewer_tags = set(viewer.interest_tags)
    for cand in candidates:
        dist = haversine_km(viewer.lat, viewer.lon, cand.lat, cand.lon)
        reason = filter_reason(viewer, prefs, cand, dist)
        if reason:
            filtered.append(FilteredCandidate(user_id=cand.user_id, reason=reason))
            continue
        common = sorted(viewer_tags & set(cand.interest_tags))
        # Điểm luật: tag trùng là chính, cùng trường và ở gần cộng thêm một chút
        score = (
            len(common)
            + 0.5 * (cand.school_id == viewer.school_id)
            + 0.5 * max(0.0, 1 - dist / prefs.max_distance_km)
        )
        ranked.append(RankedCandidate(user_id=cand.user_id, score=round(score, 4),
                                      distance_km=round(dist, 2), common_tags=common))
    ranked.sort(key=lambda r: (-r.score, r.distance_km, r.user_id))
    return ranked, filtered


def blur_level(req: BlurRequest) -> BlurResponse:
    msg_part = min(req.n_messages / BLUR_FULL_MESSAGES, 1.0)
    day_part = min(req.days_since_match / BLUR_FULL_DAYS, 1.0)
    progress = BLUR_WEIGHT_MESSAGES * msg_part + BLUR_WEIGHT_DAYS * day_part
    if req.compat is not None:
        # Khi có độ hợp từ Module 2: độ hợp thấp làm chậm tiến độ rõ ảnh
        progress *= 0.5 + 0.5 * req.compat
    level = max(0.0, 1.0 - progress)
    revealed = level == 0.0 and req.n_messages >= REVEAL_MIN_MESSAGES
    if not revealed:
        level = max(level, MIN_BLUR_BEFORE_REVEAL)  # chưa đủ điều kiện thì không bao giờ rõ hẳn
    return BlurResponse(
        blur_level=round(level, 4),
        blur_radius_px=round(level * MAX_BLUR_RADIUS_PX, 2),
        revealed=revealed,
        model_version=RULES_VERSION,
    )
