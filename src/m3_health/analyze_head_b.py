"""Phân tích lỗi Head B và horizon/lead-time trên G1.

Chạy: python -m src.m3_health.analyze_head_b [G1 H1 H2]

Đọc OOF đã lưu bởi ``run_head_b`` (chỉ tập train, không đọc test). Horizon dùng
mô hình ``tfidf_lr`` theo đúng fold/calibration của runner, chấm các tiền tố ngắn hơn
để đo mô hình cảnh báo được trước lượt công kích bao nhiêu lượt.
"""
from __future__ import annotations

import re
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, roc_auc_score

from src.eval.report import append_ledger, data_hash
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import threshold_by_fpr
from src.m3_health.features_b import build_g1_prefixes
from src.m3_health.labels import PROC, ROOT, load_config, load_train
from src.m3_health.run_head_b import _fit_calibration, load, make_model

ERRORS = ROOT / "reports" / "headB_errors.csv"
SLICES = ROOT / "reports" / "headB_error_slices.csv"
HORIZON = ROOT / "reports" / "headB_horizon.csv"
REPORT = ROOT / "docs" / "results" / "HEAD_B_ERRORS.md"
_URL = re.compile(r"https?://\S+", re.IGNORECASE)
_MENTION = re.compile(r"@[\w.]+")


def _snippet(value, limit: int = 180) -> str:
    text = " ".join(str(value).split())
    text = _URL.sub("<URL>", text)
    text = _MENTION.sub("@<user>", text)
    return text if len(text) <= limit else text[: limit - 1] + "…"


def _binary_metrics(y, p, pred) -> dict:
    y, p, pred = np.asarray(y, int), np.asarray(p, float), np.asarray(pred, int)
    tp = int(((y == 1) & (pred == 1)).sum())
    fp = int(((y == 0) & (pred == 1)).sum())
    fn = int(((y == 1) & (pred == 0)).sum())
    tn = int(((y == 0) & (pred == 0)).sum())
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    fpr = fp / (fp + tn) if fp + tn else 0.0
    return {
        "n": len(y),
        "n_pos": int(y.sum()),
        "n_pred_pos": int(pred.sum()),
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "tn": tn,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "fpr": fpr,
    }


def _add_slices(data: pd.DataFrame, dataset: str) -> pd.DataFrame:
    data = data.copy()
    data["length_bucket"] = pd.cut(
        data["n_chars"],
        bins=[-1, 50, 100, 200, 400, np.inf],
        labels=["≤50", "51–100", "101–200", "201–400", ">400"],
    )
    data["lexicon_bucket"] = np.where(data["toxic_hits"] > 0, "có từ độc hại", "không")
    if dataset == "G1":
        data["prefix_bucket"] = pd.cut(
            data["n_messages"],
            bins=[-1, 1, 2, 4, 9, np.inf],
            labels=["1", "2", "3–4", "5–9", "10+"],
        )
    return data


def _error_frame(dataset: str, frame: pd.DataFrame, oof: pd.DataFrame, tau: float) -> pd.DataFrame:
    data = frame.drop(columns=["y"], errors="ignore").merge(
        oof[["conversation_id", "fold", "y", "p"]],
        on="conversation_id",
        how="inner",
        validate="one_to_one",
    )
    data["dataset"] = dataset
    data["pred"] = (data["p"] >= tau).astype(int)
    data["error_type"] = np.select(
        [(data.y == 1) & (data.pred == 1), (data.y == 0) & (data.pred == 1),
         (data.y == 1) & (data.pred == 0)],
        ["TP", "FP", "FN"],
        default="TN",
    )
    data = _add_slices(data, dataset)
    keep = [
        "dataset", "conversation_id", "group_id", "fold", "y", "p", "pred", "error_type",
        "n_messages", "n_chars", "toxic_hits", "length_bucket", "lexicon_bucket",
    ]
    keep += [c for c in ["prefix_len", "attack_turn_idx", "n_turns", "prefix_bucket"] if c in data]
    data = data[keep + ["text"]].copy()
    data["text"] = data["text"].map(_snippet)
    return data


