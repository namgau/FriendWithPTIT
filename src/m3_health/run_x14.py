"""C5/X14: Does an H1-trained multilingual toxicity score help forecast G1?

Only H1 and G1 train labels are read. G1 text stops before the attack turn via
features_b.build_g1. The BGE-M3 encoder is frozen at the C1 revision.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, roc_auc_score

from src.eval.bootstrap import paired_cluster_bootstrap
from src.eval.calibration import fit_calibrator
from src.eval.report import append_ledger, data_hash, git_commit
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.c2_toxicity_data import load_h1_train_data
from src.m3_health.embeddings import make_encoder
from src.m3_health.features_b import NUMERIC_COLUMNS
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_head_b import load as load_head_b, make_model

REPORT = ROOT / "docs" / "results" / "HEAD_B_X14.md"
OOF = ROOT / "reports" / "x14_g1_oof.parquet"


def toxicity_feature(h1: pd.DataFrame, g1: pd.DataFrame, seed: int) -> tuple[np.ndarray, float]:
    """Fit H1 toxicity only, then infer scores for G1 pre-attack prefixes."""
    encoder = make_encoder()
    if encoder.name != "bge_m3":
        raise ValueError("X14 requires frozen C1 BGE-M3")
    print("Encoding H1 train with frozen BGE-M3...", flush=True)
    h1_emb = encoder.encode(h1["text"].fillna("").astype(str).tolist())
    y_h1 = h1["label"].to_numpy(int)
    groups_h1 = h1["group_id"].to_numpy()
    fit, cal = held_out_group_split(groups_h1, 0.2, seed=seed, y=y_h1)
    assert_no_group_leak(groups_h1, fit, cal)
    classifier = LogisticRegression(C=10.0, class_weight="balanced", max_iter=1000, random_state=seed)
    classifier.fit(h1_emb[fit], y_h1[fit])
    h1_cal_ap = average_precision_score(y_h1[cal], classifier.predict_proba(h1_emb[cal])[:, 1])
    print(f"H1 held-out PR-AUC={h1_cal_ap:.4f}; refitting on all H1 train...", flush=True)
    classifier.fit(h1_emb, y_h1)
    print("Encoding G1 train prefixes with frozen BGE-M3...", flush=True)
    g1_emb = encoder.encode(g1["text"].fillna("").astype(str).tolist())
    return classifier.predict_proba(g1_emb)[:, 1], float(h1_cal_ap)


def compare_g1(frame: pd.DataFrame, cfg: dict, seed: int) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    """Paired group CV for the same G1 prefixes, with independent calibration."""
    y = frame["y"].to_numpy(int)
    groups = frame["group_id"].to_numpy()
    hb = cfg["head_b"]
    oof = pd.DataFrame({"conversation_id": frame["conversation_id"], "group_id": groups, "y": y,
                        "x14_toxicity": frame["x14_toxicity"].to_numpy(float)})
    fold_rows = []
    for fold, (tr, va) in enumerate(group_kfold(groups, k=hb["cv_folds"], seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(groups[tr], hb["calibration_fraction"], seed=seed + fold, y=y[tr])
        fit, cal = tr[fit_i], tr[cal_i]
        assert_no_group_leak(groups, fit, cal)
        assert_no_group_leak(groups, fit, va)
        assert_no_group_leak(groups, cal, va)
        for name, columns in (("base", NUMERIC_COLUMNS), ("plus_x14", NUMERIC_COLUMNS + ["x14_toxicity"])):
            model = make_model(cfg, seed + fold, numeric_columns=columns)
            model.fit(frame.iloc[fit], y[fit])
            raw_cal = model.predict_proba(frame.iloc[cal])[:, 1]
            calibrator = fit_calibrator("platt", raw_cal, y[cal])
            p_cal = calibrator(raw_cal)
            tau = float(threshold_by_fpr(y[cal], p_cal, hb["threshold_fpr"])["threshold"])
            p = np.asarray(calibrator(model.predict_proba(frame.iloc[va])[:, 1]), dtype=float)
            oof.loc[va, name] = p
            at = confusion_at(y[va], p, tau)
            fold_rows.append({"fold": fold, "model": name, "n_fit": len(fit), "n_cal": len(cal),
                              "n_val": len(va), "pr_auc": average_precision_score(y[va], p),
                              "roc_auc": roc_auc_score(y[va], p), "tau": tau,
                              "recall": at["recall"], "fpr": at["fpr"]})
        print(f"G1 fold {fold + 1}/{hb['cv_folds']} complete", flush=True)
    if oof[["base", "plus_x14"]].isna().any().any():
        raise RuntimeError("Incomplete G1 out-of-fold predictions")
    delta = paired_cluster_bootstrap(average_precision_score, y, oof["plus_x14"], oof["base"],
                                     groups, B=cfg["bootstrap"]["B"], ci=cfg["bootstrap"]["ci"], seed=seed)
    return pd.DataFrame(fold_rows), oof, delta


def run() -> dict:
    cfg = load_config()
    seed = int(cfg["test_seed"])
    h1 = load_h1_train_data()
    g1 = load_head_b("G1")
    x14, h1_ap = toxicity_feature(h1, g1, seed)
    g1 = g1.assign(x14_toxicity=x14)
    folds, oof, delta = compare_g1(g1, cfg, seed)
    summary = folds.groupby("model")["pr_auc"].agg(["mean", "std"])
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    OOF.parent.mkdir(parents=True, exist_ok=True)
    oof.to_parquet(OOF, index=False)
    data_files = [PROC / "H1" / "labels_headB_train.parquet", PROC / "H1" / "messages.parquet",
                  PROC / "G1" / "labels_headB_train.parquet", PROC / "G1" / "messages.parquet"]
    entry = append_ledger("m3_c5_x14", "C", cfg, data_hash(*data_files), [seed],
                          "G1:tfidf_lr+H1_BGE-M3_toxicity", "paired_delta_pr_auc",
                          [delta["estimate"]], metric_sd=delta["se"],
                          notes=f"G1 group CV; BGE-M3 frozen; CI95=[{delta['lo']:.4f},{delta['hi']:.4f}]; p={delta['p_value']:.4f}")
    lines = [
        "# C5 / X14 — điểm độc hại H1 trên dự báo G1",
        "",
        "> Chỉ dùng H1/G1 train; tập test khóa chưa mở. Sinh bởi `python -m src.m3_health.run_x14`.",
        "",
        "## Thiết kế",
        "",
        "BGE-M3 đa ngữ đóng băng theo C1 mã hóa bình luận H1 và tiền tố G1. LR học nhãn độc hại chỉ trên H1 train; điểm độc hại dự đoán trên G1 là một đặc trưng bổ sung cho baseline TF-IDF + LR G1. Không dùng nhãn G1 khi fit bộ phân loại H1. Tiền tố G1 dừng trước lượt công kích. So sánh ghép cặp cùng 5 group fold theo trang Wikipedia, Platt và τ_B chọn trong train/cal của từng fold.",
        "",
        f"H1 held-out group PR-AUC của bộ phân loại embedding + LR: **{h1_ap:.3f}**; sau kiểm tra này, fit lại trên toàn H1 train trước khi tạo điểm G1. Đây là mô hình phụ X14, không thay thế PhoBERT C2.",
        "",
        "## Kết quả CV trên G1 train",
        "",
        "| Mô hình | PR-AUC mean ± SD |",
        "|---|---:|",
        f"| TF-IDF + LR | {summary.loc['base','mean']:.3f} ± {summary.loc['base','std']:.3f} |",
        f"| TF-IDF + LR + X14 | {summary.loc['plus_x14','mean']:.3f} ± {summary.loc['plus_x14','std']:.3f} |",
        "",
        f"Paired cluster bootstrap Δ(PR-AUC, +X14 − base) = **{delta['estimate']:+.3f}**, CI 95% **[{delta['lo']:+.3f}, {delta['hi']:+.3f}]**, p = {delta['p_value']:.3f} ({delta['B_valid']} lần lặp hợp lệ).",
        "",
        "X14 là kiểm tra chuyển ngôn ngữ gián tiếp: nhãn H1 đo độc hại của một bình luận tiếng Việt, còn G1 đo khả năng công kích *trong tương lai* của hội thoại tiếng Anh. Hiệu năng G1 không chứng minh chất lượng cảnh báo trên chat tiếng Việt của app.",
        "",
        f"Provenance: commit `{git_commit()}`; input hash `{entry['data_hash']}`; seed {seed}; OOF tại `reports/x14_g1_oof.parquet` (không commit).",
        "",
    ]
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    out = {"h1_ap": h1_ap, "folds": folds.to_dict("records"), "delta": delta, "ledger": entry}
    print(json.dumps({"h1_ap": h1_ap, "base": float(summary.loc["base", "mean"]),
                      "plus_x14": float(summary.loc["plus_x14", "mean"]), "delta": delta}, indent=2), flush=True)
    return out


if __name__ == "__main__":
    run()
