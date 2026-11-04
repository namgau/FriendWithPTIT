"""Head A — độ nhạy của τ2 theo tỉ lệ chi phí C_FN : C_FP (để chốt cấu hình ; docs/06 §3.4).

Chạy:  python -m src.m3_health.run_cost_sensitivity_a

Dùng OOF đã hiệu chỉnh của LightGBM + none (giống A9). Với mỗi tỉ lệ: τ2 = min(chi phí thực nghiệm, Bayes)
như src/eval/threshold.choose_tau1_tau2 ; báo precision, recall, FPR, tỉ lệ cảnh báo, chi phí trung bình mỗi mẫu.
Kết quả: docs/results/HEAD_A_COST.md, ledger.
"""
from __future__ import annotations

import pandas as pd

from src.eval.report import append_ledger, data_hash
from src.eval.threshold import choose_tau1_tau2, confusion_at
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_error_analysis import _md_table, oof_head_a

RATIOS = [(1, 3), (1, 2), (1, 1), (2, 1), (3, 1), (5, 1)]


def main() -> None:
    cfg = load_config()
    th = cfg["head_a"]["threshold"]
    lines = ["# Head A — độ nhạy τ2 theo tỉ lệ chi phí C_FN : C_FP", "",
             "> Sinh bởi `python -m src.m3_health.run_cost_sensitivity_a`. OOF train (LightGBM + none, isotonic). "
             f"Cấu hình hiện tại: {th['c_fn']} : {th['c_fp']}. C_FN = bỏ sót một ca sắp tắt ; C_FP = làm phiền một cặp "
             "vẫn đang ổn. Ngưỡng Bayes = C_FP / (C_FN + C_FP).", ""]
    for d in ("E1", "E2"):
        _, _, y, p, _, _ = oof_head_a(d, cfg)
        rows = []
        for c_fn, c_fp in RATIOS:
            _, tau2 = choose_tau1_tau2(y, p, c_fn, c_fp, th["shallow_recall"])
            at = confusion_at(y, p, tau2)
            flag = p >= tau2
            cost = (c_fn * ((y == 1) & ~flag).sum() + c_fp * ((y == 0) & flag).sum()) / len(y)
            rows.append({"C_FN : C_FP": f"{c_fn} : {c_fp}", "τ2": tau2, "precision": at["precision"],
                         "recall": at["recall"], "FPR": at["fpr"], "tỉ lệ cảnh báo": flag.mean(), "chi phí / mẫu": cost})
        df = pd.DataFrame(rows)
        lines += [f"## {d} (prevalence {y.mean():.3f}, {len(y):,} mẫu OOF)", "", *_md_table(df), ""]
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        for r in rows:
            append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:lgbm:none@cost{r['C_FN : C_FP'].replace(' ', '')}",
                          "recall_at_tau2", [r["recall"]], notes=f"tau2={r['τ2']:.3f}, precision={r['precision']:.3f}, "
                          f"alert_rate={r['tỉ lệ cảnh báo']:.3f}")
    out = ROOT / "docs" / "results" / "HEAD_A_COST.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


if __name__ == "__main__":
    main()
