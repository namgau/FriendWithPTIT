"""A9 / E5 — Head B: kiểm tra nhiễu nhãn + thử sửa lỗi buộc tội oan, so trước/sau trên CV (H1, H2).

Chạy:  python -m src.m3_health.run_head_b_e5

Cùng fold, calibration và τ_B (FPR ≤ 5% trên calibration) với run_head_b. **Mọi biến thể đánh giá trên nhãn gốc** ;
không sửa nhãn, không dùng test.
1. Kiểm tra nhiễu (tự động): văn bản trùng (chuẩn hóa) mà nhãn mâu thuẫn ; dòng chỉ có URL ; confident learning
   (Northcutt et al. 2021, JAIR) trên OOF của tfidf_lr: mẫu nhãn 0 có p ≥ t1 (= p trung bình của lớp 1) và
   mẫu nhãn 1 có p ≤ t0 (= p trung bình của lớp 0) → ước lượng tỉ lệ nghi nhiễu.
2. Biến thể (cộng dồn): base → + URL thành token → + đặc trưng chửi có đối tượng / chửi cảm thán →
   + lọc mẫu nghi nhiễu khỏi phần fit (confident learning trên OOF 3-fold bên trong phần fit).
   Báo PR-AUC, FPR / recall tổng và FPR trên slice có từ chửi, có teencode ; chênh lệch theo cặp fold so với base.
3. Độ nhạy: metric của base khi bỏ các group có văn bản trùng mâu thuẫn nhãn.
Kết quả: docs/results/HEAD_B_E5.md, ledger. Mẫu 100 FP để đọc tay → reports/h1_fp_audit.csv (KHÔNG commit).
"""
from __future__ import annotations

import re
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score

from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import threshold_by_fpr
from src.m3_health.features_b import NUMERIC_COLUMNS
from src.m3_health.labels import PROC, ROOT, load_config
from src.m3_health.run_error_analysis import TEENCODE, _md_table
from src.m3_health.run_head_b import _fit_calibration, load, make_model

URL = re.compile(r"https?://\S+|www\.\S+", re.IGNORECASE)
SWEAR = re.compile(r"\b(?:đm|dm|đmm|dmm|đcm|dcm|vl|vcl|vkl|vãi lồn|cmn|cmm|đéo|éo|địt|đụ|đù|lồn|cặc|buồi|đĩ|"
                   r"ngu|óc chó|chó|khốn nạn|mất dạy|súc vật|súc sinh|thằng ngu|con ngu|cút|câm mồm|câm mõm)\b",
                   re.IGNORECASE)
TARGET = re.compile(r"\b(?:mày|m|mi|bay|tụi mày|chúng mày|bọn mày|thằng|con|mấy thằng|mấy con|bọn|lũ|ông|bà|"
                    r"ad|admin|mẹ mày|cha mày|bố mày|nó|chúng nó|tụi nó|mấy ông|mấy bà)\b", re.IGNORECASE)
WINDOW = 3  # từ chửi và từ chỉ đối tượng cách nhau ≤ 3 token → "có đối tượng"
SWEAR_COLUMNS = ["swear_directed", "swear_exclaim", "n_swear"]


def _norm(s: pd.Series) -> pd.Series:
    return s.fillna("").str.lower().str.replace(URL, " ", regex=True).str.replace(r"\W+", " ", regex=True).str.strip()


def swear_features(text: pd.Series) -> pd.DataFrame:
    def one(t: str) -> tuple[float, float, float]:
        toks = re.findall(r"\w+", t.lower())
        joined = " ".join(toks)
        sw = [m.start() for m in SWEAR.finditer(joined)]
        if not sw:
            return 0.0, 0.0, 0.0
        pos = np.cumsum([0] + [len(x) + 1 for x in toks])  # vị trí ký tự → chỉ số token
        tok_of = lambda c: int(np.searchsorted(pos, c, side="right") - 1)
        tg = [tok_of(m.start()) for m in TARGET.finditer(joined)]
        directed = any(abs(tok_of(s) - g) <= WINDOW for s in sw for g in tg)
        return float(directed), float(not directed), float(len(sw))
    return pd.DataFrame([one(t) for t in text.fillna("")], columns=SWEAR_COLUMNS, index=text.index)


def _suspect(y: np.ndarray, p: np.ndarray) -> np.ndarray:
    """Confident learning nhị phân: lệch khỏi nhãn một cách tự tin."""
    t1, t0 = p[y == 1].mean(), p[y == 0].mean()
    return ((y == 0) & (p >= t1)) | ((y == 1) & (p <= t0))


def _inner_oof(frame, y, groups, cfg, seed, cols) -> np.ndarray:
    p = np.zeros(len(y))
    for tr, va in group_kfold(groups, 3, seed=seed, y=y):
        p[va] = make_model(cfg, seed, True, cols).fit(frame.iloc[tr], y[tr]).predict_proba(frame.iloc[va])[:, 1]
    return p


