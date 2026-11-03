"""Baseline Head B trên G1/H1/H2.

Chạy: python -m src.m3_health.run_head_b [G1 H1 H2]
Chỉ dùng labels_headB_train.parquet; test trong data/locked không được đọc.
"""
from __future__ import annotations

import sys
import time

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    brier_score_loss,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from src.eval.bootstrap import cluster_bootstrap
from src.eval.calibration import ece, fit_calibrator
from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.features_b import NUMERIC_COLUMNS, build_g1, build_single
from src.m3_health.labels import PROC, ROOT, load_config, load_train

# G1: tiền tố hỏng dài hơn lành ~0,5 lượt → kiểm tra mô hình có học nội dung hay chỉ học độ dài
LENGTH_COLUMNS = ["n_messages", "n_chars", "n_words", "last_n_chars", "last_n_words"]
LR_VARIANTS = {  # tên → (dùng TF-IDF?, cột số)
    "length_lr": (False, LENGTH_COLUMNS),
    "tfidf_lr": (True, NUMERIC_COLUMNS),
    "tfidf_lr_nolen": (True, [c for c in NUMERIC_COLUMNS if c not in LENGTH_COLUMNS]),
}
MODELS = ("majority", "lexicon", *LR_VARIANTS)
CALIBRATION = "platt"


def load(dataset: str) -> pd.DataFrame:
    labels = load_train(dataset)
    messages = pd.read_parquet(PROC / dataset / "messages.parquet")
    frame = build_g1(messages, labels) if dataset == "G1" else build_single(messages, labels)
    return frame.reset_index(drop=True)


def make_model(cfg: dict, seed: int, use_text: bool = True, numeric_columns=NUMERIC_COLUMNS) -> Pipeline:
    hb = cfg["head_b"]
    text = TfidfVectorizer(
        ngram_range=(1, 2),
        min_df=hb["min_df"],
        max_features=hb["max_features"],
        sublinear_tf=True,
    )
    numeric = StandardScaler(with_mean=False)
    transformers = [("text", text, "text")] if use_text else []
    preprocess = ColumnTransformer(
        transformers + [("numeric", numeric, list(numeric_columns))],
        sparse_threshold=1.0,
        remainder="drop",
    )
    classifier = LogisticRegression(
        max_iter=2000,
        class_weight="balanced",
        solver="liblinear",
        random_state=seed,
    )
    return Pipeline([("preprocess", preprocess), ("classifier", classifier)])


def _metrics(y, p, threshold: float | None = None) -> dict:
    row = {
        "pr_auc": average_precision_score(y, p),
        "roc_auc": roc_auc_score(y, p),
        "brier": brier_score_loss(y, p),
        "ece": ece(y, p),
        # ngưỡng 0,5 để so với tài liệu (CRAFT báo accuracy trên G1 cân bằng ; ViHSD báo macro-F1)
        "acc_05": accuracy_score(y, (p >= 0.5).astype(int)),
        "macro_f1_05": f1_score(y, (p >= 0.5).astype(int), average="macro", zero_division=0),
    }
    if threshold is not None:
        at = confusion_at(y, p, threshold)
        row.update({
            "threshold": threshold,
            "precision": precision_score(y, (p >= threshold).astype(int), zero_division=0),
            "recall": recall_score(y, (p >= threshold).astype(int), zero_division=0),
            "f1": f1_score(y, (p >= threshold).astype(int), zero_division=0),
            "fpr": at["fpr"],
            "accuracy": accuracy_score(y, (p >= threshold).astype(int)),
        })
    return row


def _lexicon_scores(frame: pd.DataFrame) -> np.ndarray:
    return np.clip(frame["last_toxic_hits"].to_numpy(float), 0.0, 1.0)


def _fit_calibration(y_cal, raw_cal):
    if len(np.unique(y_cal)) < 2:
        return lambda values: np.asarray(values, dtype=float)
    return fit_calibrator(CALIBRATION, raw_cal, y_cal)


