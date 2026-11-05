"""A6 — Head A dạng survival (X3): Cox PH, Random Survival Forest so với LightGBM phân loại.

Chạy:  python -m src.m3_health.run_survival [E2 E1]

T = thời gian tới tin kế tiếp, event = có tin kế tiếp (labels.head_a_samples). P(tắt trong Δ) = S(Δ | x).
- Cùng hội thoại và cùng fold forward chaining với A5 (run_head_a) để so cặp được ;
  survival dùng **cả** mẫu censored lúc fit, đánh giá nhị phân chỉ trên mẫu biết nhãn.
- T bị kiểm duyệt hành chính ở ``admin_cap_s`` (7 ngày): không ảnh hưởng S(Δ) với Δ ≤ 72h,
  giúp RSF không phải lưu hàng triệu mốc thời gian. RSF fit trên mẫu con (giới hạn tài nguyên).
- Một mô hình survival cho xác suất ở mọi Δ (độ nhạy X5) ; LightGBM phải học lại cho từng Δ.
"""
from __future__ import annotations

import sys
import time

import numpy as np
import pandas as pd
from lifelines import CoxPHFitter
from lightgbm import LGBMClassifier
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.preprocessing import StandardScaler
from sksurv.ensemble import RandomSurvivalForest
from sksurv.metrics import concordance_index_censored, integrated_brier_score
from sksurv.util import Surv

from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, forward_chaining
from src.m3_health.features import extract_features
from src.m3_health.labels import PROC, ROOT, cv_conversations, label_at, load_config, load_train
from src.m3_health.run_head_a import MODELS

H = 3600.0


