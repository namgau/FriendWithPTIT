"""Mô hình cuối (A8 đánh giá test 20/11, export serving) — fit trên toàn bộ train theo **đúng** giao thức CV.

Head A: pipeline LightGBM (`run_head_a.MODELS`) + phương án mất cân bằng ; fit trên 80% group bắt đầu sớm nhất,
  prior shift + isotonic fit trên 20% group muộn nhất (giống từng outer fold của `run_head_a`).
  τ1 / τ2 theo alert budget lấy trên OOF forward chaining của chính cấu hình đó (`oof_head_a`).
Head B: TF-IDF + LR (`run_head_b.make_model`), Platt trên 20% group ; τ_B = FPR ≤ 5% trên phần calibration.
Mô hình trả về là object sklearn-like (`predict_proba`) lưu được bằng joblib để serving nạp.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

from src.eval.calibration import fit_calibrator
from src.eval.imbalance import make_pipeline, prior_shift, train_prevalence_after
from src.eval.splits import forward_chaining, held_out_group_split, held_out_time_split
from src.eval.threshold import choose_tau1_tau2_budget, threshold_by_fpr
from src.m3_health.features_b import build_single
from src.m3_health.run_head_a import MODELS
from src.m3_health.run_head_b import make_model


class HeadAModel:
    def __init__(self, pipe, cols: list[str], from_prev: float, prev: float, calibrator) -> None:
        self.pipe, self.cols, self.from_prev, self.prev, self.calibrator = pipe, list(cols), from_prev, prev, calibrator

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        s = prior_shift(self.pipe.predict_proba(X[self.cols])[:, 1], self.from_prev, self.prev)
        p = np.clip(self.calibrator(s), 0.0, 1.0)
        return np.column_stack([1 - p, p])


def fit_head_a(lab: pd.DataFrame, X: pd.DataFrame, cols: list[str], seed: int, strategy: str = "none",
               model: str = "lgbm") -> HeadAModel:
    y = lab["y"].to_numpy(int)
    fit, cal = held_out_time_split(lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy(), 0.2)
    pipe = make_pipeline(MODELS[model](seed), strategy, [("scale", StandardScaler())], seed)
    pipe.fit(X.iloc[fit][cols], y[fit])
    prev = float(y[fit].mean())
    from_prev = train_prevalence_after(strategy, prev)
    s_cal = prior_shift(pipe.predict_proba(X.iloc[cal][cols])[:, 1], from_prev, prev)
    return HeadAModel(pipe, cols, from_prev, prev, fit_calibrator("isotonic", s_cal, y[cal]))


def oof_head_a(lab: pd.DataFrame, X: pd.DataFrame, cols: list[str], seed: int, k: int, strategy: str = "none",
               model: str = "lgbm") -> np.ndarray:
    """Xác suất OOF (NaN ở khối sớm nhất, khối không bao giờ là validation trong forward chaining)."""
    p = np.full(len(lab), np.nan)
    for tr, va in forward_chaining(lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy(), k):
        m = fit_head_a(lab.iloc[tr].reset_index(drop=True), X.iloc[tr].reset_index(drop=True), cols, seed,
                       strategy, model)
        p[va] = m.predict_proba(X.iloc[va])[:, 1]
    return p


def head_a_taus(y, p_oof, threshold_cfg: dict) -> tuple[float, float]:
    ok = np.isfinite(p_oof)
    return choose_tau1_tau2_budget(np.asarray(y)[ok], p_oof[ok], threshold_cfg["alert_budget"],
                                   threshold_cfg["shallow_recall"])


def frame_from_texts(texts) -> pd.DataFrame:
    """Bảng đặc trưng H1/H2 (một bình luận) từ danh sách văn bản — cùng `build_single` lúc train."""
    ids = np.arange(len(texts))
    msgs = pd.DataFrame({"conversation_id": ids, "text": ["" if t is None else str(t) for t in texts]})
    return build_single(msgs, pd.DataFrame({"conversation_id": ids, "toxic": 0}))


class HeadBTextModel:
    def __init__(self, pipe, calibrator) -> None:
        self.pipe, self.calibrator = pipe, calibrator

    def predict_proba(self, X) -> np.ndarray:
        frame = X if isinstance(X, pd.DataFrame) else frame_from_texts(list(X))
        p = np.clip(self.calibrator(self.pipe.predict_proba(frame)[:, 1]), 0.0, 1.0)
        return np.column_stack([1 - p, p])


def fit_head_b(frame: pd.DataFrame, cfg: dict, seed: int) -> tuple[HeadBTextModel, float]:
    """Trả về (mô hình, τ_B)."""
    y = frame["y"].to_numpy(int)
    hb = cfg["head_b"]
    fit, cal = held_out_group_split(frame["group_id"].to_numpy(), hb["calibration_fraction"], seed, y=y)
    pipe = make_model(cfg, seed).fit(frame.iloc[fit], y[fit])
    raw_cal = pipe.predict_proba(frame.iloc[cal])[:, 1]
    model = HeadBTextModel(pipe, fit_calibrator("platt", raw_cal, y[cal]))
    tau = float(threshold_by_fpr(y[cal], model.calibrator(raw_cal), hb["threshold_fpr"])["threshold"])
    return model, tau
