"""Cluster bootstrap cho khoảng tin cậy trên tập test (docs/01_CHUAN_BAO_CAO_AUDITABLE.md §2).

Lấy mẫu lại **group** (hội thoại / khách hàng / cặp ghép) có hoàn lại, không lấy lại từng mẫu,
vì các mẫu cùng hội thoại phụ thuộc nhau → CI theo mẫu bị hẹp giả tạo.
Cài đặt bằng trọng số: mỗi lần lặp, group được chọn c lần thì mọi mẫu của nó có trọng số c.
``metric(y_true, y_score, sample_weight=...)`` — các hàm của sklearn.metrics đều dùng được.
"""
from __future__ import annotations

from collections.abc import Callable

import numpy as np

Metric = Callable[..., float]


def _weights(groups, B: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    _, inv = np.unique(np.asarray(groups), return_inverse=True)
    n_groups = inv.max() + 1
    rng = np.random.default_rng(seed)
    counts = rng.multinomial(n_groups, np.full(n_groups, 1 / n_groups), size=B)  # (B, n_groups)
    return counts, inv


def _valid(y, w) -> bool:
    """Bỏ lần lặp chỉ còn 1 lớp (AUC không xác định)."""
    pos = w[y == 1].sum()
    return pos > 0 and pos < w.sum()


def _summary(point: float, reps: np.ndarray, ci: float, B: int, n_groups: int) -> dict:
    a = (1 - ci) / 2
    return {"estimate": float(point), "lo": float(np.quantile(reps, a)), "hi": float(np.quantile(reps, 1 - a)),
            "se": float(reps.std(ddof=1)), "B": B, "B_valid": int(len(reps)), "n_groups": n_groups,
            "ci": ci, "method": "cluster_percentile"}


def cluster_bootstrap(metric: Metric, y_true, y_score, groups, B: int = 2000, ci: float = 0.95,
                      seed: int = 42, binary: bool = True) -> dict:
    y, s = np.asarray(y_true), np.asarray(y_score)
    counts, inv = _weights(groups, B, seed)
    reps = []
    for c in counts:
        w = c[inv].astype(float)
        if binary and not _valid(y, w):
            continue
        reps.append(metric(y, s, sample_weight=w))
    return _summary(metric(y, s), np.asarray(reps), ci, B, counts.shape[1])


def paired_cluster_bootstrap(metric: Metric, y_true, score_a, score_b, groups, B: int = 2000,
                             ci: float = 0.95, seed: int = 42, binary: bool = True) -> dict:
    """So sánh 2 mô hình trên **cùng** các lần lấy mẫu lại: Δ = metric(a) − metric(b).

    ``p_value`` hai phía = 2·min(P(Δ* ≤ 0), P(Δ* ≥ 0)).
    """
    y, a, b = np.asarray(y_true), np.asarray(score_a), np.asarray(score_b)
    counts, inv = _weights(groups, B, seed)
    reps = []
    for c in counts:
        w = c[inv].astype(float)
        if binary and not _valid(y, w):
            continue
        reps.append(metric(y, a, sample_weight=w) - metric(y, b, sample_weight=w))
    reps = np.asarray(reps)
    out = _summary(metric(y, a) - metric(y, b), reps, ci, B, counts.shape[1])
    out["p_value"] = float(min(1.0, 2 * min((reps <= 0).mean(), (reps >= 0).mean())))
    out["method"] = "paired_cluster_percentile"
    return out
