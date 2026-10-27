"""Backend DEMO cho app (môn Mobile): hồ sơ mẫu, swipe, match, chat — lưu trong bộ nhớ.

Chỉ để app chạy end-to-end khi chưa có backend thật. Mọi dữ liệu mất khi tắt server.
Logic AI (xếp hạng, blur, sức khỏe hội thoại, an toàn) gọi đúng các hàm của AI Service,
nên khi Module 3 có mô hình thật, màn hình app tự dùng mô hình mới.
"""
from __future__ import annotations

import itertools
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from serving.predictors import SUGGESTIONS, get_health_predictor, level_from_p
from serving.rules import blur_level, rank_candidates
from serving.schemas import (
    BlurRequest,
    BlurResponse,
    HealthLevel,
    HealthScoreResponse,
    Message,
    Preferences,
    Profile,
)

router = APIRouter(prefix="/app", tags=["demo-backend"])

ME_ID = "u_me"
DAY_MS = 86_400_000

SCHOOLS = {"ptit": "Học viện Công nghệ Bưu chính Viễn thông", "hust": "Đại học Bách khoa Hà Nội",
           "neu": "Đại học Kinh tế Quốc dân", "uet": "Đại học Công nghệ - ĐHQGHN", "ftu": "Đại học Ngoại thương"}
FACULTIES = {"cntt": "Công nghệ thông tin", "attt": "An toàn thông tin", "dtvt": "Điện tử viễn thông",
             "mkt": "Marketing", "ktpt": "Kinh tế phát triển", "nn": "Ngôn ngữ Anh"}


class PublicProfile(BaseModel):
    user_id: str
    nickname: str
    school_name: str
    faculty_name: str
    gpa_tier: int
    graduation_status: str
    age: int
    interest_tags: list[str]
    bio: str


class CandidateCard(PublicProfile):
    distance_km: float
    common_tags: list[str]
    score: float


class MeResponse(BaseModel):
    profile: Profile
    public: PublicProfile


class SwipeRequest(BaseModel):
    target_id: str
    like: bool


class SwipeResponse(BaseModel):
    matched: bool
    match_id: str | None = None


class SendRequest(BaseModel):
    text: str


class MatchState(BaseModel):
    blur: BlurResponse
    health: HealthScoreResponse
    compat: float


class MatchSummary(BaseModel):
    match_id: str
    other: PublicProfile
    last_message: Message | None
    blur: BlurResponse


# ---------------- Dữ liệu mẫu ----------------

def _p(uid, nick, school, fac, gpa, grad, age, gender, seek, tags, lat, lon, bio):
    profile = Profile(user_id=uid, school_id=school, faculty_id=fac, gpa_tier=gpa, graduation_status=grad,
                      age=age, gender=gender, seeking_genders=seek, interest_tags=tags, lat=lat, lon=lon)
    public = PublicProfile(user_id=uid, nickname=nick, school_name=SCHOOLS[school], faculty_name=FACULTIES[fac],
                           gpa_tier=gpa, graduation_status=grad, age=age, interest_tags=tags, bio=bio)
    return profile, public


SEED = [
    _p(ME_ID, "Bạn", "ptit", "cntt", 2, "student", 21, "m", ["f"], ["âm nhạc", "phim", "du lịch", "lập trình"],
       20.981, 105.787, "Thích code và nghe nhạc indie."),
    _p("u1", "Mây", "ptit", "attt", 2, "student", 20, "f", ["m"], ["âm nhạc", "phim", "mèo"],
       20.983, 105.790, "Cuối tuần hay đi xem phim một mình."),
    _p("u2", "Gió", "hust", "dtvt", 3, "student", 21, "f", ["m"], ["lập trình", "du lịch", "leo núi"],
       21.005, 105.843, "Đang tập leo núi trong nhà."),
    _p("u3", "Sương", "neu", "mkt", 1, "alumni", 24, "f", ["m"], ["cà phê", "phim", "nhiếp ảnh"],
       21.000, 105.842, "Làm marketing, nghiện cà phê."),
    _p("u4", "Nắng", "uet", "cntt", 2, "student", 22, "f", ["m"], ["âm nhạc", "lập trình", "game"],
       21.038, 105.783, "Chơi guitar và làm app nhỏ."),
    _p("u5", "Mưa", "ftu", "nn", 3, "alumni", 25, "f", ["m"], ["du lịch", "đọc sách"],
       21.023, 105.806, "Đã đi 20 tỉnh, mục tiêu 63."),
    _p("u6", "Trăng", "ptit", "mkt", 0, "student", 19, "f", ["m", "f"], ["nhảy", "âm nhạc"],
       20.980, 105.786, "Thành viên CLB nhảy."),
    _p("u7", "Sao", "hust", "cntt", 2, "student", 20, "f", ["f"], ["lập trình"],
       21.006, 105.844, "Tìm bạn cùng học thuật toán."),
    _p("u8", "Biển", "neu", "ktpt", 1, "student", 33, "f", ["m"], ["chạy bộ", "phim"],
       21.000, 105.843, "Sáng nào cũng chạy 5 km."),
]

