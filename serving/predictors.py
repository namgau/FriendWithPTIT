"""Bộ dự đoán Module 3 — chỗ "cắm" mô hình.

Bao gồm:
- RuleHealthPredictor / RuleSafetyPredictor: baseline bằng luật (fallback an toàn).
- TrainedHealthPredictor: trích xuất đặc trưng T1/T2, gọi mô hình thật cho Head A.
- TrainedSafetyPredictor: trích xuất đặc trưng văn bản, gọi mô hình thật cho Head B.
- Dynamic thresholds (tau1, tau2, tau_nudge) nạp trực tiếp từ metadata.json.
"""
from __future__ import annotations

import re
import unicodedata
from functools import lru_cache

import numpy as np
import pandas as pd

from serving.model_registry import ModelRegistry
from serving.schemas import HealthLevel, Message

# Ngưỡng mặc định (docs/06 §3.4)
TAU1, TAU2 = 0.3, 0.6
TAU_NUDGE = 0.5

SHORT_REPLIES = {
    "ừ", "uk", "ok", "oke", "okela", "ờ", "ừm", "hihi", "haha", "hì", "vâng", "dạ",
    "k", "lol", "yeah", "sure", "kk", "yes", "yep", "no", "thanks", "thx", "ty",
}
QUESTION_ENDINGS_VI = (
    "không", "chưa", "à", "hả", "nhỉ", "sao", "gì", "nào", "đâu", "bao giờ", "mấy",
)

TOXIC_TERMS = {
    "ngu", "đần", "óc chó", "đm", "dm", "đmm", "vl", "vcl", "cút", "câm mồm", "mất dạy",
    "stupid", "idiot", "shut up", "fuck", "bitch", "loser", "moron", "hate",
}

SUGGESTIONS = {
    "long_silence": "Đã lâu chưa ai nhắn, thử hỏi thăm một câu nhẹ nhàng nhé.",
    "short_replies": "Cuộc trò chuyện đang ngắn dần, thử một câu hỏi mở về sở thích chung.",
    "shrinking_messages": "Thử kể một chuyện nhỏ hôm nay của bạn để mở chủ đề mới.",
    "one_sided_questions": "Hãy hỏi lại đối phương để cuộc trò chuyện hai chiều hơn.",
}

FEATURE_NAMES_A = [
    "n_msgs", "conv_age_log", "gap_last", "gap_mean", "gap_max", "gap_std", "gap_slope",
    "gap_rel", "burstiness", "hour", "weekend", "night", "share_last_sender", "double_text",
    "words_last", "words_mean", "words_slope", "short_share", "short_last", "question_share", "question_last",
]

NUMERIC_COLUMNS_B = [
    "n_messages", "n_senders", "sender_switch", "span_log", "last_gap_log",
    "n_chars", "n_words", "mean_word_len", "n_questions", "n_exclaims",
    "upper_ratio", "digit_ratio", "type_token_ratio", "toxic_hits",
    "last_n_chars", "last_n_words", "last_question", "last_exclaim", "last_toxic_hits",
]


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
    if not text:
        return 0.0
    padded = f" {' '.join(_words(text))} "
    hits = sum(1 for term in TOXIC_TERMS if f" {term} " in padded)
    return min(1.0, 0.6 * hits)


def toxicity_hits(text: str | None) -> int:
    if not text:
        return 0
    padded = f" {' '.join(_words(text))} "
    return sum(1 for term in TOXIC_TERMS if f" {term} " in padded)


def _calc_slope(values: np.ndarray) -> float:
    n = len(values)
    if n < 2:
        return 0.0
    x = np.arange(n, dtype=float)
    x_mean = np.mean(x)
    y_mean = np.mean(values)
    dx = x - x_mean
    denom = np.sum(dx ** 2)
    if denom == 0:
        return 0.0
    return float(np.sum(dx * (values - y_mean)) / denom)


