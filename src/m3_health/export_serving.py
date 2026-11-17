"""Export mô hình M3 cho serving (C6) — chạy sau khi freeze config (19/11).

Chạy:  python -m src.m3_health.export_serving [--version v1]

App chat tiếng Việt nên bản serving dùng đặc trưng không phụ thuộc ngôn ngữ huấn luyện:
- Head A: LightGBM + none trên **T1+T2** (21 đặc trưng `features.extract_features`, đúng hàm serving gọi), học
  trên E1 (chat 2 người) ; τ1/τ2 theo alert budget trên OOF. T3 dùng từ điển tiếng Anh, T4 cần chạy BGE-M3 mỗi
  tin → để ở báo cáo (mô hình chốt), không đưa lên server.
- Head B: TF-IDF + LR trên H1 (ViHSD, tiếng Việt), Platt, τ_B = FPR ≤ 5%.
Ghi `models/m3/<version>/{head_a,head_b}.joblib` (không commit, .gitignore) + `metadata.json` (commit) và
`models/m3/.active`. Chỉ dùng train (`load_train`), không đọc data/locked.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json

import joblib
import numpy as np
from sklearn.metrics import average_precision_score

from src.eval.report import data_hash, git_commit
from src.m3_health import run_head_a, run_head_b
from src.m3_health.final_models import fit_head_a, fit_head_b, head_a_taus, oof_head_a
from src.m3_health.labels import PROC, ROOT, load_config

HEAD_A_DATA, HEAD_B_DATA = "E1", "H1"


def main(version: str) -> None:
    cfg = load_config()
    seed, k = cfg["test_seed"], cfg["cv"]["outer_k"]
    out = ROOT / "models" / "m3" / version
    out.mkdir(parents=True, exist_ok=True)

    lab, X = run_head_a.load(HEAD_A_DATA, cfg)
    cols = list(X.columns)
    y = lab["y"].to_numpy(int)
    p_oof = oof_head_a(lab, X, cols, seed, k)
    tau1, tau2 = head_a_taus(y, p_oof, cfg["head_a"]["threshold"])
    ok = np.isfinite(p_oof)
    head_a = fit_head_a(lab, X, cols, seed)
    joblib.dump(head_a, out / "head_a.joblib")
    print(f"Head A {HEAD_A_DATA}: OOF PR-AUC {average_precision_score(y[ok], p_oof[ok]):.4f}, τ1={tau1:.3f}, τ2={tau2:.3f}")

    frame = run_head_b.load(HEAD_B_DATA)
    head_b, tau_b = fit_head_b(frame, cfg, seed)
    joblib.dump(head_b, out / "head_b.joblib")
    print(f"Head B {HEAD_B_DATA}: τ_B={tau_b:.3f}")

    meta = {
        "module": "m3", "version": version, "status": "active",
        "date": dt.date.today().isoformat(), "git_commit": git_commit(), "seed": seed,
        "head_a": {"model_file": "head_a.joblib", "dataset": HEAD_A_DATA, "model": "lgbm", "strategy": "none",
                   "features": cols, "calibration": "prior shift + isotonic (20% group muộn nhất)",
                   "tau1": tau1, "tau2": tau2, "alert_budget": cfg["head_a"]["threshold"]["alert_budget"],
                   "oof_pr_auc": float(average_precision_score(y[ok], p_oof[ok])), "n_samples": len(lab),
                   "data_hash": data_hash(PROC / HEAD_A_DATA / "labels_headA_train.parquet",
                                          PROC / HEAD_A_DATA / "messages.parquet")},
        "head_b": {"model_file": "head_b.joblib", "dataset": HEAD_B_DATA, "model": "tfidf_lr",
                   "calibration": "platt (20% group)", "tau_nudge": tau_b,
                   "threshold_fpr": cfg["head_b"]["threshold_fpr"], "n_samples": len(frame),
                   "data_hash": data_hash(PROC / HEAD_B_DATA / "labels_headB_train.parquet",
                                          PROC / HEAD_B_DATA / "messages.parquet")},
    }
    (out / "metadata.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (out.parent / ".active").write_text(version, encoding="utf-8")
    print("→", out.relative_to(ROOT))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", default="v1")
    main(parser.parse_args().version)
