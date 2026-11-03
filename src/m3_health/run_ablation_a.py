"""A7 (phần T1/T2) + X4 — Head A: ablation nhóm đặc trưng và cross-domain E1 ↔ E2.

Chạy:  python -m src.m3_health.run_ablation_a

Cùng hội thoại, cùng fold forward chaining K=5 với run_head_a (chỉ train ; test khóa tới 20/11).
Mô hình cố định: LightGBM + none (tốt nhất ở A5) để chỉ đặc trưng thay đổi.
- X1 (một phần): T1 → T1+T2 tích lũy, và bỏ-từng-nhóm. T3–T5 thêm khi B5 / C1 / B8 xong.
- X4: mô hình học trên toàn bộ train của nguồn, đánh giá trên đúng các fold validation của đích ;
  so với in-domain (học trên outer-train của đích). Brier thô cho thấy lệch prevalence, Brier sau
  prior shift (biết prevalence đích, không cần nhãn đích) cho thấy phần sửa được.
Kết quả: docs/results/HEAD_A_ABLATION.md, ledger.
"""
from __future__ import annotations

import sys

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from src.eval.imbalance import prior_shift
from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, assert_time_order, forward_chaining
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_head_a import MODELS, load

GROUPS = {
    "T1_rhythm": ["n_msgs", "conv_age_log", "gap_last", "gap_mean", "gap_max", "gap_std", "gap_slope", "gap_rel",
                  "burstiness"],
    "T1_clock": ["hour", "weekend", "night"],
    "T1_asym": ["share_last_sender", "double_text"],
    "T2_surface": ["words_last", "words_mean", "words_slope", "short_share", "short_last", "question_share",
                   "question_last"],
}
T1 = GROUPS["T1_rhythm"] + GROUPS["T1_clock"] + GROUPS["T1_asym"]
ALL = T1 + GROUPS["T2_surface"]
SETS = {"T1_rhythm": GROUPS["T1_rhythm"], "T1": T1, "T1+T2 (đủ)": ALL,  # tích lũy ; bỏ T2 = T1
        **{f"− {g}": [c for c in ALL if c not in cols] for g, cols in GROUPS.items() if g != "T2_surface"}}


def _fit(X: pd.DataFrame, y: np.ndarray, cols: list[str], seed: int) -> LGBMClassifier:
    return MODELS["lgbm"](seed).fit(X[cols], y)


def ablation(d: str, data: tuple, cfg: dict) -> pd.DataFrame:
    lab, X = data
    y, groups, start = lab["y"].to_numpy(int), lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    rows = []
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, cfg["cv"]["outer_k"])):
        assert_no_group_leak(groups, tr, va)
        assert_time_order(groups, start, tr, va)
        for name, cols in SETS.items():
            p = _fit(X.iloc[tr], y[tr], cols, cfg["test_seed"]).predict_proba(X.iloc[va][cols])[:, 1]
            rows.append({"dataset": d, "fold": fold, "set": name, "n_feat": len(cols),
                         "pr_auc": average_precision_score(y[va], p), "roc_auc": roc_auc_score(y[va], p)})
            print("ablation", d, fold, name, f"{rows[-1]['pr_auc']:.4f}", flush=True)
    return pd.DataFrame(rows)


def cross_domain(src: str, dst: str, data: dict, cfg: dict) -> pd.DataFrame:
    (lab_s, X_s), (lab_t, X_t) = data[src], data[dst]
    y_s, y_t = lab_s["y"].to_numpy(int), lab_t["y"].to_numpy(int)
    seed = cfg["test_seed"]
    cross = _fit(X_s, y_s, ALL, seed)
    groups, start = lab_t["group_id"].to_numpy(), lab_t["conv_start_s"].to_numpy()
    rows = []
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, cfg["cv"]["outer_k"])):
        prev_t = y_t[tr].mean()  # prevalence đích lấy từ outer-train của đích, không nhìn validation
        for setting, model, prev_from in (("in-domain", _fit(X_t.iloc[tr], y_t[tr], ALL, seed), None),
                                          (f"{src} → {dst}", cross, y_s.mean())):
            p = model.predict_proba(X_t.iloc[va][ALL])[:, 1]
            p_ps = p if prev_from is None else prior_shift(p, prev_from, prev_t)
            rows.append({"dst": dst, "fold": fold, "setting": setting, "pr_auc": average_precision_score(y_t[va], p),
                         "roc_auc": roc_auc_score(y_t[va], p), "brier_raw": brier_score_loss(y_t[va], p),
                         "brier_prior_shift": brier_score_loss(y_t[va], p_ps)})
            print("cross", setting, fold, f"{rows[-1]['pr_auc']:.4f}", flush=True)
    return pd.DataFrame(rows)