def _calc_burstiness(gaps: np.ndarray) -> float:
    if len(gaps) == 0:
        return 0.0
    mu = float(np.mean(gaps))
    sd = float(np.std(gaps))
    if mu + sd > 0:
        return float((sd - mu) / (sd + mu))
    return 0.0


def extract_t1_t2_features(past_messages: list[Message], now_ms: int) -> pd.DataFrame:
    """Trích xuất 21 đặc trưng T1 (thời gian, nhịp điệu) và T2 (bề mặt văn bản)."""
    n = len(past_messages)
    if n == 0:
        return pd.DataFrame([{col: 0.0 for col in FEATURE_NAMES_A}])

    ts = np.array([m.timestamp_ms / 1000.0 for m in past_messages], dtype=float)
    now_s = now_ms / 1000.0
    conv_start_s = ts[0]
    conv_age_log = float(np.log1p(max(now_s - conv_start_s, 0.0)))

    silence_s = max(now_s - ts[-1], 0.0)
    gap_last = float(np.log1p(silence_s))

    if n >= 2:
        gaps_s = np.diff(ts)
        lg_gaps = np.log1p(np.maximum(gaps_s, 0.0))
        gap_mean = float(np.mean(lg_gaps))
        gap_max = float(np.max(lg_gaps))
        gap_std = float(np.std(lg_gaps))
        gap_slope = _calc_slope(lg_gaps[-10:])
        hist_gap = gap_mean
        burstiness = _calc_burstiness(gaps_s)
    else:
        gap_mean = gap_last
        gap_max = gap_last
        gap_std = 0.0
        gap_slope = 0.0
        hist_gap = gap_last
        burstiness = 0.0

    gap_rel = gap_last - hist_gap

    dt = pd.to_datetime(ts[-1], unit="s", utc=True)
    hour = float(dt.hour)
    weekend = float(dt.dayofweek >= 5)
    night = float(0 <= dt.hour < 7)

    window = past_messages[-min(10, n):]
    last_sender = window[-1].sender_id
    share_last_sender = float(np.mean([m.sender_id == last_sender for m in window]))
    double_text = float(len(window) >= 2 and window[-2].sender_id == last_sender)

    words_counts = np.array([len(_words(m.text)) for m in window], dtype=float)
    words_last = float(words_counts[-1])
    words_mean = float(np.mean(words_counts))
    words_slope = _calc_slope(words_counts)

    is_shorts = np.array([float(is_short_reply(m.text)) for m in window], dtype=float)
    short_share = float(np.mean(is_shorts))
    short_last = float(is_shorts[-1])

    is_quests = np.array([float(is_question(m.text)) for m in window], dtype=float)
    question_share = float(np.mean(is_quests))
    question_last = float(is_quests[-1])

    data = {
        "n_msgs": float(n),
        "conv_age_log": conv_age_log,
        "gap_last": gap_last,
        "gap_mean": gap_mean,
        "gap_max": gap_max,
        "gap_std": gap_std,
        "gap_slope": gap_slope,
        "gap_rel": gap_rel,
        "burstiness": burstiness,
        "hour": hour,
        "weekend": weekend,
        "night": night,
        "share_last_sender": share_last_sender,
        "double_text": double_text,
        "words_last": words_last,
        "words_mean": words_mean,
        "words_slope": words_slope,
        "short_share": short_share,
        "short_last": short_last,
        "question_share": question_share,
        "question_last": question_last,
    }
    return pd.DataFrame([data])


def derive_health_factors(past_messages: list[Message], now_ms: int) -> list[str]:
    """Xác định các yếu tố nguy cơ dựa trên quy tắc giải thích."""
    if not past_messages:
        return ["no_messages"]
    factors: list[str] = []
    silence_h = (now_ms - past_messages[-1].timestamp_ms) / 3_600_000
    if silence_h >= 24:
        factors.append("long_silence")

    last = past_messages[-6:]
    if sum(is_short_reply(m.text) for m in last) >= 3:
        factors.append("short_replies")

    lengths = [len(_words(m.text)) for m in last]
    if len(lengths) >= 4 and lengths[-1] < lengths[0] and lengths[-2] < lengths[1]:
        factors.append("shrinking_messages")

    askers = {m.sender_id for m in last if is_question(m.text)}
    if len(askers) == 1 and len({m.sender_id for m in last}) == 2:
        factors.append("one_sided_questions")

    return factors