BOT_REPLIES = ["Chào bạn, rất vui được match nha!", "Mình cũng thích vậy đó, bạn hay làm gì cuối tuần?",
               "Nghe hay ghê, kể thêm đi.", "Haha thật hả?", "Ừm", "ok", "ừ"]


class Store:
    def __init__(self) -> None:
        self.reset()

    def reset(self) -> None:
        self.profiles = {p.user_id: p for p, _ in SEED}
        self.public = {pub.user_id: pub for _, pub in SEED}
        self.seen: set[str] = set()
        self.matches: dict[str, dict] = {}
        self._ids = itertools.count(1)


store = Store()


def _now_ms() -> int:
    return int(time.time() * 1000)


def _match(match_id: str) -> dict:
    if match_id not in store.matches:
        raise HTTPException(404, "match not found")
    return store.matches[match_id]


def _blur_for(m: dict) -> BlurResponse:
    days = (_now_ms() - m["created_ms"]) / DAY_MS
    return blur_level(BlurRequest(n_messages=len(m["messages"]), days_since_match=days))


# ---------------- API ----------------

@router.post("/reset")
def reset() -> dict:
    store.reset()
    return {"status": "reset"}


@router.get("/me", response_model=MeResponse)
def me() -> MeResponse:
    return MeResponse(profile=store.profiles[ME_ID], public=store.public[ME_ID])


@router.get("/candidates", response_model=list[CandidateCard])
def candidates() -> list[CandidateCard]:
    viewer = store.profiles[ME_ID]
    matched = {m["other_id"] for m in store.matches.values()}
    pool = [p for uid, p in store.profiles.items() if uid not in store.seen and uid not in matched]
    ranked, _ = rank_candidates(viewer, Preferences(), pool)
    return [CandidateCard(**store.public[r.user_id].model_dump(), distance_km=r.distance_km,
                          common_tags=r.common_tags, score=r.score) for r in ranked]


@router.post("/swipe", response_model=SwipeResponse)
def swipe(req: SwipeRequest) -> SwipeResponse:
    if req.target_id not in store.profiles or req.target_id == ME_ID:
        raise HTTPException(404, "unknown user")
    store.seen.add(req.target_id)
    me_tags = set(store.profiles[ME_ID].interest_tags)
    # Demo: đối phương "thích lại" nếu có ít nhất 1 sở thích chung
    if not req.like or not me_tags & set(store.profiles[req.target_id].interest_tags):
        return SwipeResponse(matched=False)
    match_id = f"m{next(store._ids)}"
    store.matches[match_id] = {"other_id": req.target_id, "created_ms": _now_ms(), "messages": []}
    return SwipeResponse(matched=True, match_id=match_id)


@router.get("/matches", response_model=list[MatchSummary])
def matches() -> list[MatchSummary]:
    out = []
    for mid, m in store.matches.items():
        last = m["messages"][-1] if m["messages"] else None
        out.append(MatchSummary(match_id=mid, other=store.public[m["other_id"]], last_message=last, blur=_blur_for(m)))
    return out


@router.get("/matches/{match_id}/messages", response_model=list[Message])
def messages(match_id: str) -> list[Message]:
    return _match(match_id)["messages"]


@router.post("/matches/{match_id}/messages", response_model=list[Message])
def send(match_id: str, req: SendRequest) -> list[Message]:
    m = _match(match_id)
    now = _now_ms()
    m["messages"].append(Message(sender_id=ME_ID, timestamp_ms=now, text=req.text))
    n_bot = sum(1 for x in m["messages"] if x.sender_id != ME_ID)
    reply = BOT_REPLIES[min(n_bot, len(BOT_REPLIES) - 1)]  # bot trả lời ngắn dần để demo cảnh báo
    m["messages"].append(Message(sender_id=m["other_id"], timestamp_ms=now + 1000, text=reply))
    return m["messages"]


@router.get("/matches/{match_id}/state", response_model=MatchState)
def state(match_id: str) -> MatchState:
    m = _match(match_id)
    predictor = get_health_predictor()
    p, factors = predictor.score(m["messages"], _now_ms() + 2000, ME_ID)
    level = level_from_p(p)
    health = HealthScoreResponse(
        p_disengage=round(p, 4), level=level, top_factors=factors, model_version=predictor.version,
        suggestions=[] if level == HealthLevel.healthy else [SUGGESTIONS[f] for f in factors if f in SUGGESTIONS],
    )
    # Độ hợp DEMO cho quả cầu (Module 2 chưa có mô hình): tăng theo số tin, giảm khi hội thoại nhạt
    compat = round(min(1.0, 0.2 + 0.02 * len(m["messages"])) * (1 - 0.5 * p), 3)
    return MatchState(blur=_blur_for(m), health=health, compat=compat)
