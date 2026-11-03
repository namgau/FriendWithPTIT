"""A9 — phân tích lỗi cả 2 head (docs/06 §5b) trên dự đoán out-of-fold của tập TRAIN.

Chạy:  python -m src.m3_health.run_error_analysis

Chưa dùng test (khóa tới 20/11) ; sau khi mở test chạy lại phần tương ứng (A8). Cùng fold với run_head_a /
run_head_b. Bảng tổng hợp (không có nội dung tin nhắn) → docs/results/ERROR_ANALYSIS.md ; ví dụ kèm văn bản
để đọc tay → reports/error_examples_<ID>.md (gitignored, KHÔNG commit).
- Head A (E1, E2): LightGBM + none, calibration isotonic như A5 ; E1 confusion tại τ2 ; lead time ;
  E2 slice ; E3 ca sai tự tin nhất + đóng góp SHAP (LightGBM pred_contrib) ; E4 learning curve.
- Head B (G1, H1, H2): tfidf_lr như run_head_b, ngưỡng τ_B theo FPR của từng fold ; FPR/recall theo slice
  (teencode, không dấu, có từ trong lexicon, độ dài) + CI 95% cluster bootstrap theo group.
"""
from __future__ import annotations

import re
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score

from src.eval.bootstrap import cluster_bootstrap
from src.eval.calibration import fit_calibrator
from src.eval.report import append_ledger, data_hash
from src.eval.splits import (assert_no_group_leak, assert_time_order, forward_chaining, group_kfold,
                             held_out_group_split, held_out_time_split)
from src.eval.threshold import choose_tau1_tau2, confusion_at, threshold_by_fpr
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_head_a import MODELS, load as load_a
from src.m3_health.run_head_b import _fit_calibration, load as load_b, make_model

H = 3600.0
TEENCODE = re.compile(r"\b(?:ko|k0|kh[oô]ng?k|khum|hok|j|dc|đc|vs|ns|bik|bít|z|zậy|zị|oke|cx|mn|mk|mik|tr[oờ]i ơi|"
                      r"vcl|vl|đm|dm|cmn|ae|ny|ck|vk|trc|thik|iu|hum|nha|nhe|ạk|uk|ukm)\b", re.IGNORECASE)
VI_DIACRITIC = re.compile(r"[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", re.IGNORECASE)


def _md_table(df: pd.DataFrame, digits: int = 3) -> list[str]:
    fmt = lambda v: f"{v:.{digits}f}" if isinstance(v, (float, np.floating)) else (f"{v:,}" if isinstance(
        v, (int, np.integer)) else str(v))
    return ["| " + " | ".join(df.columns) + " |", "|" + "---|" * len(df.columns),
            *("| " + " | ".join(fmt(v) for v in r) + " |" for r in df.itertuples(index=False))]


# ------------------------------------------------------------------ Head A

def oof_head_a(d: str, cfg: dict):
    lab, X = load_a(d, cfg)
    y, groups, start = lab["y"].to_numpy(int), lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    seed = cfg["test_seed"]
    p = np.full(len(y), np.nan)
    contrib = np.zeros((len(y), X.shape[1] + 1))
    folds = list(forward_chaining(groups, start, cfg["cv"]["outer_k"]))
    for tr, va in folds:
        assert_no_group_leak(groups, tr, va)
        assert_time_order(groups, start, tr, va)
        fit_i, cal_i = held_out_time_split(groups[tr], start[tr], 0.2)
        fit, cal = tr[fit_i], tr[cal_i]
        m = MODELS["lgbm"](seed).fit(X.iloc[fit], y[fit])
        p[va] = fit_calibrator("isotonic", m.predict_proba(X.iloc[cal])[:, 1], y[cal])(m.predict_proba(X.iloc[va])[:, 1])
        contrib[va] = m.predict(X.iloc[va], pred_contrib=True)
    ev = ~np.isnan(p)
    return lab[ev].reset_index(drop=True), X[ev].reset_index(drop=True), y[ev], p[ev], contrib[ev], folds