def _slice_rows(data: pd.DataFrame, tau: float) -> pd.DataFrame:
    rows = []
    for column in ("length_bucket", "lexicon_bucket", "prefix_bucket"):
        if column not in data:
            continue
        for value, group in data.groupby(column, observed=True):
            if len(group) == 0:
                continue
            metrics = _binary_metrics(group.y, group.p, group.pred)
            pr_auc = average_precision_score(group.y, group.p) if group.y.nunique() > 1 else np.nan
            rows.append({
                "dataset": group["dataset"].iloc[0],
                "slice": column,
                "value": str(value),
                "pr_auc": pr_auc,
                "threshold": tau,
                **metrics,
            })
    return pd.DataFrame(rows)


def _summary_rows(data: pd.DataFrame, tau: float) -> pd.DataFrame:
    rows = []
    for dataset, group in data.groupby("dataset", sort=False):
        metrics = _binary_metrics(group.y, group.p, group.pred)
        pr_auc = average_precision_score(group.y, group.p) if group.y.nunique() > 1 else np.nan
        roc_auc = roc_auc_score(group.y, group.p) if group.y.nunique() > 1 else np.nan
        rows.append({
            "dataset": dataset,
            "prevalence": float(group.y.mean()),
            "threshold": tau,
            "pr_auc": pr_auc,
            "roc_auc": roc_auc,
            **metrics,
        })
    return pd.DataFrame(rows)