def write_report(abl: pd.DataFrame, xd: pd.DataFrame, cfg: dict) -> None:
    lines = ["# Head A — ablation đặc trưng (A7, X1 phần T1/T2) và cross-domain (X4)", "",
             "> Sinh bởi `python -m src.m3_health.run_ablation_a`. Chỉ tập train ; cùng hội thoại và fold forward "
             f"chaining K = {cfg['cv']['outer_k']} với `HEAD_A_CV.md` ; LightGBM + none cố định, Δ = 48h. "
             "Mean ± SD qua 5 fold. T3 (B5), T4–T5 (C1, B8) sẽ thêm dòng khi có.", "",
             "## X1 — ablation nhóm đặc trưng (PR-AUC)", "",
             "Nhóm: " + " ; ".join(f"**{g}** = {', '.join(c)}" for g, c in GROUPS.items()) + ".", "",
             "| Tập đặc trưng | Số đặc trưng | " + " | ".join(f"{d} PR-AUC | {d} Δ so với đủ" for d in abl.dataset.unique())
             + " |", "|---|---|" + "---|---|" * abl.dataset.nunique()]
    full = abl[abl.set == "T1+T2 (đủ)"].set_index(["dataset", "fold"]).pr_auc
    for name in SETS:
        cells = []
        for d in abl.dataset.unique():
            g = abl[(abl.set == name) & (abl.dataset == d)].set_index(["dataset", "fold"]).pr_auc
            cells += [mean_sd(g), "—" if name == "T1+T2 (đủ)" else mean_sd(g - full.loc[g.index])]
        lines.append(f"| {name} | {len(SETS[name])} | " + " | ".join(cells) + " |")
    lines += ["", "Δ so với đủ tính theo cặp cùng fold (âm = bỏ nhóm làm giảm PR-AUC).", "",
              "## X4 — cross-domain E1 ↔ E2", "",
              "Mô hình nguồn học trên **toàn bộ** train của nguồn (60k hội thoại), đánh giá trên các fold validation "
              "của đích. Prior shift dùng prevalence của outer-train đích (không cần nhãn từng mẫu đích).", "",
              "| Đích | Thiết lập | PR-AUC | ROC-AUC | Brier thô | Brier sau prior shift |", "|---|---|---|---|---|---|"]
    for (dst, setting), g in xd.groupby(["dst", "setting"], sort=False):
        ps = "—" if setting == "in-domain" else mean_sd(g.brier_prior_shift)
        lines.append(f"| {dst} | {setting} | {mean_sd(g.pr_auc)} | {mean_sd(g.roc_auc)} | {mean_sd(g.brier_raw)} | {ps} |")
    out = ROOT / "docs" / "results" / "HEAD_A_ABLATION.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    data = {d: load(d, cfg) for d in datasets}
    abl = pd.concat([ablation(d, data[d], cfg) for d in datasets], ignore_index=True)
    xd = pd.concat([cross_domain(s, t, data, cfg) for s, t in (("E1", "E2"), ("E2", "E1"))], ignore_index=True)
    write_report(abl, xd, cfg)
    for d in datasets:
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        for name, g in abl[abl.dataset == d].groupby("set", sort=False):
            append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:ablation:{name}", "pr_auc", g.pr_auc,
                          notes="X1 lgbm none, forward_chaining K=5")
        for setting, g in xd[xd.dst == d].groupby("setting", sort=False):
            append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:cross:{setting}", "pr_auc", g.pr_auc,
                          notes="X4 lgbm none")


if __name__ == "__main__":
    main(sys.argv[1:] or ["E1", "E2"])