def lead_time(lab: pd.DataFrame, X: pd.DataFrame, y, flag) -> dict:
    """Với mỗi ca tắt được báo: lùi về các mẫu trước trong cùng hội thoại còn liên tục được báo ;
    lead = tuổi hội thoại tại ca tắt − tại mẫu đầu chuỗi (giờ). 0 = chỉ báo đúng ở tin cuối."""
    df = pd.DataFrame({"c": lab["conversation_id"], "t": lab["turn_idx"], "age": np.expm1(X["conv_age_log"]),
                       "y": y, "f": flag}).sort_values(["c", "t"]).reset_index(drop=True)
    run_start = df["age"].where(df["f"] & ~(df["f"].shift(fill_value=False) & (df["c"] == df["c"].shift())))
    df["start"] = run_start.groupby(df["c"]).ffill()
    tp = df[(df.y == 1) & df.f]
    lead = (tp["age"] - tp["start"]) / H
    return {"n_tp": len(tp), "lead_0": float((lead == 0).mean()) if len(tp) else np.nan,
            "lead_median_h_if_pos": float(lead[lead > 0].median()) if (lead > 0).any() else 0.0,
            "lead_p90_h": float(lead.quantile(0.9)) if len(tp) else np.nan}


def slices_a(X: pd.DataFrame, y, p, flag) -> pd.DataFrame:
    s = {"giờ": pd.cut(X["hour"], [-1, 5, 11, 17, 23], labels=["0–5h", "6–11h", "12–17h", "18–23h"]),
         "cuối tuần": X["weekend"].map({0.0: "ngày thường", 1.0: "cuối tuần"}),
         "số tin": pd.cut(X["n_msgs"], [0, 10, 20, 50, np.inf], labels=["6–10", "11–20", "21–50", ">50"]),
         "tuổi hội thoại": pd.cut(np.expm1(X["conv_age_log"]) / H, [-1, 1, 24, np.inf],
                                  labels=["< 1h", "1–24h", "> 24h"])}
    rows = []
    for name, key in s.items():
        for v in key.cat.categories if hasattr(key, "cat") else sorted(key.unique()):
            m = (key == v).to_numpy()
            if m.sum() < 50 or y[m].min() == y[m].max():
                continue
            rows.append({"slice": name, "giá trị": str(v), "n": int(m.sum()), "prevalence": y[m].mean(),
                         "PR-AUC": average_precision_score(y[m], p[m]), "lift": average_precision_score(y[m], p[m]) / y[m].mean(),
                         "recall@τ2": flag[m & (y == 1)].mean(), "FPR@τ2": flag[m & (y == 0)].mean()})
    return pd.DataFrame(rows)


def learning_curve(d: str, cfg: dict, fracs=(0.1, 0.25, 0.5, 1.0)) -> pd.DataFrame:
    """Fold cuối (outer-train lớn nhất) ; lấy ngẫu nhiên một phần group của outer-train."""
    lab, X = load_a(d, cfg)
    y, groups, start = lab["y"].to_numpy(int), lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    tr, va = list(forward_chaining(groups, start, cfg["cv"]["outer_k"]))[-1]
    rng = np.random.default_rng(cfg["test_seed"])
    ug = np.unique(groups[tr])
    rows = []
    for f in fracs:
        keep = np.isin(groups[tr], rng.choice(ug, max(1, int(f * len(ug))), replace=False))
        m = MODELS["lgbm"](cfg["test_seed"]).fit(X.iloc[tr[keep]], y[tr[keep]])
        pv = m.predict_proba(X.iloc[va])[:, 1]
        pt = m.predict_proba(X.iloc[tr[keep]])[:, 1]
        rows.append({"dataset": d, "tỉ lệ train": f, "n_train": int(keep.sum()),
                     "PR-AUC train": average_precision_score(y[tr[keep]], pt), "PR-AUC val": average_precision_score(y[va], pv)})
    return pd.DataFrame(rows)