def analyze_errors(datasets: list[str], cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    all_data, all_slices, summaries = [], [], []
    for dataset in datasets:
        oof_path = ROOT / "reports" / f"headB_oof_{dataset}.csv"
        if not oof_path.exists():
            raise FileNotFoundError(f"{oof_path} chưa có; chạy run_head_b trước")
        oof = pd.read_csv(oof_path)
        needed = {"conversation_id", "y", "p", "group_id"}
        missing = needed - set(oof.columns)
        if missing:
            raise ValueError(f"{oof_path} thiếu cột {sorted(missing)}; chạy lại run_head_b")
        frame = load(dataset)
        tau = threshold_by_fpr(oof["y"], oof["p"], cfg["head_b"]["threshold_fpr"])["threshold"]
        data = _error_frame(dataset, frame, oof, tau)
        all_data.append(data)
        summaries.append(_summary_rows(data, tau))
        all_slices.append(_slice_rows(data, tau))
        print(dataset, f"tau={tau:.4f}", f"FP={int((data.error_type == 'FP').sum())}",
              f"FN={int((data.error_type == 'FN').sum())}", flush=True)
    errors = pd.concat(all_data, ignore_index=True)
    errors.to_csv(ERRORS, index=False)
    slices = pd.concat(all_slices, ignore_index=True)
    slices.to_csv(SLICES, index=False)
    return errors, slices, pd.concat(summaries, ignore_index=True)


def horizon_g1(cfg: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Đo lead-time trên G1: cảnh báo sớm nhất tại tiền tố dài ``eval_len``.

    ``lead = attack_turn_idx - eval_len``. ``eval_len = prefix_len`` là cảnh báo ngay
    trước lượt công kích (lead 0). Chỉ đánh giá các hội thoại hỏng; không dùng test.
    """
    frame = load("G1")
    labels = load_train("G1")
    messages = pd.read_parquet(PROC / "G1" / "messages.parquet")
    prefixes = build_g1_prefixes(messages, labels)
    y = frame["y"].to_numpy(int)
    groups = frame["group_id"].to_numpy()
    hb, seed = cfg["head_b"], cfg["test_seed"]
    rows, folds = [], []
    for fold, (tr, va) in enumerate(group_kfold(groups, hb["cv_folds"], seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(
            groups[tr], hb["calibration_fraction"], seed=seed + fold, y=y[tr]
        )
        fit, cal = tr[fit_i], tr[cal_i]
        assert_no_group_leak(groups, fit, cal)
        assert_no_group_leak(groups, fit, va)
        assert_no_group_leak(groups, cal, va)

        model = make_model(cfg, seed + fold)
        model.fit(frame.iloc[fit], y[fit])
        calibrator = _fit_calibration(y[cal], model.predict_proba(frame.iloc[cal])[:, 1])
        p_cal = calibrator(model.predict_proba(frame.iloc[cal])[:, 1])
        tau = threshold_by_fpr(y[cal], p_cal, hb["threshold_fpr"])["threshold"]

        va_frame = frame.iloc[va]
        va_ids = set(va_frame["conversation_id"])
        p_full = dict(zip(va_frame["conversation_id"],
                          calibrator(model.predict_proba(va_frame)[:, 1])))
        subset = prefixes[prefixes["conversation_id"].isin(va_ids)].copy()
        if subset.empty:
            continue
        subset["p"] = calibrator(model.predict_proba(subset)[:, 1])

        fold_rows = []
        for conversation_id, group in subset.groupby("conversation_id", sort=False):
            if int(group["y"].iloc[0]) != 1:
                continue
            group = group.sort_values("eval_len")
            hit = group[group["p"] >= tau]
            attack_turn = int(group["attack_turn_idx"].iloc[0])
            prefix_len = int(group["prefix_len"].iloc[0])
            first_len = int(hit["eval_len"].iloc[0]) if not hit.empty else np.nan
            score_full = float(p_full[conversation_id])
            row = {
                "dataset": "G1",
                "fold": fold,
                "conversation_id": conversation_id,
                "group_id": group["group_id"].iloc[0],
                "attack_turn_idx": attack_turn,
                "prefix_len": prefix_len,
                "first_alert_len": first_len,
                "lead_turns": attack_turn - first_len if np.isfinite(first_len) else np.nan,
                "detected": int(not hit.empty),
                "p_full": score_full,
                "detected_full": int(score_full >= tau),
                "threshold": tau,
            }
            rows.append(row)
            fold_rows.append(row)

        fold_df = pd.DataFrame(fold_rows)
        if not fold_df.empty:
            full = fold_df[fold_df["detected_full"] == 1]
            folds.append({
                "fold": fold,
                "n": len(fold_df),
                "detected": int(fold_df["detected"].sum()),
                "detected_frac": float(fold_df["detected"].mean()),
                "median_lead": float(fold_df.loc[fold_df.detected == 1, "lead_turns"].median()),
                "tp_full": len(full),
                "median_lead_full": float(full["lead_turns"].median()) if not full.empty else np.nan,
                "threshold": tau,
            })

    per_conversation = pd.DataFrame(rows)
    per_conversation.to_csv(HORIZON, index=False)
    return per_conversation, pd.DataFrame(folds)


def _write_report(errors: pd.DataFrame, slices: pd.DataFrame, summary: pd.DataFrame,
                  horizon: pd.DataFrame, fold_horizon: pd.DataFrame, cfg: dict) -> None:
    hb = cfg["head_b"]
    lines = [
        "# Head B — phân tích lỗi và horizon trên tập train",
        "",
        "> Sinh bởi `python -m src.m3_health.analyze_head_b`. Chỉ dùng out-of-fold của tập train; "
        "test **chưa mở**.",
        f"> Ngưỡng mô tả: τ_B toàn cục trên OOF theo FPR ≤ {hb['threshold_fpr']:.0%}. "
        "Đây là phân tích chẩn đoán, không dùng để chọn mô hình.",
        "",
        "## Tổng quan lỗi",
        "",
        "| Tập | N | Prevalence | τ_B | PR-AUC | Precision | Recall | F1 | FPR | FP | FN |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for row in summary.itertuples():
        lines.append(
            f"| {row.dataset} | {row.n:,} | {row.prevalence:.3f} | {row.threshold:.3f} | "
            f"{row.pr_auc:.3f} | {row.precision:.3f} | {row.recall:.3f} | {row.f1:.3f} | "
            f"{row.fpr:.3f} | {row.fp} | {row.fn} |"
        )

    lines += ["", "## Ví dụ lỗi", ""]
    for dataset in ("G1", "H1", "H2"):
        group = errors[errors["dataset"] == dataset]
        if group.empty:
            continue
        lines += [f"### {dataset}", ""]
        for error_type, title, ascending in (("FP", "Báo nhầm (FP)", False), ("FN", "Bỏ sót (FN)", True)):
            subset = (group[group["error_type"] == error_type]
                      .drop_duplicates(subset=["text"])
                      .sort_values("p", ascending=ascending)
                      .head(5))
            lines += [f"**{title} — top 5 theo xác suất**", "",
                      "| p | n_messages | Nhãn | Nội dung rút gọn |", "|---|---|---|---|"]
            for row in subset.itertuples():
                lines.append(f"| {row.p:.3f} | {row.n_messages} | {row.y} | {row.text} |")
            lines.append("")

    lines += ["## Metric theo slice", "",
              "| Tập | Slice | Giá trị | N | Dương tính | PR-AUC | Precision | Recall | FPR |",
              "|---|---|---|---|---|---|---|---|---|"]
    for row in slices.itertuples():
        pr_auc = "—" if pd.isna(row.pr_auc) else f"{row.pr_auc:.3f}"
        lines.append(
            f"| {row.dataset} | {row.slice} | {row.value} | {row.n:,} | {row.n_pos:,} | "
            f"{pr_auc} | {row.precision:.3f} | {row.recall:.3f} | {row.fpr:.3f} |"
        )

    lines += ["", "## Horizon/lead-time trên G1", "",
              "Chỉ tính trên hội thoại hỏng. `lead = attack_turn_idx - eval_len`; "
              "`eval_len = prefix_len` là cảnh báo ngay trước lượt công kích (lead 0). "
              "Cảnh báo sớm được chọn theo lần đầu tiên `p ≥ τ_B` trên các tiền tố ngắn hơn.",
              ""]
    if horizon.empty:
        lines.append("Không có hội thoại hỏng để đánh giá.")
    else:
        detected = horizon[horizon["detected"] == 1]
        full = horizon[horizon["detected_full"] == 1]
        lines += [
            "| Fold | N | Phát hiện sớm | Tỉ lệ | Median lead | TP tại prefix | Median lead TP | τ_B |",
            "|---|---|---|---|---|---|---|---|",
        ]
        for row in fold_horizon.itertuples():
            median = "—" if pd.isna(row.median_lead) else f"{row.median_lead:.1f}"
            median_full = "—" if pd.isna(row.median_lead_full) else f"{row.median_lead_full:.1f}"
            lines.append(
                f"| {row.fold} | {row.n} | {row.detected} | {row.detected_frac:.3f} | "
                f"{median} | {row.tp_full} | {median_full} | {row.threshold:.3f} |"
            )
        lines += [
            "",
            f"- Tổng số hội thoại hỏng: **{len(horizon)}**; cảnh báo sớm ở bất kỳ tiền tố nào: "
            f"**{len(detected)}** ({len(detected) / len(horizon):.1%}).",
            f"- Tại tiền tố triển khai (đúng giao thức hiện tại): **{len(full)} TP "
            f"({len(full) / len(horizon):.1%})**. Trong số TP này, lead trung vị: "
            f"**{full['lead_turns'].median():.1f} lượt**; IQR: "
            f"**{full['lead_turns'].quantile(0.25):.1f}–{full['lead_turns'].quantile(0.75):.1f}**.",
            f"- Trong số TP tại prefix, phát hiện trước ≥1 lượt: "
            f"**{(full['lead_turns'] >= 1).mean():.1%}**; ≥2 lượt: "
            f"**{(full['lead_turns'] >= 2).mean():.1%}**; ≥3 lượt: "
            f"**{(full['lead_turns'] >= 3).mean():.1%}**.",
            f"- Trên toàn bộ hội thoại hỏng, cảnh báo trước ≥1 lượt: "
            f"**{(detected['lead_turns'] >= 1).sum() / len(horizon):.1%}**; ≥2 lượt: "
            f"**{(detected['lead_turns'] >= 2).sum() / len(horizon):.1%}**; ≥3 lượt: "
            f"**{(detected['lead_turns'] >= 3).sum() / len(horizon):.1%}**.",
            "",
            "> Lưu ý: mô hình được huấn luyện trên tiền tố ngay trước lượt công kích; điểm ở các "
            "tiền tố ngắn hơn là ngoài phân phối huấn luyện. Ngưỡng giữ nguyên theo calibration, nên "
            "horizon chỉ là phân tích khảo sát, không phải metric chọn mô hình.",
        ]

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    print("wrote", REPORT)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    (ROOT / "reports").mkdir(exist_ok=True)
    errors, slices, summary = analyze_errors(datasets, cfg)
    horizon, fold_horizon = horizon_g1(cfg)
    _write_report(errors, slices, summary, horizon, fold_horizon, cfg)
    if not horizon.empty:
        digest = data_hash(PROC / "G1" / "labels_headB_train.parquet", PROC / "G1" / "messages.parquet")
        append_ledger(
            "m3_headB", "B", cfg, digest, [cfg["test_seed"]], "G1:tfidf_lr", "horizon_detected_frac",
            [fold_horizon["detected_frac"].mean()],
            notes=(
                f"horizon exploratory; median_lead={horizon.loc[horizon.detected == 1, 'lead_turns'].median():.2f}, "
                f"n_detected={int(horizon.detected.sum())}/{len(horizon)}, FPR<={cfg['head_b']['threshold_fpr']}"
            ),
        )


if __name__ == "__main__":
    main(sys.argv[1:] or ["G1", "H1", "H2"])
