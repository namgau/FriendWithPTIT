"""5 phương án xử lý mất cân bằng (X2): none / class_weight / undersample / oversample / smote.

Resampling nằm **trong** ``imblearn.pipeline.Pipeline`` → chỉ chạy lúc ``fit`` trên train fold,
không bao giờ chạm validation/test (lúc ``predict`` sampler bị bỏ qua).
Sau under/oversampling, xác suất của mô hình ứng với prevalence 50/50 → dùng ``prior_shift``
để đưa về prevalence thật trước khi calibrate / chọn ngưỡng.
"""
from __future__ import annotations

import numpy as np
from imblearn.over_sampling import SMOTE, RandomOverSampler
from imblearn.pipeline import Pipeline
from imblearn.under_sampling import RandomUnderSampler
from sklearn.base import clone

STRATEGIES = ("none", "class_weight", "undersample", "oversample", "smote")
RESAMPLING = ("undersample", "oversample", "smote")


def make_pipeline(estimator, strategy: str = "none", preprocess: list | None = None, seed: int = 42) -> Pipeline:
    """``preprocess``: danh sách bước (tên, transformer) chạy trước sampler (vd scaler — bắt buộc trước SMOTE)."""
    if strategy not in STRATEGIES:
        raise ValueError(f"strategy phải thuộc {STRATEGIES}")
    est = clone(estimator)
    steps = list(preprocess or [])
    if strategy == "class_weight":
        if "class_weight" not in est.get_params():
            raise ValueError(f"{type(est).__name__} không hỗ trợ class_weight")
        est.set_params(class_weight="balanced")
    elif strategy == "undersample":
        steps.append(("sampler", RandomUnderSampler(random_state=seed)))
    elif strategy == "oversample":
        steps.append(("sampler", RandomOverSampler(random_state=seed)))
    elif strategy == "smote":
        steps.append(("sampler", SMOTE(random_state=seed)))
    steps.append(("model", est))
    return Pipeline(steps)


def train_prevalence_after(strategy: str, prevalence: float) -> float:
    """Prevalence mà mô hình "thấy" lúc học: 0.5 nếu resample cân bằng, còn lại giữ nguyên.

    class_weight="balanced" cũng làm lệch xác suất như học trên dữ liệu 50/50.
    """
    return 0.5 if strategy in RESAMPLING + ("class_weight",) else prevalence


def prior_shift(p, from_prev: float, to_prev: float) -> np.ndarray:
    """Hiệu chỉnh xác suất khi prevalence đổi (Saerens et al. 2002): odds nhân với tỉ số odds prior."""
    p = np.clip(np.asarray(p, dtype=float), 1e-12, 1 - 1e-12)
    r1, r0 = to_prev / from_prev, (1 - to_prev) / (1 - from_prev)
    return p * r1 / (p * r1 + (1 - p) * r0)