def run(dataset: str, cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    frame = load(dataset)
    y = frame["y"].to_numpy(int)
    groups = frame["group_id"].to_numpy()
    hb = cfg["head_b"]
    seed, k = cfg["test_seed"], hb["cv_folds"]
    rows, oof = [], []
    for fold, (tr, va) in enumerate(group_kfold(groups, k, seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(
            groups[tr],
            hb["calibration_fraction"],
            seed=seed + fold,
            y=y[tr],
        )
        fit, cal = tr[fit_i], tr[cal_i]
        assert_no_group_leak(groups, fit, cal)
        assert_no_group_leak(groups, fit, va)
        assert_no_group_leak(groups, cal, va)

        prevalence = float(y[fit].mean())
        majority = np.full(len(va), prevalence)
        rows.append({
            "dataset": dataset,
            "fold": fold,
            "model": "majority",
            "n_fit": len(fit),
            "n_cal": len(cal),
            "n_val": len(va),
            "pr_auc": average_precision_score(y[va], majority),
            "roc_auc": 0.5,
        })

        lexicon_cal, lexicon_va = _lexicon_scores(frame.iloc[cal]), _lexicon_scores(frame.iloc[va])
        tau_lex = threshold_by_fpr(y[cal], lexicon_cal, hb["threshold_fpr"])["threshold"]
        rows.append({
            "dataset": dataset,
            "fold": fold,
            "model": "lexicon",
            "n_fit": len(fit),
            "n_cal": len(cal),
            "n_val": len(va),
            **_metrics(y[va], lexicon_va, tau_lex),
        })
        if not np.isfinite(rows[-1]["threshold"]):
            rows[-1]["threshold"] = np.nan

        for name, (use_text, numeric_columns) in LR_VARIANTS.items():
            started = time.time()
            model = make_model(cfg, seed + fold, use_text, numeric_columns)
            model.fit(frame.iloc[fit], y[fit])
            raw_cal = model.predict_proba(frame.iloc[cal])[:, 1]
            raw_va = model.predict_proba(frame.iloc[va])[:, 1]
            calibrator = _fit_calibration(y[cal], raw_cal)
            p_cal, p_va = calibrator(raw_cal), calibrator(raw_va)
            tau = threshold_by_fpr(y[cal], p_cal, hb["threshold_fpr"])["threshold"]
            rows.append({
                "dataset": dataset,
                "fold": fold,
                "model": name,
                "n_fit": len(fit),
                "n_cal": len(cal),
                "n_val": len(va),
                "fit_s": time.time() - started,
                **_metrics(y[va], p_va, tau),
            })
            if name == "tfidf_lr":
                oof.append(pd.DataFrame({
                    "dataset": dataset,
                    "fold": fold,
                    "model": name,
                    "y": y[va],
                    "p": p_va,
                    "group_id": groups[va],
                }))
            print(dataset, fold, name, f"tau={tau:.3f}", f"pr_auc={rows[-1]['pr_auc']:.4f}", flush=True)

    info = {
        "n_rows": len(frame),
        "n_conversations": int(frame["conversation_id"].nunique()),
        "n_groups": int(frame["group_id"].nunique()),
        "prevalence": float(y.mean()),
    }
    return pd.DataFrame(rows), pd.concat(oof, ignore_index=True), info


def bootstrap_ci(oof: pd.DataFrame, cfg: dict) -> pd.DataFrame:
    """CI 95% cluster theo group cho out-of-fold ``tfidf_lr`` (không dùng test)."""
    boot = cfg["bootstrap"]
    rows = []
    metrics = (("pr_auc", average_precision_score), ("roc_auc", roc_auc_score), ("brier", brier_score_loss))
    for dataset, group in oof.groupby("dataset", sort=False):
        for name, metric in metrics:
            result = cluster_bootstrap(
                metric,
                group["y"].to_numpy(),
                group["p"].to_numpy(),
                group["group_id"].to_numpy(),
                B=boot["B"],
                ci=boot["ci"],
                seed=cfg["test_seed"],
            )
            rows.append({"dataset": dataset, "model": "tfidf_lr", "metric": name, **result})
            print(dataset, "bootstrap", name, f"CI=[{result['lo']:.4f}, {result['hi']:.4f}]", flush=True)
    return pd.DataFrame(rows)


def write_report(all_res: pd.DataFrame, oof: pd.DataFrame, boots: pd.DataFrame, infos: dict,
                 cfg: dict) -> None:
    hb = cfg["head_b"]
    lines = [
        "# Head B — kết quả CV trên tập train",
        "",
        "> Sinh bởi `python -m src.m3_health.run_head_b`. Tập test **chưa mở** (khóa tới 20/11).",
        f"> Outer: group K-fold K = {hb['cv_folds']} theo `group_id`; calibrator {CALIBRATION} fit trên "
        f"{hb['calibration_fraction']:.0%} group của outer-train; τ_B chọn theo FPR ≤ {hb['threshold_fpr']:.1%}.",
        "> G1 dùng tiền tố trước lượt công kích; H1/H2 dùng từng bình luận. Mean ± SD qua các fold.",
        "> `length_lr`: chỉ đặc trưng độ dài ; `tfidf_lr_nolen`: TF-IDF + đặc trưng số trừ độ dài — "
        "kiểm tra mô hình học nội dung hay chỉ học độ dài tiền tố (G1). Acc/Macro-F1@0.5 trên xác suất đã hiệu chỉnh.",
        f"> Bootstrap: cluster theo group, B = {cfg['bootstrap']['B']}, CI {cfg['bootstrap']['ci']:.0%} "
        "trên out-of-fold `tfidf_lr` (không dùng test).",
        "",
    ]
    for dataset, result in all_res.groupby("dataset"):
        info = infos[dataset]
        lines += [
            f"## {dataset}",
            "",
            f"N = {info['n_rows']:,} mẫu, {info['n_conversations']:,} hội thoại, "
            f"{info['n_groups']:,} group, prevalence {info['prevalence']:.3f}.",
            "",
            "| Mô hình | PR-AUC | ROC-AUC | Acc@0.5 | Macro-F1@0.5 | Brier | ECE | τ_B | Precision | Recall | F1 | FPR |",
            "|---|---|---|---|---|---|---|---|---|---|---|---|",
        ]
        for model in MODELS:
            subset = result[result["model"] == model]
            if subset.empty:
                continue
            cell = lambda column: mean_sd(subset[column].dropna()) if column in subset and subset[column].notna().any() else "—"
            lines.append(
                f"| {model} | {cell('pr_auc')} | {cell('roc_auc')} | {cell('acc_05')} | {cell('macro_f1_05')} | "
                f"{cell('brier')} | {cell('ece')} | "
                f"{cell('threshold')} | {cell('precision')} | {cell('recall')} | {cell('f1')} | {cell('fpr')} |"
            )
        subset = oof[oof["dataset"] == dataset]
        if not subset.empty:
            tau = threshold_by_fpr(subset["y"], subset["p"], hb["threshold_fpr"])
            at = confusion_at(subset["y"], subset["p"], tau["threshold"])
            lines += [
                "",
                f"**τ_B toàn cục trên out-of-fold `tfidf_lr`:** {tau['threshold']:.3f} "
                f"(FPR {at['fpr']:.3f}, precision {at['precision']:.3f}, recall {at['recall']:.3f}, "
                f"F1 {at['f1']:.3f}).",
            ]
        boot = boots[boots["dataset"] == dataset]
        if not boot.empty:
            first = boot.iloc[0]
            lines += [
                "",
                f"**Bootstrap CI 95% cho `tfidf_lr` (cluster theo group, B = {int(first['B'])}, "
                f"{int(first['B_valid'])} lần hợp lệ):**",
                "",
                "| Metric | Điểm | 95% CI | SE |",
                "|---|---|---|---|",
            ]
            for metric in ("pr_auc", "roc_auc", "brier"):
                row = boot[boot["metric"] == metric]
                if row.empty:
                    continue
                row = row.iloc[0]
                lines.append(
                    f"| {metric} | {row['estimate']:.4f} | "
                    f"[{row['lo']:.4f}, {row['hi']:.4f}] | {row['se']:.4f} |"
                )
        lines.append("")
    out = ROOT / "docs" / "results" / "HEAD_B_CV.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    (ROOT / "reports").mkdir(exist_ok=True)
    results, oofs, boots, infos = [], [], [], {}
    for dataset in datasets:
        result, oof, info = run(dataset, cfg)
        result.to_csv(ROOT / "reports" / f"headB_cv_{dataset}.csv", index=False)
        oof.to_csv(ROOT / "reports" / f"headB_oof_{dataset}.csv", index=False)
        boot = bootstrap_ci(oof, cfg)
        results.append(result)
        oofs.append(oof)
        boots.append(boot)
        infos[dataset] = info
        digest = data_hash(PROC / dataset / "labels_headB_train.parquet", PROC / dataset / "messages.parquet")
        for model in MODELS:
            subset = result[result["model"] == model]
            if subset.empty:
                continue
            append_ledger(
                "m3_headB",
                "B",
                cfg,
                digest,
                [cfg["test_seed"]],
                f"{dataset}:{model}",
                "pr_auc",
                subset["pr_auc"],
                notes=f"group K-fold K={cfg['head_b']['cv_folds']}, FPR<={cfg['head_b']['threshold_fpr']}",
            )
        for metric_name in ("pr_auc", "roc_auc"):
            row = boot[boot["metric"] == metric_name].iloc[0]
            append_ledger(
                "m3_headB",
                "B",
                cfg,
                digest,
                [cfg["test_seed"]],
                f"{dataset}:tfidf_lr",
                f"{metric_name}_bootstrap",
                [row["estimate"]],
                notes=(
                    f"cluster bootstrap B={int(row['B'])}, CI95=[{row['lo']:.4f}, {row['hi']:.4f}], "
                    f"SE={row['se']:.4f}"
                ),
                metric_sd=row["se"],
            )
    boot_all = pd.concat(boots, ignore_index=True)
    boot_all.to_csv(ROOT / "reports" / "headB_bootstrap.csv", index=False)
    write_report(pd.concat(results, ignore_index=True), pd.concat(oofs, ignore_index=True), boot_all, infos, cfg)


if __name__ == "__main__":
    main(sys.argv[1:] or ["G1", "H1", "H2"])