def head_a(d: str, cfg: dict, examples: list[str]) -> tuple[list[str], dict]:
    th = cfg["head_a"]["threshold"]
    lab, X, y, p, contrib, _ = oof_head_a(d, cfg)
    tau1, tau2 = choose_tau1_tau2(y, p, th["c_fn"], th["c_fp"], th["shallow_recall"])
    flag2, flag1 = p >= tau2, p >= tau1
    at2 = confusion_at(y, p, tau2)
    tn, fp, fn, tp = (int(((y == a) & (flag2 == b)).sum()) for a, b in ((0, 0), (0, 1), (1, 0), (1, 1)))
    lt1, lt2 = lead_time(lab, X, y, flag1), lead_time(lab, X, y, flag2)
    lines = [f"## Head A — {d}", "", f"OOF: {len(y):,} mẫu, prevalence {y.mean():.3f} ; LightGBM + none, isotonic. "
             f"τ1 = {tau1:.3f}, τ2 = {tau2:.3f} (chọn trên chính OOF này, C_FN : C_FP = {th['c_fn']}:{th['c_fp']}).", "",
             "**E1 — confusion tại τ2**", "", "| | Báo (p ≥ τ2) | Không báo |", "|---|---|---|",
             f"| Tắt thật | TP {tp:,} | FN {fn:,} |", f"| Không tắt | FP {fp:,} | TN {tn:,} |", "",
             f"Precision {at2['precision']:.3f}, recall {at2['recall']:.3f}, FPR {at2['fpr']:.3f}. "
             f"Ở mức hời hợt (τ1): recall {flag1[y == 1].mean():.3f}, FPR {flag1[y == 0].mean():.3f}.", "",
             "**Lead time** (ca tắt được báo ; lùi theo chuỗi mẫu liên tục được báo trong cùng hội thoại):", "",
             "| Mức | Số ca TP | Tỉ lệ lead = 0 (chỉ báo ở tin cuối) | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |",
             "|---|---|---|---|---|"]
    for name, lt in (("τ1 hời hợt", lt1), ("τ2 nguy cơ", lt2)):
        lines.append(f"| {name} | {lt['n_tp']:,} | {lt['lead_0']:.3f} | {lt['lead_median_h_if_pos']:.2f} | {lt['lead_p90_h']:.2f} |")
    lines += ["", "**E2 — slice** (recall/FPR tại τ2 ; lift = PR-AUC / prevalence của slice):", "",
              *_md_table(slices_a(X, y, p, flag2)), ""]
    feats = list(X.columns)
    imp = pd.Series(np.abs(contrib[:, :-1]).mean(0), feats).sort_values(ascending=False)
    lines += ["**E3 — đóng góp SHAP trung bình |log-odds|** (top 8): " +
              ", ".join(f"{k} {v:.3f}" for k, v in imp.head(8).items()) + ".", ""]
    err = pd.DataFrame({"y": y, "p": p, "i": np.arange(len(y))})
    worst_fp = err[err.y == 0].nlargest(10, "p")
    worst_fn = err[err.y == 1].nsmallest(10, "p")
    rows = []
    for kind, part in (("FP", worst_fp), ("FN", worst_fn)):
        for r in part.itertuples():
            c = pd.Series(contrib[r.i, :-1], feats)
            top = c.reindex(c.abs().sort_values(ascending=False).index[:3])
            rows.append({"loại": kind, "p": r.p, "im lặng sau đó (giờ)": lab.at[r.i, "duration_s"] / H,
                         "số tin": int(X.at[r.i, "n_msgs"]), "gap_last (phút)": np.expm1(X.at[r.i, "gap_last"]) / 60,
                         "top SHAP": ", ".join(f"{k} {v:+.2f}" for k, v in top.items())})
    lines += ["**E3 — 10 FP + 10 FN tự tin nhất** (không kèm văn bản ; văn bản trong `reports/`, không commit):", "",
              *_md_table(pd.DataFrame(rows), 2), ""]
    msgs = pd.read_parquet(PROC / d / "messages.parquet", columns=["conversation_id", "turn_idx", "sender_id", "text"])
    msgs = msgs[msgs.conversation_id.isin(lab.conversation_id.iloc[list(worst_fp.i) + list(worst_fn.i)])]
    examples += [f"# {d} — ca sai tự tin (KHÔNG commit)", ""]
    for kind, part in (("FP", worst_fp), ("FN", worst_fn)):
        for r in part.itertuples():
            c, t = lab.at[r.i, "conversation_id"], lab.at[r.i, "turn_idx"]
            ctx = msgs[(msgs.conversation_id == c) & msgs.turn_idx.between(t - 3, t)].sort_values("turn_idx")
            examples += [f"## {kind} p={r.p:.2f} im {lab.at[r.i, 'duration_s'] / H:.1f}h", "",
                         *(f"- [{m.sender_id}] {str(m.text)[:200]}" for m in ctx.itertuples()), ""]
    return lines, {"tau2": tau2, "precision": at2["precision"], "recall": at2["recall"], "lead0_tau1": lt1["lead_0"]}


