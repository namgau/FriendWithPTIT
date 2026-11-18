"""A8 — mở tập test khóa **một lần** (20/11) và đánh giá các mô hình đã chốt.

Chạy:  python -m src.m3_health.run_test --open            (chỉ A, từ ngày 20/11)
       python -m src.m3_health.run_test --open --again    (chạy lại để đối chiếu tái lập ; được ghi lại)

Mô hình fit trên toàn bộ train bằng `final_models` (cùng giao thức CV) ; τ chọn trên train (OOF / calibration),
không bao giờ trên test. Báo PR-AUC [CI 95% cluster bootstrap theo group], ROC-AUC, Brier, ECE, confusion tại τ,
và paired cluster bootstrap Δ PR-AUC giữa mô hình chốt và từng baseline.
Ra: docs/results/TEST_RESULTS.md, reports/test_scores_<ID>.parquet (không commit), ledger (module m3_test),
experiments/test_opened.json (dấu đã mở test).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from src.eval.bootstrap import cluster_bootstrap, paired_cluster_bootstrap
from src.eval.calibration import ece
from src.eval.report import append_ledger, data_hash, git_commit
from src.eval.threshold import confusion_at
from src.m3_health import run_head_a, run_head_b
from src.m3_health.embedding_cache import has_cache
from src.m3_health.features import extract_features, rule_score
from src.m3_health.features_b import build_g1, build_single
from src.m3_health.final_models import fit_head_a, fit_head_b, head_a_taus, oof_head_a
from src.m3_health.labels import LOCK_FILE, LOCKED, PROC, ROOT, _sha, load_config
from src.m3_health.run_ablation_a import ALL, add_t3, add_t4

OPEN_DATE = dt.date(2026, 11, 20)
MARKER = ROOT / "experiments" / "test_opened.json"
HEAD_A, HEAD_B = ("E1", "E2"), ("G1", "H1", "H2")


def guard(open_flag: bool, again: bool, today: dt.date | None = None) -> list:
    """Trả về lịch sử mở test ; dừng nếu chưa đủ điều kiện."""
    today = today or dt.date.today()
    if not open_flag:
        raise SystemExit("Cần --open: script này đọc data/locked (chỉ A, đúng 1 lần).")
    if today < OPEN_DATE:
        raise SystemExit(f"Chưa tới ngày mở test ({OPEN_DATE}).")
    history = json.loads(MARKER.read_text(encoding="utf-8")) if MARKER.exists() else []
    if history and not again:
        raise SystemExit(f"Test đã mở lúc {history[0]['opened_at']} — chỉ chạy lại để đối chiếu với --again.")
    return history


def load_test(d: str) -> pd.DataFrame:
    head = "headA" if d in HEAD_A else "headB"
    test = pd.read_parquet(LOCKED / d / f"labels_{head}_test.parquet")
    lock = json.loads(LOCK_FILE.read_text(encoding="utf-8"))[d]
    if _sha(test["group_id"]) != lock["test_group_ids_sha256"]:
        raise RuntimeError(f"{d}: tập test không khớp test_lock.json")
    return test


def evaluate(d: str, y, scores: dict[str, np.ndarray], groups, chosen: str, taus: dict[str, float], cfg: dict) -> list[dict]:
    B, ci = cfg["bootstrap"]["B"], cfg["bootstrap"]["ci"]
    rows = []
    for name, s in scores.items():
        boot = cluster_bootstrap(average_precision_score, y, s, groups, B=B, ci=ci)
        row = {"dataset": d, "model": name, "n": len(y), "prevalence": float(np.mean(y)),
               "pr_auc": boot["estimate"], "pr_auc_lo": boot["lo"], "pr_auc_hi": boot["hi"],
               "roc_auc": roc_auc_score(y, s)}
        if np.all((s >= 0) & (s <= 1)):
            row.update(brier=brier_score_loss(y, s), ece=ece(y, s))
        if name in taus:
            at = confusion_at(y, s, taus[name])
            row.update(tau=taus[name], precision=at["precision"], recall=at["recall"], fpr=at["fpr"])
        if name != chosen:
            pb = paired_cluster_bootstrap(average_precision_score, y, scores[chosen], s, groups, B=B, ci=ci)
            row.update(delta_vs_chosen=pb["estimate"], delta_lo=pb["lo"], delta_hi=pb["hi"])
        rows.append(row)
    return rows


def head_a(d: str, cfg: dict) -> tuple[list[dict], pd.DataFrame]:
    seed, k = cfg["test_seed"], cfg["cv"]["outer_k"]
    lab, X = run_head_a.load(d, cfg)
    test = load_test(d)
    test = test[test["y"].notna()].reset_index(drop=True)
    X_te = extract_features(pd.read_parquet(PROC / d / "messages.parquet"), test)
    lab_X, test_X = add_t3(d, lab, X), add_t3(d, test, X_te)
    if has_cache(d):
        lab_X, test_X = add_t4(d, lab, lab_X), add_t4(d, test, test_X)
    full = list(lab_X.columns)
    y, y_te = lab["y"].to_numpy(int), test["y"].to_numpy(int)
    tau1, tau2 = head_a_taus(y, oof_head_a(lab, lab_X, full, seed, k), cfg["head_a"]["threshold"])
    chosen = "lgbm_" + ("T1-T4" if has_cache(d) else "T1-T3")
    scores = {"rule": rule_score(X_te).to_numpy(float), "gap_last": X_te["gap_last"].to_numpy(float),
              "lgbm_T1T2": fit_head_a(lab, lab_X, ALL, seed).predict_proba(test_X)[:, 1],
              chosen: fit_head_a(lab, lab_X, full, seed).predict_proba(test_X)[:, 1]}
    rows = evaluate(d, y_te, scores, test["group_id"].to_numpy(), chosen, {chosen: tau2}, cfg)
    rows[-1]["tau1"] = tau1
    return rows, pd.DataFrame({"group_id": test["group_id"], "y": y_te, **scores})


def head_b(d: str, cfg: dict) -> tuple[list[dict], pd.DataFrame]:
    frame = run_head_b.load(d)
    msgs = pd.read_parquet(PROC / d / "messages.parquet")
    test = load_test(d)
    te = (build_g1(msgs, test) if d == "G1" else build_single(msgs, test)).reset_index(drop=True)
    model, tau = fit_head_b(frame, cfg, cfg["test_seed"])
    scores = {"lexicon": np.clip(te["last_toxic_hits"].to_numpy(float), 0, 1),
              "tfidf_lr": model.predict_proba(te)[:, 1]}
    taus = {"tfidf_lr": tau}
    chosen = "tfidf_lr"
    if d in ("H1", "H2"):
        # C2 final artifact is trained only on H1 train. H2 is a frozen zero-shot
        # transfer; the H1 Platt parameters and threshold are not retuned on H2.
        from src.m3_health.final_phobert_c2 import DEFAULT_DIR, predict_cpu
        if not (DEFAULT_DIR / "manifest.json").is_file():
            raise FileNotFoundError(f"Missing final C2 PhoBERT artifact: {DEFAULT_DIR}")
        p_phobert, tau_phobert = predict_cpu(te["text"].fillna("").astype(str).tolist())
        scores["phobert_c2" if d == "H1" else "phobert_c2_x12"] = p_phobert
        taus["phobert_c2" if d == "H1" else "phobert_c2_x12"] = tau_phobert
        if d == "H1":
            chosen = "phobert_c2"
    y = te["y"].to_numpy(int)
    rows = evaluate(d, y, scores, te["group_id"].to_numpy(), chosen, taus, cfg)
    return rows, pd.DataFrame({"group_id": te["group_id"], "y": y, **scores})


def _fmt(r: dict) -> str:
    cells = [r["dataset"], r["model"], f"{r['pr_auc']:.3f} [{r['pr_auc_lo']:.3f}, {r['pr_auc_hi']:.3f}]",
             f"{r['roc_auc']:.3f}", f"{r['brier']:.3f}" if "brier" in r else "—",
             f"{r['precision']:.3f} / {r['recall']:.3f} / {r['fpr']:.3f} @ {r['tau']:.3f}" if "tau" in r else "—",
             f"{r['delta_vs_chosen']:+.3f} [{r['delta_lo']:+.3f}, {r['delta_hi']:+.3f}]" if "delta_vs_chosen" in r else "(chốt)"]
    return "| " + " | ".join(cells) + " |"


def main(open_flag: bool, again: bool, datasets: tuple[str, ...] = HEAD_A + HEAD_B) -> None:
    history = guard(open_flag, again)
    cfg = load_config()
    history.append({"opened_at": dt.datetime.now().isoformat(timespec="seconds"), "git_commit": git_commit(),
                    "datasets": list(datasets)})
    MARKER.write_text(json.dumps(history, indent=2) + "\n", encoding="utf-8")  # ghi trước khi đọc test
    rows = []
    for d in datasets:
        r, scores = (head_a if d in HEAD_A else head_b)(d, cfg)
        scores.to_parquet(ROOT / "reports" / f"test_scores_{d}.parquet", index=False)
        rows += r
        head = "headA" if d in HEAD_A else "headB"
        dh = data_hash(PROC / d / f"labels_{head}_train.parquet", LOCKED / d / f"labels_{head}_test.parquet")
        for x in r:
            append_ledger("m3_test", "A", cfg, dh, [cfg["test_seed"]], f"{d}:{x['model']}", "pr_auc_test",
                          [x["pr_auc"]], notes=f"CI95=[{x['pr_auc_lo']:.4f}, {x['pr_auc_hi']:.4f}] cluster bootstrap"
                          + (f" ; Δ vs chốt {x['delta_vs_chosen']:+.4f} [{x['delta_lo']:+.4f}, {x['delta_hi']:+.4f}]"
                             if "delta_vs_chosen" in x else " ; mô hình chốt"))
        print(pd.DataFrame(r).round(4).to_string(), flush=True)
    command = "python -m src.m3_health.run_test --open" + (
        " --datasets " + " ".join(datasets) if datasets != HEAD_A + HEAD_B else ""
    )
    lines = ["# Kết quả test (A8) — mở một lần", "",
             f"> Sinh bởi `{command}` lúc {history[-1]['opened_at']}, commit "
             f"`{history[-1]['git_commit']}`. Mô hình fit chỉ trên train (C2 fit toàn bộ H1 train), τ chọn trên train. "
             f"CI 95% cluster bootstrap theo group, B = {cfg['bootstrap']['B']} ; Δ = PR-AUC(chốt) − PR-AUC(mô hình), "
             "paired trên cùng lần lấy mẫu.", "",
             "| Bộ | Mô hình | PR-AUC [CI 95%] | ROC-AUC | Brier | Precision / Recall / FPR @ τ | Δ chốt − mô hình [CI] |",
             "|---|---|---|---|---|---|---|", *map(_fmt, rows), ""]
    name = "TEST_RESULTS.md" if datasets == HEAD_A + HEAD_B else "TEST_RESULTS_" + "_".join(datasets) + ".md"
    (ROOT / "docs" / "results" / name).write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--open", action="store_true", dest="open_flag")
    p.add_argument("--again", action="store_true")
    p.add_argument("--datasets", nargs="+", choices=HEAD_A + HEAD_B,
                   help="Chỉ mở các bộ này (ví dụ --datasets H1 H2 cho C2 trên CPU).")
    a = p.parse_args()
    main(a.open_flag, a.again, tuple(a.datasets) if a.datasets else HEAD_A + HEAD_B)
