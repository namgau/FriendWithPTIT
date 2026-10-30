"""Đặc trưng Head A — tầng T1 (hành vi thời gian) + phần đơn giản của T2 (bề mặt văn bản).

Bản tối thiểu cho baseline A5 ; B4 mở rộng đủ T1/T2 theo docs/06 §2.
Mọi đặc trưng tại mốc (c, t) chỉ dùng các tin ``turn_idx <= t`` của hội thoại c (cửa sổ ``window`` tin).
Cài đặt vector hóa: bảng tin nhắn sắp theo (conversation_id, turn_idx) nên tin thứ k trước dòng i
là dòng i − k (khi turn_idx ≥ k).
"""
from __future__ import annotations

import re

import numpy as np
import pandas as pd

SHORT_REPLIES = {"ok", "k", "kk", "lol", "yeah", "yes", "yep", "no", "sure", "haha", "thanks", "thx", "ty",
                 "ừ", "uk", "ok", "oke", "ờ", "ừm", "hihi", "haha", "hì", "vâng", "dạ"}
_WORD = re.compile(r"\w+", re.UNICODE)


def _message_table(msgs: pd.DataFrame) -> pd.DataFrame:
    m = msgs.sort_values(["conversation_id", "turn_idx"]).reset_index(drop=True)
    text = m["text"].fillna("")
    words = text.str.count(_WORD.pattern)
    norm = text.str.lower().str.strip().str.strip(".!?~ ")
    return pd.DataFrame({
        "conversation_id": m["conversation_id"].to_numpy(), "turn_idx": m["turn_idx"].to_numpy(),
        "ts": m["timestamp_s"].to_numpy(float), "sender": m["sender_id"].to_numpy(),
        "gap": m.groupby("conversation_id")["timestamp_s"].diff().to_numpy(float),
        "n_words": words.to_numpy(float), "is_question": text.str.contains(r"\?", regex=True).to_numpy(),
        "is_short": (norm.isin(SHORT_REPLIES) | (words <= 1)).to_numpy(),
    })


def _lags(col: np.ndarray, rows: np.ndarray, turn: np.ndarray, window: int) -> np.ndarray:
    """Ma trận (n_mẫu, window): cột k = giá trị của tin t − k ; NaN nếu trước đầu hội thoại."""
    out = np.full((len(rows), window), np.nan)
    for k in range(window):
        ok = turn >= k
        out[ok, k] = col[rows[ok] - k]
    return out


def _slope(mat: np.ndarray) -> np.ndarray:
    """Độ dốc theo thời gian (cột 0 = mới nhất) của từng dòng, bỏ NaN."""
    x = -np.arange(mat.shape[1], dtype=float)[None, :].repeat(len(mat), 0)
    ok = ~np.isnan(mat)
    n = ok.sum(1)
    xm = np.where(ok, x, 0).sum(1) / np.maximum(n, 1)
    ym = np.where(ok, mat, 0).sum(1) / np.maximum(n, 1)
    dx = np.where(ok, x - xm[:, None], 0)
    num = (dx * np.where(ok, mat - ym[:, None], 0)).sum(1)
    den = (dx ** 2).sum(1)
    return np.where(den > 0, num / np.where(den > 0, den, 1), 0.0)


def extract_features(msgs: pd.DataFrame, samples: pd.DataFrame, window: int = 10) -> pd.DataFrame:
    """``samples`` có cột conversation_id, turn_idx (mốc t). Trả DataFrame cùng thứ tự với ``samples``."""
    m = _message_table(msgs[msgs["conversation_id"].isin(samples["conversation_id"].unique())])
    key = pd.MultiIndex.from_arrays([m["conversation_id"], m["turn_idx"]])
    rows = key.get_indexer(pd.MultiIndex.from_arrays([samples["conversation_id"], samples["turn_idx"]]))
    if (rows < 0).any():
        raise KeyError("mẫu không có trong bảng tin nhắn")
    turn = m["turn_idx"].to_numpy()[rows]
    W = min(window, int(turn.min()) + 1)  # mọi mẫu đều đủ W tin (mẫu từ tin thứ 6)

    gap = _lags(np.log1p(m["gap"].to_numpy()), rows, turn, W)[:, : W - 1]  # tin đầu không có gap
    words = _lags(m["n_words"].to_numpy(), rows, turn, W)
    short = _lags(m["is_short"].to_numpy(float), rows, turn, W)
    quest = _lags(m["is_question"].to_numpy(float), rows, turn, W)
    sender = m["sender"].to_numpy()
    last_sender = sender[rows]
    same = np.stack([sender[rows - k] == last_sender for k in range(W)], 1).astype(float)

    ts = m["ts"].to_numpy()
    start = m.groupby("conversation_id")["ts"].transform("min").to_numpy()[rows]
    lg = pd.Series(np.log1p(m["gap"].to_numpy()))
    g = lg.groupby(m["conversation_id"].to_numpy())
    hist_gap = (g.cumsum() / g.cumcount().clip(lower=1)).to_numpy()[rows]  # mean log-latency tới t
    hour = pd.to_datetime(ts[rows], unit="s", utc=True)

    f = pd.DataFrame({
        # T1 — nhịp thời gian
        "n_msgs": turn + 1.0,
        "conv_age_log": np.log1p(ts[rows] - start),
        "gap_last": gap[:, 0], "gap_mean": np.nanmean(gap, 1), "gap_max": np.nanmax(gap, 1),
        "gap_std": np.nanstd(gap, 1), "gap_slope": _slope(gap),
        "gap_rel": gap[:, 0] - hist_gap,  # log-latency gần nhất − trung bình log-latency của chính cặp
        "burstiness": _burstiness(np.expm1(gap)),
        "hour": hour.hour.to_numpy(float), "weekend": (hour.day_of_week >= 5).astype(float),
        "night": ((hour.hour >= 0) & (hour.hour < 7)).astype(float),
        # T1 — bất đối xứng 2 bên trong cửa sổ
        "share_last_sender": same.mean(1), "double_text": same[:, 1],
        # T2 — bề mặt văn bản
        "words_last": words[:, 0], "words_mean": words.mean(1), "words_slope": _slope(words),
        "short_share": short.mean(1), "short_last": short[:, 0],
        "question_share": quest.mean(1), "question_last": quest[:, 0],
    }, index=samples.index)
    return f


def _burstiness(gaps: np.ndarray) -> np.ndarray:
    mu, sd = np.nanmean(gaps, 1), np.nanstd(gaps, 1)
    return np.where(mu + sd > 0, (sd - mu) / np.where(mu + sd > 0, mu + sd, 1), 0.0)


def rule_score(f: pd.DataFrame) -> np.ndarray:
    """Baseline luật (gần giống serving/predictors.RuleHealthPredictor, không có im lặng vì đo tại lúc nhắn)."""
    shrinking = (f["words_slope"] < 0).astype(float)
    one_sided = (f["share_last_sender"] > 0.7).astype(float)
    return 0.2 * f["short_last"] + 0.4 * f["short_share"] + 0.1 * shrinking + 0.1 * one_sided + 0.2 * (f["gap_rel"] > 0)
