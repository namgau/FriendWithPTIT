"""T3 interaction-style features; every sample uses only its trailing prefix window.

Lexicons and the precise missing-value conventions are documented in
docs/B5_T3_FEATURES.md. Missing measurements are NaN, not evidence of similarity.
"""
from __future__ import annotations

import re
import unicodedata

import numpy as np
import pandas as pd

KEYS = ["conversation_id", "turn_idx"]
_TOKEN = re.compile(r"[^\W_]+(?:['’][^\W_]+)*", re.UNICODE)
_LEXICONS = {
    "en": {
        "pronoun": {"i", "me", "my", "mine", "you", "your", "yours", "we", "us", "our", "he", "she", "they", "them", "it"},
        "article": {"a", "an", "the"},
        "preposition": {"in", "on", "at", "to", "of", "for", "with", "from", "by", "about", "into", "over"},
        "conjunction": {"and", "or", "but", "because", "if", "while", "although", "so"},
        "auxiliary": {"am", "is", "are", "was", "were", "be", "been", "do", "does", "did", "have", "has", "had", "can", "could", "will", "would", "should"},
        "negation": {"no", "not", "never", "neither", "nor"},
    },
    "vi": {
        "pronoun": {"tôi", "mình", "tớ", "ta", "bạn", "cậu", "anh", "em", "chị", "họ", "nó", "chúng"},
        "particle": {"nhé", "nha", "à", "ơi", "thôi", "ạ", "nhỉ", "hả", "ư", "vậy"},
        "conjunction": {"và", "hay", "hoặc", "nhưng", "vì", "nếu", "mà", "nên", "rồi"},
        "preposition": {"của", "cho", "với", "từ", "tại", "ở", "về", "đến", "trong", "trên"},
        "negation": {"không", "chẳng", "chưa", "chớ", "đừng"},
    },
}
T3_COLUMNS = ["t3_lsm", "t3_lsm_slope", "t3_mirroring_mean", "t3_mirroring_slope",
              "t3_turn_length_mean", "t3_turn_change_rate", "t3_lsm_missing",
              "t3_mirroring_missing", "t3_turn_change_missing", "t3_multi_sender"]


def _tokens(text) -> list[str]:
    if text is None or pd.isna(text):
        return []
    return _TOKEN.findall(unicodedata.normalize("NFC", str(text)).casefold())


def _slope(points: list[tuple[int, float]]) -> float:
    if len(points) < 2:
        return float("nan")
    x, y = np.asarray(points, dtype=float).T
    x = x - x.mean()
    return float(np.dot(x, y - y.mean()) / np.dot(x, x))


def _lsm(senders: list, tokens: list[list[str]], lexicon: dict) -> float:
    people = list(dict.fromkeys(sender for sender in senders if sender is not None))
    if len(people) != 2:
        return float("nan")
    totals = [0, 0]
    counts = np.zeros((2, len(lexicon)), dtype=float)
    for sender, words in zip(senders, tokens):
        if sender is None:
            continue
        i = people.index(sender)
        totals[i] += len(words)
        for j, words_in_category in enumerate(lexicon.values()):
            counts[i, j] += sum(word in words_in_category for word in words)
    if not all(totals):
        return float("nan")
    proportions = counts / np.asarray(totals)[:, None]
    active = proportions.sum(axis=0) > 0
    if not active.any():
        return float("nan")
    a, b = proportions[:, active]
    return float(np.mean(1 - np.abs(a - b) / (a + b + 0.0001)))


def _window_features(senders: list, tokens: list[list[str]], lexicon: dict) -> dict:
    lsm_points = []
    for end in range(2, len(senders) + 1):
        value = _lsm(senders[:end], tokens[:end], lexicon)
        if np.isfinite(value):
            lsm_points.append((end - 1, value))
    lsm = _lsm(senders, tokens, lexicon)
    mirror_points = []
    known_pairs = changes = 0
    runs = []
    for i, sender in enumerate(senders):
        if sender is not None:
            if i and senders[i - 1] == sender:
                runs[-1] += 1
            else:
                runs.append(1)
        if i and sender is not None and senders[i - 1] is not None:
            known_pairs += 1
            if sender != senders[i - 1]:
                changes += 1
                a, b = set(tokens[i - 1]), set(tokens[i])
                if a and b:
                    mirror_points.append((i, len(a & b) / len(a | b)))
    return {
        "t3_lsm": lsm,
        "t3_lsm_slope": _slope(lsm_points),
        "t3_mirroring_mean": float(np.mean([p[1] for p in mirror_points])) if mirror_points else np.nan,
        "t3_mirroring_slope": _slope(mirror_points),
        "t3_turn_length_mean": float(np.mean(runs)) if runs else np.nan,
        "t3_turn_change_rate": changes / known_pairs if known_pairs else np.nan,
        "t3_lsm_missing": float(not np.isfinite(lsm)),
        "t3_mirroring_missing": float(not mirror_points),
        "t3_turn_change_missing": float(not known_pairs),
        "t3_multi_sender": float(len({s for s in senders if s is not None}) > 2),
    }


def extract_t3_features(messages: pd.DataFrame, samples: pd.DataFrame, *, language: str = "en", window: int = 10) -> pd.DataFrame:
    """Compute T3 in sample order, preserving its index; never inspect later messages."""
    if language not in _LEXICONS:
        raise ValueError("language must be 'en' or 'vi'")
    if not isinstance(window, int) or isinstance(window, bool) or window < 1:
        raise ValueError("window must be a positive integer")
    for frame, columns, label in ((messages, {*KEYS, "text", "sender_id"}, "messages"), (samples, set(KEYS), "samples")):
        if missing := columns.difference(frame.columns):
            raise ValueError(f"{label} missing columns: {sorted(missing)}")
        if frame[KEYS].isna().any().any():
            raise ValueError(f"{label} keys must not be null")
    if messages.duplicated(KEYS).any():
        raise ValueError("message keys must be unique")
    if samples.empty:
        return pd.DataFrame(index=samples.index, columns=T3_COLUMNS, dtype=float)
    frame = messages.loc[messages.conversation_id.isin(samples.conversation_id.unique()), [*KEYS, "sender_id", "text"]].sort_values(KEYS, kind="stable")
    grouped = {key: group for key, group in frame.groupby("conversation_id", sort=False)}
    requested = {}
    for pos, (cid, turn) in enumerate(samples[KEYS].itertuples(index=False, name=None)):
        requested.setdefault(cid, []).append((pos, turn))
    result = [None] * len(samples)
    for cid, wanted in requested.items():
        if cid not in grouped:
            raise KeyError(f"unknown conversation_id {cid!r}")
        group = grouped[cid]
        turns = group.turn_idx.to_numpy()
        senders = [None if pd.isna(s) else s for s in group.sender_id]
        texts = [_tokens(text) for text in group.text]
        for pos, turn in wanted:
            end = int(np.searchsorted(turns, turn, side="left"))
            if end == len(turns) or turns[end] != turn:
                raise KeyError(f"sample message {(cid, turn)!r} is missing")
            start = max(0, end - window + 1)
            result[pos] = _window_features(senders[start:end + 1], texts[start:end + 1], _LEXICONS[language])
    return pd.DataFrame(result, columns=T3_COLUMNS, index=samples.index, dtype=float)
