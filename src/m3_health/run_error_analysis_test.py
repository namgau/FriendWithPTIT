"""A9 trên TEST — phân tích lỗi mô hình chốt bằng điểm test `run_test` đã lưu (không fit lại, không chọn lại τ).

Chạy (sau A8):  python -m src.m3_health.run_error_analysis_test

Đọc reports/test_scores_<ID>.parquet + τ mô hình chốt ở experiments/test_taus.json (giá trị run_test in ra,
làm tròn 4 chữ số). Bảng gộp (không có nội dung tin) → docs/results/ERROR_ANALYSIS_TEST.md ; ví dụ kèm văn bản
→ reports/error_examples_test.md (gitignored, KHÔNG commit).
"""
from __future__ import annotations

import json

import numpy as np
import pandas as pd

from src.m3_health.features import extract_features
from src.m3_health.features_b import build_g1, build_single
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_error_analysis import H, TEENCODE, VI_DIACRITIC, _md_table, _rate_ci, lead_time, slices_a
from src.m3_health.run_test import load_test

TAUS = ROOT / "experiments" / "test_taus.json"
CHOSEN = {"E1": "lgbm_T1-T4", "E2": "lgbm_T1-T4", "G1": "tfidf_lr", "H1": "phobert_c2", "H2": "tfidf_lr"}


def _scores(d: str, groups) -> pd.DataFrame:
    s = pd.read_parquet(ROOT / "reports" / f"test_scores_{d}.parquet")
    if len(s) != len(groups) or not (s["group_id"].to_numpy() == np.asarray(groups)).all():
        raise RuntimeError(f"{d}: thứ tự test_scores không khớp tập test dựng lại")
    return s


def head_a(d: str, tau: dict, examples: list[str]) -> list[str]:
    test = load_test(d)
    test = test[test["y"].notna()].reset_index(drop=True)
    msgs = pd.read_parquet(PROC / d / "messages.parquet")
    X = extract_features(msgs, test)
    s = _scores(d, test["group_id"])
    y, p = s["y"].to_numpy(int), s[CHOSEN[d]].to_numpy(float)
    flag1, flag2 = p >= tau["tau1"], p >= tau["tau2"]
    tn, fp, fn, tp = (int(((y == a) & (flag2 == b)).sum()) for a, b in ((0, 0), (0, 1), (1, 0), (1, 1)))
    lines = [f"## Head A — {d} (test, {CHOSEN[d]})", "",
             f"{len(y):,} mẫu, prevalence {y.mean():.3f} ; τ1 = {tau['tau1']:.4f}, τ2 = {tau['tau2']:.4f} chọn trên OOF train.", "",
             "| | Báo (p ≥ τ2) | Không báo |", "|---|---|---|",
             f"| Tắt thật | TP {tp:,} | FN {fn:,} |", f"| Không tắt | FP {fp:,} | TN {tn:,} |", "",
             f"Precision {tp / max(tp + fp, 1):.3f}, recall {tp / max(tp + fn, 1):.3f}, FPR {fp / max(fp + tn, 1):.3f}. "
             f"Mức hời hợt (τ1): recall {flag1[y == 1].mean():.3f}, FPR {flag1[y == 0].mean():.3f}.", "",
             "| Mức | Số ca TP | Tỉ lệ lead = 0 | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |", "|---|---|---|---|---|"]
    for name, f in (("τ1 hời hợt", flag1), ("τ2 nguy cơ", flag2)):
        lt = lead_time(test, X, y, f)
        lines.append(f"| {name} | {lt['n_tp']:,} | {lt['lead_0']:.3f} | {lt['lead_median_h_if_pos']:.2f} | {lt['lead_p90_h']:.2f} |")
    lines += ["", "Slice (recall/FPR tại τ2 ; lift = PR-AUC / prevalence):", "", *_md_table(slices_a(X, y, p, flag2)), ""]
    err = pd.DataFrame({"y": y, "p": p})
    pick = [("FP", err[err.y == 0].nlargest(10, "p")), ("FN", err[err.y == 1].nsmallest(10, "p"))]
    keep = test.loc[[i for _, part in pick for i in part.index], "conversation_id"]
    msgs = msgs[msgs.conversation_id.isin(keep)]
    examples += [f"# {d} test — ca sai tự tin (KHÔNG commit)", ""]
    for kind, part in pick:
        for i, r in part.iterrows():
            c, t = test.at[i, "conversation_id"], test.at[i, "turn_idx"]
            ctx = msgs[(msgs.conversation_id == c) & msgs.turn_idx.between(t - 3, t)].sort_values("turn_idx")
            examples += [f"## {kind} p={r.p:.2f} im {test.at[i, 'duration_s'] / H:.1f}h", "",
                         *(f"- [{m.sender_id}] {str(m.text)[:200]}" for m in ctx.itertuples()), ""]
    return lines