# ------------------------------------------------------------------ Head B

def oof_head_b(d: str, cfg: dict):
    frame = load_b(d)
    y, groups = frame["y"].to_numpy(int), frame["group_id"].to_numpy()
    hb, seed = cfg["head_b"], cfg["test_seed"]
    p, flag = np.full(len(y), np.nan), np.zeros(len(y), bool)
    for fold, (tr, va) in enumerate(group_kfold(groups, hb["cv_folds"], seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(groups[tr], hb["calibration_fraction"], seed=seed + fold, y=y[tr])
        fit, cal = tr[fit_i], tr[cal_i]
        assert_no_group_leak(groups, fit, va)
        model = make_model(cfg, seed + fold).fit(frame.iloc[fit], y[fit])
        calib = _fit_calibration(y[cal], model.predict_proba(frame.iloc[cal])[:, 1])
        p_cal = calib(model.predict_proba(frame.iloc[cal])[:, 1])
        tau = threshold_by_fpr(y[cal], p_cal, hb["threshold_fpr"])["threshold"]
        p[va] = calib(model.predict_proba(frame.iloc[va])[:, 1])
        flag[va] = p[va] >= tau
    return frame, y, p, flag, groups


def _rate_ci(mask_pos, flag, groups, cfg, B=1000) -> str:
    """Tỉ lệ flag trong mask (FPR nếu mask = y==0 & slice, recall nếu y==1 & slice) + CI cluster bootstrap."""
    if mask_pos.sum() < 20:
        return "—"
    r = cluster_bootstrap(lambda yy, ff, sample_weight=None: np.average(ff, weights=sample_weight),
                          np.zeros(mask_pos.sum()), flag[mask_pos].astype(float), groups[mask_pos], B=B,
                          ci=cfg["bootstrap"]["ci"], seed=cfg["test_seed"], binary=False)
    return f"{r['estimate']:.3f} [{r['lo']:.3f}, {r['hi']:.3f}]"


def head_b(d: str, cfg: dict, examples: list[str]) -> list[str]:
    frame, y, p, flag, groups = oof_head_b(d, cfg)
    text = frame["text"].fillna("").astype(str)
    last = frame["last_text"].fillna("").astype(str) if "last_text" in frame else text
    sl = {"có từ trong lexicon độc hại": frame["toxic_hits"].to_numpy() > 0,
          "dài (> trung vị số từ)": frame["n_words"] > frame["n_words"].median()}
    if d != "G1":  # slice tiếng Việt, không áp cho G1 (tiếng Anh)
        sl["có teencode / viết tắt"] = text.str.contains(TEENCODE)
        sl["không dấu tiếng Việt"] = ~text.str.contains(VI_DIACRITIC)
    rows = [{"slice": "toàn bộ", "giá trị": "", "n lành": int((y == 0).sum()), "FPR (buộc tội oan)": _rate_ci(y == 0, flag, groups, cfg),
             "n độc hại": int((y == 1).sum()), "recall": _rate_ci(y == 1, flag, groups, cfg)}]
    for name, m in sl.items():
        m = np.asarray(m, bool)
        for v, mm in (("có", m), ("không", ~m)):
            rows.append({"slice": name, "giá trị": v, "n lành": int((mm & (y == 0)).sum()),
                         "FPR (buộc tội oan)": _rate_ci(mm & (y == 0), flag, groups, cfg),
                         "n độc hại": int((mm & (y == 1)).sum()), "recall": _rate_ci(mm & (y == 1), flag, groups, cfg)})
    fp = np.where((y == 0) & flag)[0]
    fn = np.where((y == 1) & ~flag)[0]
    lines = [f"## Head B — {d}", "", f"OOF tfidf_lr: {len(y):,} mẫu, prevalence {y.mean():.3f} ; τ_B theo FPR ≤ "
             f"{cfg['head_b']['threshold_fpr']:.0%} trên calibration từng fold. FP = {len(fp):,}, FN = {len(fn):,}. "
             "CI 95% cluster bootstrap theo group (B = 1000).", "", *_md_table(pd.DataFrame(rows)), ""]
    examples += [f"# {d} — FP (buộc tội oan) và FN tự tin nhất (KHÔNG commit)", ""]
    for kind, idx, order in (("FP", fp, -1), ("FN", fn, 1)):
        for i in idx[np.argsort(order * p[idx])][:15]:
            examples.append(f"- {kind} p={p[i]:.2f}: {last.iat[i][:300]}")
    examples.append("")
    return lines


def main() -> None:
    cfg = load_config()
    examples: list[str] = []
    lines = ["# Phân tích lỗi (A9) — out-of-fold trên tập train", "",
             "> Sinh bởi `python -m src.m3_health.run_error_analysis`. Test **chưa mở** ; sẽ chạy lại trên test sau "
             "20/11 (A8). Bảng không chứa nội dung tin nhắn ; ví dụ có văn bản để đọc tay nằm ở "
             "`reports/error_examples_*.md` (gitignored). Kiểu lỗi định tính (đã xong việc, chuyển kênh, ngủ, "
             "đùa thân mật…) cần A đọc tay các ví dụ đó rồi mới ghi vào báo cáo.", ""]
    summ = {}
    for d in ("E1", "E2"):
        part, summ[d] = head_a(d, cfg, examples)
        lines += part
    lc = pd.concat([learning_curve(d, cfg) for d in ("E1", "E2")], ignore_index=True)
    lines += ["## Head A — E4 learning curve (fold cuối, lấy ngẫu nhiên một phần group của outer-train)", "",
              *_md_table(lc), "", "Khoảng cách train − val lớn và val còn tăng theo n → variance ; val phẳng → thiếu "
              "đặc trưng / nhiễu nhãn (bias).", ""]
    for d in ("G1", "H1", "H2"):
        lines += head_b(d, cfg, examples)
    (ROOT / "docs" / "results" / "ERROR_ANALYSIS.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    (ROOT / "reports" / "error_examples.md").write_text("\n".join(examples) + "\n", encoding="utf-8")
    for d, s in summ.items():
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:lgbm:none@tau2", "recall_at_tau2",
                      [s["recall"]], notes=f"A9 OOF; tau2={s['tau2']:.3f}, precision={s['precision']:.3f}, "
                      f"lead0@tau1={s['lead0_tau1']:.3f}")
    print("wrote docs/results/ERROR_ANALYSIS.md, reports/error_examples.md")


if __name__ == "__main__":
    main()
