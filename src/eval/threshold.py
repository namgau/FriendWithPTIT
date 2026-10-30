"""Chọn ngưỡng trên validation (không bao giờ trên test).

- Head A: τ2 (at_risk) theo chi phí FN:FP ; τ1 (shallow) theo recall mục tiêu ; ``levels`` → 3 mức.
- Head B: τ_B theo FPR tối đa (hạn chế "buộc tội oan").
"""
from __future__ import annotations

import numpy as np


def confusion_at(y, p, t: float) -> dict:
    y, pred = np.asarray(y).astype(bool), np.asarray(p) >= t
    tp, fp = int((pred & y).sum()), int((pred & ~y).sum())
    fn, tn = int((~pred & y).sum()), int((~pred & ~y).sum())
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    return {"threshold": float(t), "tp": tp, "fp": fp, "fn": fn, "tn": tn, "precision": prec, "recall": rec,
            "fpr": fp / (fp + tn) if fp + tn else 0.0, "f1": 2 * prec * rec / (prec + rec) if prec + rec else 0.0}


def _candidates(p) -> np.ndarray:
    p = np.asarray(p, float)
    if len(p) > 2000:  # đủ mịn mà không quét mọi giá trị
        return np.unique(np.quantile(p, np.linspace(0, 1, 2001)))
    return np.unique(p)


def threshold_by_cost(y, p, c_fn: float = 1.0, c_fp: float = 1.0) -> dict:
    """Ngưỡng làm nhỏ nhất chi phí kỳ vọng c_fn·FN + c_fp·FP trên validation.

    Với xác suất đã hiệu chỉnh tốt, ngưỡng tối ưu lý thuyết là c_fp / (c_fp + c_fn) — trả kèm để đối chiếu.
    """
    best = min((confusion_at(y, p, t) for t in _candidates(p)), key=lambda r: c_fn * r["fn"] + c_fp * r["fp"])
    best["cost"] = c_fn * best["fn"] + c_fp * best["fp"]
    best["bayes_threshold"] = c_fp / (c_fp + c_fn)
    return best


def threshold_by_fpr(y, p, max_fpr: float = 0.05) -> dict:
    """Ngưỡng thấp nhất (recall cao nhất) mà FPR ≤ ``max_fpr``."""
    ok = [r for r in (confusion_at(y, p, t) for t in _candidates(p)) if r["fpr"] <= max_fpr]
    return min(ok, key=lambda r: r["threshold"]) if ok else confusion_at(y, p, np.inf)


def threshold_by_recall(y, p, min_recall: float = 0.8) -> dict:
    """Ngưỡng cao nhất (ít cảnh báo nhất) mà recall ≥ ``min_recall``."""
    ok = [r for r in (confusion_at(y, p, t) for t in _candidates(p)) if r["recall"] >= min_recall]
    return max(ok, key=lambda r: r["threshold"])


def choose_tau1_tau2(y, p, c_fn: float = 3.0, c_fp: float = 1.0, shallow_recall: float = 0.8) -> tuple[float, float]:
    """τ2 = ngưỡng chi phí (mức at_risk) ; τ1 = ngưỡng đạt recall ``shallow_recall`` (mức shallow), τ1 ≤ τ2."""
    tau2 = threshold_by_cost(y, p, c_fn, c_fp)["threshold"]
    tau1 = min(threshold_by_recall(y, p, shallow_recall)["threshold"], tau2)
    return float(tau1), float(tau2)


def levels(p, tau1: float, tau2: float) -> np.ndarray:
    """0 = healthy, 1 = shallow, 2 = at_risk (khớp HealthLevel trong serving/schemas.py)."""
    p = np.asarray(p, float)
    return (p >= tau1).astype(int) + (p >= tau2).astype(int)
