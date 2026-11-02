"""A5b — 5 phương án mất cân bằng trên Head B tiếng Việt (H2 rất lệch, H1 để đối chiếu), phần CPU.

Chạy:  python -m src.m3_health.run_imbalance_b [H2 H1]

Mô hình: TF-IDF (1–2 gram) + đặc trưng số → [sampler] → LR. Giao thức giống run_head_b:
group K-fold, 20% group của outer-train để calibrate (Platt sau prior_shift) và chọn τ_B theo FPR.
Phần embedding đã cache + LR chờ B8 ; phần PhoBERT/XLM-R là B10 (GPU).
"""
from __future__ import annotations

import sys

import numpy as np
import pandas as pd
from sklearn.base import clone
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, f1_score, roc_auc_score

from src.eval.calibration import ece, fit_calibrator
from src.eval.imbalance import STRATEGIES, make_pipeline, prior_shift, train_prevalence_after
from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_head_b import load, make_model


def run(dataset: str, cfg: dict) -> pd.DataFrame:
    frame = load(dataset)
    y = frame["y"].to_numpy(int)
    groups = frame["group_id"].to_numpy()
    hb, seed = cfg["head_b"], cfg["test_seed"]
    preprocess = make_model(cfg, seed).named_steps["preprocess"]  # cùng TF-IDF + đặc trưng số như C4
    rows = []
    for fold, (tr, va) in enumerate(group_kfold(groups, hb["cv_folds"], seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(groups[tr], hb["calibration_fraction"], seed=seed + fold, y=y[tr])
        fit, cal = tr[fit_i], tr[cal_i]
        for a, b in [(fit, cal), (fit, va), (cal, va)]:
            assert_no_group_leak(groups, a, b)
        prev = y[fit].mean()
        for strat in STRATEGIES:
            lr = LogisticRegression(max_iter=2000, solver="liblinear", random_state=seed)
            pipe = make_pipeline(lr, strat, [("preprocess", clone(preprocess))], seed)
            pipe.fit(frame.iloc[fit], y[fit])
            from_prev = train_prevalence_after(strat, prev)
            raw_cal = prior_shift(pipe.predict_proba(frame.iloc[cal])[:, 1], from_prev, prev)
            raw_va = prior_shift(pipe.predict_proba(frame.iloc[va])[:, 1], from_prev, prev)
            cal_fn = fit_calibrator("platt", raw_cal, y[cal])
            p_cal, p_va = cal_fn(raw_cal), cal_fn(raw_va)
            tau = threshold_by_fpr(y[cal], p_cal, hb["threshold_fpr"])["threshold"]
            at = confusion_at(y[va], p_va, tau)
            pred_raw = pipe.predict(frame.iloc[va])  # ngưỡng 0,5 trên xác suất của chính mô hình (không chỉnh)
            rows.append({
                "dataset": dataset, "fold": fold, "strategy": strat, "n_fit": len(fit), "prev_val": y[va].mean(),
                "pr_auc": average_precision_score(y[va], p_va), "roc_auc": roc_auc_score(y[va], p_va),
                "brier": brier_score_loss(y[va], p_va), "ece": ece(y[va], p_va),
                "ece_uncalibrated": ece(y[va], pipe.predict_proba(frame.iloc[va])[:, 1]),
                "macro_f1_default": f1_score(y[va], pred_raw, average="macro"),
                "recall_default": float((pred_raw[y[va] == 1] == 1).mean()),
                "fpr_default": float((pred_raw[y[va] == 0] == 1).mean()),
                "recall_at_tau": at["recall"], "fpr_at_tau": at["fpr"], "precision_at_tau": at["precision"],
            })
            print(dataset, fold, strat, f"pr_auc={rows[-1]['pr_auc']:.4f}", flush=True)
    return pd.DataFrame(rows)


def write_report(res: pd.DataFrame, cfg: dict) -> None:
    hb = cfg["head_b"]
    lines = ["# Head B — 5 phương án mất cân bằng (A5b, phần CPU)", "",
             "> Sinh bởi `python -m src.m3_health.run_imbalance_b`. Chỉ tập train ; group K-fold K = "
             f"{hb['cv_folds']} ; TF-IDF + đặc trưng số → [sampler] → LR (resampling chỉ trong fold fit).",
             "> Cột *mặc định*: dùng thẳng `predict()` (ngưỡng 0,5 trên xác suất chưa chỉnh) — cách làm thường gặp. "
             f"Cột *τ_B*: prior_shift + Platt trên 20% group calibration, τ_B theo FPR ≤ {hb['threshold_fpr']:.0%}.", ""]
    for d, g in res.groupby("dataset", sort=False):
        lines += [f"## {d} (prevalence {g['prev_val'].mean():.3f})", "",
                  "| Phương án | PR-AUC | ROC-AUC | ECE chưa chỉnh | ECE sau chỉnh | Macro-F1 mặc định | Recall mặc định "
                  "| FPR mặc định | Recall @τ_B | FPR @τ_B |", "|---|---|---|---|---|---|---|---|---|---|"]
        for s, h in g.groupby("strategy", sort=False):
            c = lambda col: mean_sd(h[col])
            lines.append(f"| {s} | {c('pr_auc')} | {c('roc_auc')} | {c('ece_uncalibrated')} | {c('ece')} | "
                         f"{c('macro_f1_default')} | {c('recall_default')} | {c('fpr_default')} | "
                         f"{c('recall_at_tau')} | {c('fpr_at_tau')} |")
        lines.append("")
    out = ROOT / "docs" / "results" / "IMBALANCE_B.md"
    out.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    res = []
    for d in datasets:
        r = run(d, cfg)
        r.to_csv(ROOT / "reports" / f"imbalanceB_cv_{d}.csv", index=False)
        res.append(r)
        dh = data_hash(PROC / d / "labels_headB_train.parquet", PROC / d / "messages.parquet")
        for s, g in r.groupby("strategy", sort=False):
            append_ledger("m3_headB", "A", cfg, dh, [cfg["test_seed"]], f"{d}:tfidf_lr:{s}", "pr_auc", g["pr_auc"],
                          notes="A5b imbalance, group K-fold")
    write_report(pd.concat(res, ignore_index=True), cfg)


if __name__ == "__main__":
    main(sys.argv[1:] or ["H2", "H1"])
