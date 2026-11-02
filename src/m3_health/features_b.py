"""Đặc trưng Head B cho G1 (tiền tố hội thoại) và H1/H2 (một bình luận).

G1 chỉ dùng các lượt ``turn_idx < prefix_len`` do ``labels.head_b_g1`` tạo ra:
lượt công kích và mọi lượt sau bị loại, hội thoại lành bỏ lượt cuối.
"""
from __future__ import annotations

import re

import numpy as np
import pandas as pd

TOXIC_TERMS = {
    "ngu", "đần", "óc chó", "đm", "dm", "đmm", "vl", "vcl", "cút", "câm mồm", "mất dạy",
    "stupid", "idiot", "shut up", "fuck", "bitch", "loser", "dumb", "moron", "hate",
}
_WORD = re.compile(r"\w+", re.UNICODE)

BASE_COLUMNS = ["n_messages", "n_senders", "sender_switch", "span_log", "last_gap_log"]
TEXT_COLUMNS = ["n_chars", "n_words", "mean_word_len", "n_questions", "n_exclaims",
                "upper_ratio", "digit_ratio", "type_token_ratio", "toxic_hits"]
LAST_COLUMNS = ["last_n_chars", "last_n_words", "last_question", "last_exclaim", "last_toxic_hits"]
NUMERIC_COLUMNS = BASE_COLUMNS + TEXT_COLUMNS + LAST_COLUMNS


def _as_text(value) -> str:
    return "" if pd.isna(value) else str(value)


def _words(text: str) -> list[str]:
    return _WORD.findall(_as_text(text).casefold())


def _toxic_hits(text: str) -> int:
    words = _words(text)
    padded = f" {' '.join(words)} "
    return sum(1 for term in TOXIC_TERMS if f" {term} " in padded)


def _text_stats(texts: pd.Series) -> pd.DataFrame:
    rows = []
    for value in texts:
        text = _as_text(value)
        words = _words(text)
        letters = sum(ch.isalpha() for ch in text)
        digits = sum(ch.isdigit() for ch in text)
        rows.append({
            "n_chars": len(text),
            "n_words": len(words),
            "mean_word_len": float(np.mean([len(w) for w in words])) if words else 0.0,
            "n_questions": text.count("?"),
            "n_exclaims": text.count("!"),
            "upper_ratio": sum(ch.isupper() for ch in text) / max(letters, 1),
            "digit_ratio": digits / max(len(text), 1),
            "type_token_ratio": len(set(words)) / max(len(words), 1),
            "toxic_hits": _toxic_hits(text),
        })
    return pd.DataFrame(rows, index=texts.index)


def _last_stats(texts: pd.Series) -> pd.DataFrame:
    rows = []
    for value in texts:
        text = _as_text(value)
        rows.append({
            "last_n_chars": len(text),
            "last_n_words": len(_words(text)),
            "last_question": int("?" in text),
            "last_exclaim": int("!" in text),
            "last_toxic_hits": _toxic_hits(text),
        })
    return pd.DataFrame(rows, index=texts.index)


def _sender_switch(series: pd.Series) -> float:
    if len(series) < 2:
        return 0.0
    return float(series.ne(series.shift()).iloc[1:].mean())


def _last_gap(series: pd.Series) -> float:
    values = pd.to_numeric(series, errors="coerce").dropna()
    if len(values) < 2:
        return 0.0
    return float(np.log1p(max(values.iloc[-1] - values.iloc[-2], 0.0)))


def _prefix_aggregate(messages: pd.DataFrame, labels: pd.DataFrame) -> pd.DataFrame:
    frame = messages.merge(
        labels[["conversation_id", "prefix_len"]],
        on="conversation_id",
        how="inner",
    )
    frame = frame[frame["turn_idx"] < frame["prefix_len"]]
    frame = frame.sort_values(["conversation_id", "turn_idx"], kind="stable")
    grouped = frame.groupby("conversation_id", sort=False)
    aggregated = pd.DataFrame({
        "text": grouped["text"].apply(lambda values: " ".join(_as_text(v) for v in values)),
        "last_text": grouped["text"].last().apply(_as_text),
        "n_messages": grouped.size(),
        "n_senders": grouped["sender_id"].nunique(),
        "sender_switch": grouped["sender_id"].apply(_sender_switch),
        "first_ts": grouped["timestamp_s"].first(),
        "last_ts": grouped["timestamp_s"].last(),
        "last_gap_log": grouped["timestamp_s"].apply(_last_gap),
    }).reset_index()
    aggregated["span_log"] = np.log1p(
        (pd.to_numeric(aggregated["last_ts"], errors="coerce")
         - pd.to_numeric(aggregated["first_ts"], errors="coerce")).clip(lower=0)
    ).fillna(0.0)
    return aggregated.drop(columns=["first_ts", "last_ts"])


def build_g1(messages: pd.DataFrame, labels: pd.DataFrame) -> pd.DataFrame:
    aggregated = _prefix_aggregate(messages, labels)
    out = labels.merge(aggregated, on="conversation_id", how="left")
    out["text"] = out["text"].fillna("")
    out["last_text"] = out["last_text"].fillna("")
    for column in ["n_messages", "n_senders"]:
        out[column] = out[column].fillna(0).astype(int)
    for column in ["sender_switch", "span_log", "last_gap_log"]:
        out[column] = out[column].fillna(0.0)
    stats = _text_stats(out["text"])
    last = _last_stats(out["last_text"])
    out = pd.concat([out.reset_index(drop=True), stats.reset_index(drop=True), last.reset_index(drop=True)], axis=1)
    out["y"] = out["derailed"].astype(int)
    return out


def build_single(messages: pd.DataFrame, labels: pd.DataFrame) -> pd.DataFrame:
    out = labels.merge(messages[["conversation_id", "text"]], on="conversation_id", how="left")
    out["text"] = out["text"].fillna("")
    stats = _text_stats(out["text"])
    last = _last_stats(out["text"])
    out = pd.concat([out.reset_index(drop=True), stats.reset_index(drop=True), last.reset_index(drop=True)], axis=1)
    out["n_messages"] = 1
    out["n_senders"] = 1
    out["sender_switch"] = 0.0
    out["span_log"] = 0.0
    out["last_gap_log"] = 0.0
    out["y"] = out["toxic"].astype(int)
    return out