def head_b(d: str, tau: dict, cfg: dict, examples: list[str]) -> list[str]:
    msgs = pd.read_parquet(PROC / d / "messages.parquet")
    test = load_test(d)
    te = (build_g1(msgs, test) if d == "G1" else build_single(msgs, test)).reset_index(drop=True)
    s = _scores(d, te["group_id"])
    y, p, groups = s["y"].to_numpy(int), s[CHOSEN[d]].to_numpy(float), te["group_id"].to_numpy()
    flag = p >= tau["tau"]
    text = te["text"].fillna("").astype(str)
    last = te["last_text"].fillna("").astype(str) if "last_text" in te else text  # H1/H2: một bình luận
    sl = {"có từ trong lexicon độc hại": te["toxic_hits"].to_numpy() > 0,
          "dài (> trung vị số từ)": (te["n_words"] > te["n_words"].median()).to_numpy()}
    if d != "G1":
        sl["có teencode / viết tắt"] = text.str.contains(TEENCODE).to_numpy()
        sl["không dấu tiếng Việt"] = (~text.str.contains(VI_DIACRITIC)).to_numpy()
    rows = [{"slice": "toàn bộ", "giá trị": "", "n lành": int((y == 0).sum()), "FPR (buộc tội oan)": _rate_ci(y == 0, flag, groups, cfg),
             "n độc hại": int((y == 1).sum()), "recall": _rate_ci(y == 1, flag, groups, cfg)}]
    for name, m in sl.items():
        for v, mm in (("có", m), ("không", ~m)):
            rows.append({"slice": name, "giá trị": v, "n lành": int((mm & (y == 0)).sum()),
                         "FPR (buộc tội oan)": _rate_ci(mm & (y == 0), flag, groups, cfg),
                         "n độc hại": int((mm & (y == 1)).sum()), "recall": _rate_ci(mm & (y == 1), flag, groups, cfg)})
    fp, fn = np.where((y == 0) & flag)[0], np.where((y == 1) & ~flag)[0]
    examples += [f"# {d} test — FP và FN tự tin nhất (KHÔNG commit)", ""]
    for kind, idx, order in (("FP", fp, -1), ("FN", fn, 1)):
        examples += [f"- {kind} p={p[i]:.2f}: {last.iat[i][:300]}" for i in idx[np.argsort(order * p[idx])][:15]]
    examples.append("")
    return [f"## Head B — {d} (test, {CHOSEN[d]})", "",
            f"{len(y):,} mẫu, prevalence {y.mean():.3f} ; τ_B = {tau['tau']:.4f} chọn trên train/calibration. "
            f"FP = {len(fp):,}, FN = {len(fn):,}. CI 95% cluster bootstrap theo group (B = 1000).", "",
            *_md_table(pd.DataFrame(rows)), ""]


def main() -> None:
    cfg, taus = load_config(), json.loads(TAUS.read_text(encoding="utf-8"))
    examples: list[str] = []
    lines = ["# Phân tích lỗi (A9) — trên TEST", "",
             "> Sinh bởi `python -m src.m3_health.run_error_analysis_test` từ điểm test của `run_test` (A8). "
             "Không fit lại, không chọn lại ngưỡng trên test ; τ lấy từ `experiments/test_taus.json`. "
             "Bảng không chứa nội dung tin nhắn ; ví dụ có văn bản ở `reports/error_examples_test.md` (gitignored).", ""]
    for d in ("E1", "E2"):
        lines += head_a(d, taus[d], examples)
    for d in ("G1", "H1", "H2"):
        lines += head_b(d, taus[d], cfg, examples)
    (ROOT / "docs" / "results" / "ERROR_ANALYSIS_TEST.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    (ROOT / "reports" / "error_examples_test.md").write_text("\n".join(examples) + "\n", encoding="utf-8")
    print("wrote docs/results/ERROR_ANALYSIS_TEST.md, reports/error_examples_test.md")


if __name__ == "__main__":
    main()