VARIANTS = {  # tên: (thay URL, thêm đặc trưng chửi, lọc nghi nhiễu)
    "base": (False, False, False),
    "+ url token": (True, False, False),
    "+ url + chửi có/không đối tượng": (True, True, False),
    "+ url + chửi + lọc nghi nhiễu": (True, True, True),
}


def run(d: str, cfg: dict):
    raw = load(d)
    y, groups = raw["y"].to_numpy(int), raw["group_id"].to_numpy()
    hb, seed = cfg["head_b"], cfg["test_seed"]
    url_frame = raw.assign(text=raw["text"].fillna("").str.replace(URL, " urltoken ", regex=True))
    url_frame = pd.concat([url_frame, swear_features(raw["text"])], axis=1)
    swear = url_frame["n_swear"].to_numpy() > 0
    teen = raw["text"].fillna("").str.contains(TEENCODE).to_numpy()
    rows, oof_base, flags = [], np.zeros(len(y)), np.zeros(len(y), bool)
    for fold, (tr, va) in enumerate(group_kfold(groups, hb["cv_folds"], seed=seed, y=y)):
        fit_i, cal_i = held_out_group_split(groups[tr], hb["calibration_fraction"], seed=seed + fold, y=y[tr])
        fit, cal = tr[fit_i], tr[cal_i]
        assert_no_group_leak(groups, fit, va)
        assert_no_group_leak(groups, cal, va)
        for name, (use_url, use_swear, denoise) in VARIANTS.items():
            frame = url_frame if use_url else raw
            cols = NUMERIC_COLUMNS + (SWEAR_COLUMNS if use_swear else [])
            fit_use, removed = fit, 0
            if denoise:
                bad = _suspect(y[fit], _inner_oof(frame.iloc[fit], y[fit], groups[fit], cfg, seed + fold, cols))
                fit_use, removed = fit[~bad], int(bad.sum())
            model = make_model(cfg, seed + fold, True, cols).fit(frame.iloc[fit_use], y[fit_use])
            raw_cal = model.predict_proba(frame.iloc[cal])[:, 1]
            calib = _fit_calibration(y[cal], raw_cal)
            tau = threshold_by_fpr(y[cal], calib(raw_cal), hb["threshold_fpr"])["threshold"]
            p = calib(model.predict_proba(frame.iloc[va])[:, 1])
            f = p >= tau
            yv, sv, tv = y[va], swear[va], teen[va]
            rate = lambda m: f[m].mean() if m.any() else np.nan
            rows.append({"dataset": d, "fold": fold, "variant": name, "removed_from_fit": removed,
                         "pr_auc": average_precision_score(yv, p), "fpr": rate(yv == 0), "recall": rate(yv == 1),
                         "fpr_swear": rate((yv == 0) & sv), "recall_swear": rate((yv == 1) & sv),
                         "fpr_teen": rate((yv == 0) & tv), "fpr_noswear": rate((yv == 0) & ~sv)})
            if name == "base":
                oof_base[va], flags[va] = p, f
            print(d, fold, name, f"pr_auc={rows[-1]['pr_auc']:.4f} fpr_swear={rows[-1]['fpr_swear']:.3f}", flush=True)
    return raw, y, groups, oof_base, flags, swear, pd.DataFrame(rows)


def audit(raw, y, groups, p, flags) -> tuple[dict, pd.DataFrame]:
    norm = _norm(raw["text"])
    lab_per_text = pd.DataFrame({"t": norm, "y": y}).groupby("t")["y"].agg(["nunique", "size"])
    conflict_texts = lab_per_text[(lab_per_text["nunique"] > 1) & (lab_per_text.index != "")].index
    conflict = norm.isin(conflict_texts).to_numpy()
    url_only = (norm == "").to_numpy() & raw["text"].fillna("").str.contains(URL).to_numpy()
    sus = _suspect(y, p)
    info = {"n": len(y), "dup_conflict_rows": int(conflict.sum()), "dup_conflict_texts": len(conflict_texts),
            "url_only_rows": int(url_only.sum()), "suspect_rows": int(sus.sum()), "suspect_share": float(sus.mean()),
            "suspect_label0_share": float(sus[y == 0].mean()), "suspect_label1_share": float(sus[y == 1].mean()),
            "fp_suspect_share": float(sus[(y == 0) & flags].mean())}
    keep = ~np.isin(groups, groups[conflict])
    info["base_pr_auc_all"] = float(average_precision_score(y, p))
    info["base_pr_auc_no_conflict"] = float(average_precision_score(y[keep], p[keep]))
    info["base_fpr_all"] = float(flags[y == 0].mean())
    info["base_fpr_no_conflict"] = float(flags[keep & (y == 0)].mean())
    fp = np.where((y == 0) & flags)[0]
    pick = np.random.default_rng(42).choice(fp, min(100, len(fp)), replace=False)
    sample = pd.DataFrame({"row": pick, "p": p[pick].round(3), "text": raw["text"].iloc[pick].str.slice(0, 300).to_numpy(),
                           "category": ""})
    return info, sample