def extract_safety_features(context: list[Message], draft: Message) -> pd.DataFrame:
    """Trích xuất bảng đặc trưng cho Head B (chuỗi hội thoại + tin nháp)."""
    all_msgs = list(context) + [draft]
    full_text = " ".join((m.text or "") for m in all_msgs).strip()
    draft_text = (draft.text or "").strip()

    words_full = _words(full_text)
    words_draft = _words(draft_text)
    letters_full = sum(ch.isalpha() for ch in full_text)
    digits_full = sum(ch.isdigit() for ch in full_text)

    ts = [m.timestamp_ms / 1000.0 for m in all_msgs]
    span_s = max(ts[-1] - ts[0], 0.0) if len(ts) >= 2 else 0.0
    last_gap_s = max(ts[-1] - ts[-2], 0.0) if len(ts) >= 2 else 0.0

    senders = [m.sender_id for m in all_msgs]
    sender_switches = [1.0 for i in range(1, len(senders)) if senders[i] != senders[i - 1]]
    sender_switch = float(len(sender_switches) / max(len(senders) - 1, 1))

    data = {
        "text": full_text,
        "last_text": draft_text,
        "n_messages": float(len(all_msgs)),
        "n_senders": float(len(set(senders))),
        "sender_switch": sender_switch,
        "span_log": float(np.log1p(span_s)),
        "last_gap_log": float(np.log1p(last_gap_s)),
        "n_chars": float(len(full_text)),
        "n_words": float(len(words_full)),
        "mean_word_len": float(np.mean([len(w) for w in words_full])) if words_full else 0.0,
        "n_questions": float(full_text.count("?")),
        "n_exclaims": float(full_text.count("!")),
        "upper_ratio": float(sum(ch.isupper() for ch in full_text) / max(letters_full, 1)),
        "digit_ratio": float(digits_full / max(len(full_text), 1)),
        "type_token_ratio": float(len(set(words_full)) / max(len(words_full), 1)),
        "toxic_hits": float(toxicity_hits(full_text)),
        "last_n_chars": float(len(draft_text)),
        "last_n_words": float(len(words_draft)),
        "last_question": float("?" in draft_text),
        "last_exclaim": float("!" in draft_text),
        "last_toxic_hits": float(toxicity_hits(draft_text)),
    }
    return pd.DataFrame([data])


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


class TrainedHealthPredictor:
    """Bộ dự đoán sức khỏe hội thoại (Head A) dùng mô hình học máy thật.

    Trích xuất đặc trưng T1/T2, gọi mô hình ML từ registry.
    Nếu chưa có model artifact hoặc xảy ra lỗi trong quá trình suy luận,
    tự động fallback nhẹ nhàng về RuleHealthPredictor.
    """

    def __init__(self, registry: ModelRegistry | None = None, version: str | None = None) -> None:
        self.registry = registry or ModelRegistry()
        self.version_requested = version
        self.fallback = RuleHealthPredictor()

        thresholds = self.registry.get_thresholds(version)
        self.tau1 = float(thresholds["tau1"])
        self.tau2 = float(thresholds["tau2"])

        self.model = self.registry.load_head_a(version)
        if self.model is not None:
            model_ver = getattr(self.model, "version", version or "v1")
            self.version = f"health-trained-{model_ver}"
        else:
            self.version = self.fallback.version

    def score(self, messages: list[Message], now_ms: int, viewer_id: str) -> tuple[float, list[str]]:
        past = sorted((m for m in messages if m.timestamp_ms <= now_ms), key=lambda m: m.timestamp_ms)
        if not past:
            return 0.5, ["no_messages"]

        if self.model is None:
            return self.fallback.score(messages, now_ms, viewer_id)

        try:
            features_df = extract_t1_t2_features(past, now_ms)
            if hasattr(self.model, "predict_proba"):
                p = float(self.model.predict_proba(features_df)[0, 1])
            elif hasattr(self.model, "score"):
                p, factors = self.model.score(messages, now_ms, viewer_id)
                return float(p), factors
            else:
                p = float(self.model.predict(features_df)[0])

            p = max(0.0, min(1.0, p))
            factors = derive_health_factors(past, now_ms)
            return p, factors
        except Exception:
            return self.fallback.score(messages, now_ms, viewer_id)