def load(d: str, cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Chọn hội thoại giống run_head_a.load, nhưng giữ cả mẫu censored của các hội thoại đó."""
    lab = load_train(d)
    lab = lab[lab["conversation_id"].isin(cv_conversations(lab, cfg))].reset_index(drop=True)
    return lab, extract_features(pd.read_parquet(PROC / d / "messages.parquet"), lab)


def _capped(lab: pd.DataFrame, cap_s: float) -> tuple[np.ndarray, np.ndarray]:
    t = lab["duration_s"].to_numpy(float)
    e = lab["event"].to_numpy(bool) & (t <= cap_s)
    return np.minimum(t, cap_s), e


def _rsf_grid(t: np.ndarray, cap_s: float, n: int = 120) -> np.ndarray:
    """Làm tròn T lên lưới log (≤ n mốc) cho RSF."""
    grid = np.unique(np.concatenate([[0.0], np.geomspace(1, cap_s, n)]))
    return grid[np.searchsorted(grid, t, side="left").clip(0, len(grid) - 1)]


def _surv_at(fns, times: np.ndarray) -> np.ndarray:
    return np.stack([fn(times) for fn in fns])


def run(d: str, cfg: dict) -> pd.DataFrame:
    sv = cfg["head_a"]["survival"]
    cap_s, deltas = sv["admin_cap_s"], [cfg["head_a"]["delta_s"], *cfg["head_a"]["sensitivity_delta_s"]]
    lab, X = load(d, cfg)
    groups, start = lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    t, e = _capped(lab, cap_s)
    known = lab["y"].notna().to_numpy()
    seed, rows = cfg["test_seed"], []
    ibs_times = np.geomspace(5 * 60, 72 * H, 25)
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, cfg["cv"]["outer_k"])):
        assert_no_group_leak(groups, tr, va)
        sc = StandardScaler().fit(X.iloc[tr])
        Xtr, Xva = sc.transform(X.iloc[tr]), sc.transform(X.iloc[va])
        va_k = va[known[va]]
        Xva_k = sc.transform(X.iloc[va_k])
        preds: dict[str, dict] = {}  # model → {"surv": (n_va_k, len(deltas)), "risk": (n_va,), "ibs_surv": ...}

        t0 = time.time()
        cox = CoxPHFitter(penalizer=sv["cox_penalizer"])
        cox.fit(pd.DataFrame(Xtr, columns=X.columns).assign(T=t[tr], E=e[tr]), "T", "E")
        df_va = pd.DataFrame(Xva, columns=X.columns)
        sf = cox.predict_survival_function(pd.DataFrame(Xva_k, columns=X.columns), times=deltas).T.to_numpy()
        sf_ibs = cox.predict_survival_function(df_va, times=ibs_times).T.to_numpy()
        preds["cox"] = {"surv": sf, "risk": cox.predict_partial_hazard(df_va).to_numpy(), "ibs": sf_ibs,
                        "fit_s": time.time() - t0}

        t0 = time.time()
        rng = np.random.default_rng(seed + fold)
        sub = rng.choice(len(tr), min(sv["rsf_max_fit"], len(tr)), replace=False)
        rsf = RandomSurvivalForest(n_estimators=sv["rsf_trees"], min_samples_leaf=sv["rsf_min_leaf"],
                                   max_features="sqrt", n_jobs=-1, random_state=seed)
        rsf.fit(Xtr[sub], Surv.from_arrays(e[tr][sub], _rsf_grid(t[tr][sub], cap_s)))
        lo = max(rsf.unique_times_[0], ibs_times[0])
        preds["rsf"] = {"surv": _surv_at(rsf.predict_survival_function(Xva_k), np.array(deltas)),
                        "risk": rsf.predict(Xva),
                        "ibs": _surv_at(rsf.predict_survival_function(Xva), np.clip(ibs_times, lo, None)),
                        "fit_s": time.time() - t0}

        y_tr_s = Surv.from_arrays(e[tr], t[tr])
        y_va_s = Surv.from_arrays(e[va], t[va])
        ok_ibs = t[va] < t[tr].max()
        for name, p in preds.items():
            row = {"dataset": d, "fold": fold, "model": name, "n_fit": len(tr), "n_val": len(va),
                   "fit_s": p["fit_s"],
                   "c_index": concordance_index_censored(e[va], t[va], p["risk"])[0],
                   "ibs": integrated_brier_score(y_tr_s, y_va_s[ok_ibs], p["ibs"][ok_ibs], ibs_times)}
            for j, dl in enumerate(deltas):  # P(tắt trong Δ) = S(Δ), đánh giá trên mẫu biết nhãn ở Δ
                y_d = label_at(lab.iloc[va_k], dl).to_numpy()
                m = ~np.isnan(y_d)
                row[f"pr_auc_{dl / H:g}h"] = average_precision_score(y_d[m], p["surv"][m, j])
                row[f"brier_{dl / H:g}h"] = brier_score_loss(y_d[m], p["surv"][m, j])
            rows.append(row)

        # LightGBM phân loại (A5, không resample) — học lại cho từng Δ, chỉ trên mẫu biết nhãn
        row = {"dataset": d, "fold": fold, "model": "lgbm_clf", "n_fit": int(known[tr].sum()), "n_val": len(va_k)}
        t0 = time.time()
        for dl in deltas:
            y_all = label_at(lab, dl).to_numpy()
            ftr = tr[~np.isnan(y_all[tr])]
            fva = va[~np.isnan(y_all[va])]
            clf = MODELS["lgbm"](seed).fit(X.iloc[ftr], y_all[ftr].astype(int))
            p = clf.predict_proba(X.iloc[fva])[:, 1]
            row[f"pr_auc_{dl / H:g}h"] = average_precision_score(y_all[fva], p)
            row[f"brier_{dl / H:g}h"] = brier_score_loss(y_all[fva], p)
            if dl == deltas[0]:
                row["roc_auc_48h"] = roc_auc_score(y_all[fva], p)
        row["fit_s"] = time.time() - t0
        rows.append(row)
        print(d, fold, {r["model"]: round(r["pr_auc_48h"], 4) for r in rows[-3:]}, flush=True)
    res = pd.DataFrame(rows)
    res.attrs["info"] = {"n": len(lab), "n_censored_48h": int((~known).sum()),
                         "n_conv": int(lab["conversation_id"].nunique())}
    return res


def write_report(all_res: list[pd.DataFrame], cfg: dict) -> None:
    deltas = [cfg["head_a"]["delta_s"], *cfg["head_a"]["sensitivity_delta_s"]]
    dcols = sorted({f"{dl / H:g}h" for dl in deltas}, key=lambda s: float(s[:-1]))
    lines = ["# Head A — survival so với phân loại (A6, X3, X5)", "",
             "> Sinh bởi `python -m src.m3_health.run_survival`. Chỉ tập train ; cùng hội thoại và fold forward "
             "chaining với `HEAD_A_CV.md`. P(tắt trong Δ) của survival = S(Δ | x) từ **một** mô hình ; LightGBM học "
             "lại cho từng Δ. T kiểm duyệt hành chính ở 7 ngày ; RSF fit trên mẫu con "
             f"{cfg['head_a']['survival']['rsf_max_fit']:,} dòng. C-index Harrell, IBS trên 5 phút–72h.", ""]
    for res in all_res:
        d, info = res["dataset"].iloc[0], res.attrs["info"]
        lines += [f"## {d}", "", f"{info['n']:,} mẫu ({info['n_censored_48h']:,} mẫu censored ở 48h — mô hình "
                  f"phân loại phải bỏ, survival dùng được), {info['n_conv']:,} hội thoại.", "",
                  "| Mô hình | C-index | IBS | " + " | ".join(f"PR-AUC {c}" for c in dcols) + " | Brier 48h | Thời gian fit (s) |",
                  "|---" * (5 + len(dcols)) + "|"]
        for m, g in res.groupby("model", sort=False):
            c = lambda col: mean_sd(g[col]) if col in g and g[col].notna().all() else "—"
            lines.append(f"| {m} | {c('c_index')} | {c('ibs')} | " + " | ".join(c(f"pr_auc_{x}") for x in dcols)
                         + f" | {c('brier_48h')} | {g['fit_s'].mean():.0f} |")
        lines.append("")
    out = ROOT / "docs" / "results" / "HEAD_A_SURVIVAL.md"
    out.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    out = []
    for d in datasets:
        res = run(d, cfg)
        res.to_csv(ROOT / "reports" / f"headA_survival_{d}.csv", index=False)
        out.append(res)
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        for m, g in res.groupby("model", sort=False):
            append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:{m}", "pr_auc_48h", g["pr_auc_48h"],
                          notes="A6 survival vs classifier, forward chaining K=5")
    write_report(out, cfg)


if __name__ == "__main__":
    main(sys.argv[1:] or ["E2", "E1"])