def write_report(res: pd.DataFrame, infos: dict) -> None:
    lines = ["# Head B — E5: nhiễu nhãn và sửa lỗi buộc tội oan (H1, H2)", "",
             "> Sinh bởi `python -m src.m3_health.run_head_b_e5`. Chỉ tập train, cùng fold / calibration / τ_B "
             "(FPR ≤ 5%) với `HEAD_B_CV.md`. **Mọi biến thể đánh giá trên nhãn gốc.** Mean ± SD qua 5 fold ; "
             "Δ = chênh lệch theo cặp fold so với base.", "",
             "## 1. Kiểm tra nhiễu nhãn (tự động, trên OOF của base)", "",
             "| | " + " | ".join(infos) + " |", "|---|" + "---|" * len(infos)]
    labels = {"n": "số mẫu", "dup_conflict_texts": "văn bản trùng có nhãn mâu thuẫn",
              "dup_conflict_rows": "số dòng thuộc các văn bản đó", "url_only_rows": "dòng chỉ có URL",
              "suspect_share": "tỉ lệ nghi nhiễu (confident learning)", "suspect_label0_share": "… trong nhãn 0 (sạch)",
              "suspect_label1_share": "… trong nhãn 1 (độc hại)", "fp_suspect_share": "tỉ lệ FP thuộc diện nghi nhiễu",
              "base_pr_auc_all": "PR-AUC base", "base_pr_auc_no_conflict": "PR-AUC base, bỏ group trùng mâu thuẫn",
              "base_fpr_all": "FPR base", "base_fpr_no_conflict": "FPR base, bỏ group trùng mâu thuẫn"}
    for k, name in labels.items():
        vals = [infos[d][k] for d in infos]
        lines.append(f"| {name} | " + " | ".join(f"{v:,}" if isinstance(v, int) else f"{v:.3f}" for v in vals) + " |")
    lines += ["", "Confident learning chỉ cho biết mẫu **nghi** nhiễu (mô hình rất tự tin ngược nhãn) — gồm cả nhãn "
              "sai thật lẫn lỗi thật của mô hình ; tỉ lệ nhãn sai thật xem mục đọc tay.", "",
              "## 2. Biến thể (cộng dồn)", ""]
    metrics = [("pr_auc", "PR-AUC"), ("fpr", "FPR"), ("recall", "Recall"), ("fpr_swear", "FPR có từ chửi"),
               ("recall_swear", "Recall có từ chửi"), ("fpr_noswear", "FPR không từ chửi"), ("fpr_teen", "FPR teencode")]
    for d, g in res.groupby("dataset", sort=False):
        base = g[g.variant == "base"].set_index("fold")
        lines += [f"### {d}", "", "| Biến thể | Bỏ khỏi fit | " + " | ".join(h for _, h in metrics) + " |",
                  "|---|---|" + "---|" * len(metrics)]
        for v, gv in g.groupby("variant", sort=False):
            gv = gv.set_index("fold")
            cells = []
            for c, _ in metrics:
                cell = mean_sd(gv[c])
                if v != "base":
                    cell += f" (Δ {(gv[c] - base[c]).mean():+.3f})"
                cells.append(cell)
            lines.append(f"| {v} | {int(gv.removed_from_fit.mean()):,} | " + " | ".join(cells) + " |")
        lines.append("")
    lines += ["Đặc trưng chửi: `swear_directed` = từ chửi cách từ chỉ đối tượng (mày, thằng, con, bọn, ad…) ≤ "
              f"{WINDOW} token ; `swear_exclaim` = có từ chửi nhưng không có đối tượng gần (đm, vl dùng như từ cảm thán).", ""]
    out = ROOT / "docs" / "results" / "HEAD_B_E5.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    cfg = load_config()
    res, infos = [], {}
    for d in datasets:
        raw, y, groups, p, flags, swear, r = run(d, cfg)
        infos[d], sample = audit(raw, y, groups, p, flags)
        if d == "H1":
            sample.to_csv(ROOT / "reports" / "h1_fp_audit.csv", index=False, encoding="utf-8")
        res.append(r)
        dh = data_hash(PROC / d / "labels_headB_train.parquet", PROC / d / "messages.parquet")
        for v, g in r.groupby("variant", sort=False):
            append_ledger("m3_headB", "A", cfg, dh, [cfg["test_seed"]], f"{d}:e5:{v}", "fpr_swear", g.fpr_swear,
                          notes=f"E5; pr_auc={g.pr_auc.mean():.4f}, fpr={g.fpr.mean():.4f}, recall={g.recall.mean():.4f}")
    write_report(pd.concat(res, ignore_index=True), infos)


if __name__ == "__main__":
    main(sys.argv[1:] or ["H1", "H2"])