class TrainedSafetyPredictor:
    """Bộ dự đoán an toàn hội thoại (Head B) dùng mô hình học máy thật.

    Trích xuất đặc trưng văn bản, gọi mô hình ML từ registry.
    Nếu chưa có model artifact hoặc xảy ra lỗi trong quá trình suy luận,
    tự động fallback nhẹ nhàng về RuleSafetyPredictor.
    """

    def __init__(self, registry: ModelRegistry | None = None, version: str | None = None) -> None:
        self.registry = registry or ModelRegistry()
        self.version_requested = version
        self.fallback = RuleSafetyPredictor()

        thresholds = self.registry.get_thresholds(version)
        self.tau_nudge = float(thresholds["tau_nudge"])

        self.model = self.registry.load_head_b(version)
        if self.model is not None:
            model_ver = getattr(self.model, "version", version or "v1")
            self.version = f"safety-trained-{model_ver}"
        else:
            self.version = self.fallback.version

    def score(self, context: list[Message], draft: Message) -> tuple[float, float]:
        if self.model is None:
            return self.fallback.score(context, draft)

        try:
            draft_text = draft.text or ""

            if hasattr(self.model, "predict_proba"):
                try:
                    raw_tox = float(self.model.predict_proba([draft_text])[0, 1])
                except Exception:
                    df_draft = extract_safety_features([], draft)
                    raw_tox = float(self.model.predict_proba(df_draft)[0, 1])

                if context:
                    ctx_texts = [m.text or "" for m in context[-5:] if (m.text or "").strip()]
                    if ctx_texts:
                        try:
                            ctx_probs = self.model.predict_proba(ctx_texts)[:, 1]
                            max_ctx = float(np.max(ctx_probs))
                        except Exception:
                            df_ctx = extract_safety_features(context, draft)
                            max_ctx = float(self.model.predict_proba(df_ctx)[0, 1])
                    else:
                        max_ctx = 0.0
                    derail = min(1.0, 0.5 * raw_tox + 0.5 * max_ctx)
                else:
                    derail = raw_tox

                rule_tox = toxicity_score(draft_text)
                if raw_tox < self.tau_nudge and rule_tox == 0:
                    tox = 0.0
                else:
                    tox = max(raw_tox, rule_tox)

                if derail < self.tau_nudge and tox == 0:
                    derail = 0.0
                else:
                    derail = max(derail, tox)

                return float(max(0.0, min(1.0, tox))), float(max(0.0, min(1.0, derail)))

            # If custom wrapper class with score(context, draft) method
            if hasattr(self.model, "score") and not hasattr(self.model, "fit"):
                tox, derail = self.model.score(context, draft)
                return float(tox), float(derail)

            return self.fallback.score(context, draft)
        except Exception:
            return self.fallback.score(context, draft)


def level_from_p(p: float, tau1: float = TAU1, tau2: float = TAU2) -> HealthLevel:
    if p < tau1:
        return HealthLevel.healthy
    if p < tau2:
        return HealthLevel.shallow
    return HealthLevel.at_risk


@lru_cache
def get_health_predictor() -> TrainedHealthPredictor:
    return TrainedHealthPredictor()


@lru_cache
def get_safety_predictor() -> TrainedSafetyPredictor:
    return TrainedSafetyPredictor()
