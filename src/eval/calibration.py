"""Hiệu chỉnh xác suất (Platt / isotonic) và đo độ hiệu chỉnh (ECE, bảng reliability).

Calibrator phải fit trên dữ liệu **không** dùng để huấn luyện mô hình (inner validation hoặc
out-of-fold của train), rồi mới áp lên outer validation / test.
"""
from __future__ import annotations

from collections.abc import Callable

import numpy as np
import pandas as pd
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression

METHODS = ("none", "platt", "isotonic")


def _logit(p) -> np.ndarray:
    p = np.clip(np.asarray(p, dtype=float), 1e-6, 1 - 1e-6)
    return np.log(p / (1 - p))


def fit_calibrator(method: str, scores, y) -> Callable[[np.ndarray], np.ndarray]:
    """Trả về hàm score → xác suất đã hiệu chỉnh. ``scores`` là xác suất thô của mô hình."""
    y = np.asarray(y)
    if method == "none":
        return lambda s: np.asarray(s, dtype=float)
    if method == "platt":
        lr = LogisticRegression(C=1e6).fit(_logit(scores).reshape(-1, 1), y)
        return lambda s: lr.predict_proba(_logit(s).reshape(-1, 1))[:, 1]
    if method == "isotonic":
        iso = IsotonicRegression(out_of_bounds="clip", y_min=0, y_max=1).fit(np.asarray(scores, float), y)
        return lambda s: iso.predict(np.asarray(s, dtype=float))
    raise ValueError(f"method phải thuộc {METHODS}")


def reliability_table(y, p, n_bins: int = 10, strategy: str = "quantile") -> pd.DataFrame:
    """Mỗi bin: số mẫu, xác suất dự đoán trung bình, tỉ lệ dương thực tế."""
    y, p = np.asarray(y, float), np.asarray(p, float)
    if strategy == "quantile":
        edges = np.unique(np.quantile(p, np.linspace(0, 1, n_bins + 1)))
    else:
        edges = np.linspace(0, 1, n_bins + 1)
    idx = np.clip(np.searchsorted(edges, p, side="right") - 1, 0, len(edges) - 2)
    df = pd.DataFrame({"bin": idx, "p": p, "y": y}).groupby("bin").agg(n=("y", "size"), p_mean=("p", "mean"),
                                                                       y_rate=("y", "mean"))
    return df.reset_index()


def ece(y, p, n_bins: int = 10, strategy: str = "quantile") -> float:
    """Expected Calibration Error = Σ (n_bin / N) · |p_mean − y_rate|."""
    t = reliability_table(y, p, n_bins, strategy)
    return float((t["n"] / t["n"].sum() * (t["p_mean"] - t["y_rate"]).abs()).sum())
