"""A5 — Head A: baseline + LR + LightGBM × 5 phương án mất cân bằng (X2) + calibration + τ1/τ2.

Chạy:  python -m src.m3_health.run_head_a [E1 E2]

Giao thức (docs/06 §4): chỉ dùng tập train (test khóa tới 20/11) ; outer = forward chaining K=5 theo
thời điểm bắt đầu group ; trong mỗi outer-train, 20% group muộn nhất làm tập calibration
(fit mô hình trên phần còn lại) ; mọi resampling nằm trong pipeline nên chỉ chạy trên phần fit.
Kết quả: reports/headA_cv_<ID>.csv (từng fold), docs/results/HEAD_A_CV.md (bảng tổng hợp), ledger.
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.preprocessing import StandardScaler

from src.eval.calibration import ece, fit_calibrator
from src.eval.imbalance import STRATEGIES, make_pipeline, prior_shift, train_prevalence_after
from src.eval.report import append_ledger, cv_summary, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, assert_time_order, forward_chaining, held_out_time_split
from src.eval.threshold import choose_tau1_tau2, confusion_at, levels
from src.m3_health.features import extract_features, rule_score
from src.m3_health.labels import PROC, ROOT, load_config, load_train

MODELS = {
    "lr": lambda seed: LogisticRegression(max_iter=1000),
    "lgbm": lambda seed: LGBMClassifier(n_estimators=300, learning_rate=0.05, num_leaves=31,
                                        min_child_samples=50, subsample=0.8, subsample_freq=1,
                                        colsample_bytree=0.8, random_state=seed, verbose=-1),
}


def load(d: str, cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    lab = load_train(d)
    lab = lab[lab["y"].notna()].reset_index(drop=True)  # bỏ mẫu censored ở bài nhị phân
    cap = cfg["head_a"]["cv_max_conversations"]
    convs = lab["conversation_id"].unique()
    if len(convs) > cap:  # giới hạn tài nguyên: lấy ngẫu nhiên theo hội thoại, ghi vào báo cáo
        keep = np.random.default_rng(cfg["test_seed"]).choice(convs, cap, replace=False)
        lab = lab[lab["conversation_id"].isin(keep)].reset_index(drop=True)
        lab.attrs["capped"] = True
    msgs = pd.read_parquet(PROC / d / "messages.parquet")
    return lab, extract_features(msgs, lab)


def _metrics(y, p) -> dict:
    return {"pr_auc": average_precision_score(y, p), "roc_auc": roc_auc_score(y, p),
            "brier": brier_score_loss(y, p), "ece": ece(y, p)}


def run(d: str, cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    lab, X = load(d, cfg)
    y = lab["y"].to_numpy(int)
    groups, start = lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    seed, k = cfg["test_seed"], cfg["cv"]["outer_k"]
    rows, oof = [], []
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, k)):
        assert_no_group_leak(groups, tr, va)
        assert_time_order(groups, start, tr, va)
        fit_i, cal_i = held_out_time_split(groups[tr], start[tr], 0.2)
        fit, cal = tr[fit_i], tr[cal_i]
        prev = y[fit].mean()
        candidates = {("majority", "none"): None, ("rule", "none"): "rule", ("gap_last", "none"): "gap_last"}
        candidates.update({(m, s): "model" for m in MODELS for s in STRATEGIES})
        for (name, strat), kind in candidates.items():
            t0 = time.time()
            if kind is None:
                s_cal, s_va = np.full(len(cal), prev), np.full(len(va), prev)
            elif kind == "rule":
                s_cal, s_va = rule_score(X.iloc[cal]).to_numpy(), rule_score(X.iloc[va]).to_numpy()
            elif kind == "gap_last":
                s_cal, s_va = X["gap_last"].to_numpy()[cal], X["gap_last"].to_numpy()[va]
            else:
                pipe = make_pipeline(MODELS[name](seed), strat, [("scale", StandardScaler())], seed)
                pipe.fit(X.iloc[fit], y[fit])
                from_prev = train_prevalence_after(strat, prev)
                s_cal = prior_shift(pipe.predict_proba(X.iloc[cal])[:, 1], from_prev, prev)
                s_va = prior_shift(pipe.predict_proba(X.iloc[va])[:, 1], from_prev, prev)
            row = {"dataset": d, "fold": fold, "model": name, "strategy": strat, "n_fit": len(fit),
                   "n_val": len(va), "prev_val": y[va].mean(), "fit_s": time.time() - t0}
            if kind is None:
                row.update(_metrics(y[va], s_va) | {"pr_auc": y[va].mean(), "roc_auc": 0.5})
            else:
                row["pr_auc"], row["roc_auc"] = average_precision_score(y[va], s_va), roc_auc_score(y[va], s_va)
                for method in ("platt", "isotonic"):
                    p = fit_calibrator(method, s_cal, y[cal])(s_va)
                    row[f"brier_{method}"], row[f"ece_{method}"] = brier_score_loss(y[va], p), ece(y[va], p)
                    if method == "isotonic":
                        oof.append(pd.DataFrame({"model": name, "strategy": strat, "fold": fold, "y": y[va], "p": p}))
                if kind == "model":
                    row["brier_raw"], row["ece_raw"] = brier_score_loss(y[va], s_va), ece(y[va], s_va)
            rows.append(row)
            print(d, fold, name, strat, f"pr_auc={row['pr_auc']:.4f}", f"{row['fit_s']:.0f}s", flush=True)
    info = {"n_samples": len(lab), "n_conversations": int(lab["conversation_id"].nunique()),
            "capped": bool(lab.attrs.get("capped")), "prevalence": float(y.mean())}
    return pd.DataFrame(rows), pd.concat(oof, ignore_index=True), info


def write_report(all_res: pd.DataFrame, taus: dict, cfg: dict, n_info: dict) -> None:
    metrics = ["pr_auc", "roc_auc", "brier_raw", "brier_platt", "brier_isotonic", "ece_raw", "ece_platt",
               "ece_isotonic"]
    lines = ["# Head A — kết quả CV trên tập train (A5, X2)", "",
             "> Sinh bởi `python -m src.m3_health.run_head_a`. Tập test **chưa mở** (khóa tới 20/11).",
             f"> Outer: forward chaining K = {cfg['cv']['outer_k']} theo thời điểm bắt đầu group ; calibration trên "
             "20% group muộn nhất của mỗi outer-train ; Δ = 48h ; đặc trưng T1 + T2 tối thiểu "
             "(`src/m3_health/features.py`). Mean ± SD qua 5 fold.", ""]
    for d, res in all_res.groupby("dataset"):
        info = n_info[d]
        lines += [f"## {d}", "", f"N = {info['n_samples']:,} mẫu, {info['n_conversations']:,} hội thoại"
                  f"{' (lấy ngẫu nhiên ' + str(cfg['head_a']['cv_max_conversations']) + ' hội thoại vì giới hạn tài nguyên)' if info['capped'] else ''}"
                  f", prevalence {info['prevalence']:.3f}.", "",
                  "| Mô hình | Imbalance | PR-AUC | ROC-AUC | Brier (isotonic) | ECE thô | ECE isotonic |",
                  "|---|---|---|---|---|---|---|"]
        for (m, s), g in res.groupby(["model", "strategy"], sort=False):
            cell = lambda c: mean_sd(g[c]) if c in g and g[c].notna().all() else "—"
            lines.append(f"| {m} | {s} | {cell('pr_auc')} | {cell('roc_auc')} | {cell('brier_isotonic')} | "
                         f"{cell('ece_raw')} | {cell('ece_isotonic')} |")
        t = taus[d]
        lines += ["", f"**Mô hình tốt nhất theo PR-AUC:** `{t['model']}` + `{t['strategy']}`. "
                  f"Ngưỡng chọn trên dự đoán out-of-fold đã hiệu chỉnh (C_FN : C_FP = {t['c_fn']:g} : {t['c_fp']:g}, "
                  f"recall mức hời hợt ≥ {t['shallow_recall']:g}): **τ1 = {t['tau1']:.3f}, τ2 = {t['tau2']:.3f}** ; "
                  f"tỉ lệ 3 mức khỏe / hời hợt / nguy cơ = {t['level_share']} ; tại τ2: precision {t['at_tau2']['precision']:.3f}, "
                  f"recall {t['at_tau2']['recall']:.3f}.", ""]
    out = ROOT / "docs" / "results" / "HEAD_A_CV.md"
    out.parent.mkdir(exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    th = cfg["head_a"]["threshold"]
    (ROOT / "reports").mkdir(exist_ok=True)
    all_res, taus, n_info = [], {}, {}
    for d in datasets:
        res, oof, n_info[d] = run(d, cfg)
        res.to_csv(ROOT / "reports" / f"headA_cv_{d}.csv", index=False)
        all_res.append(res)
        summ = cv_summary(res[res["model"].isin(MODELS)], ["model", "strategy"], ["pr_auc"])
        best = summ.sort_values("pr_auc_mean", ascending=False).iloc[0]
        o = oof[(oof.model == best.model) & (oof.strategy == best.strategy)]
        tau1, tau2 = choose_tau1_tau2(o.y, o.p, th["c_fn"], th["c_fp"], th["shallow_recall"])
        share = np.bincount(levels(o.p, tau1, tau2), minlength=3) / len(o)
        taus[d] = {"model": best.model, "strategy": best.strategy, "tau1": tau1, "tau2": tau2, **th,
                   "level_share": " / ".join(f"{x:.2f}" for x in share), "at_tau2": confusion_at(o.y, o.p, tau2)}
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        for (m, s), g in res.groupby(["model", "strategy"], sort=False):
            append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:{m}:{s}", "pr_auc",
                          g["pr_auc"], notes=f"forward_chaining K={cfg['cv']['outer_k']}, T1+T2 min")
    write_report(pd.concat(all_res), taus, cfg, n_info)


if __name__ == "__main__":
    main(sys.argv[1:] or ["E1", "E2"])
