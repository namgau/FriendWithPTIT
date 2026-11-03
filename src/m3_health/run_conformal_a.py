"""A6 phần conformal (X8) — Head A: cận dưới / cận trên có bảo đảm cho thời gian tới tin kế tiếp.

Chạy:  python -m src.m3_health.run_conformal_a

Mục tiêu Y = log1p(T̃), T̃ = min(T, cap 7 ngày) (cap như run_survival). Split-conformal quantile regression
(CQR, Romano et al. 2019) một phía, mức 1 − α = 90%:
  - cận dưới  L(x): P(T̃ ≥ L) ≥ 90%  — "cặp này im ít nhất L" (dùng để KHÔNG nhắc quá sớm)
  - cận trên  U(x): P(T̃ ≤ U) ≥ 90%  — "trả lời trong U, hoặc im > 7 ngày" (hạn để nhắc)
Censoring thật (event = 0, T < cap, rất ít): chỉ biết T ≥ duration → với L dùng duration (bảo thủ, đúng
lập luận của Candès et al. 2023 / Davidov et al. 2025), với U cho điểm = +∞ (bảo thủ) ; khi đo coverage
trên validation, mẫu không xác định được tính là KHÔNG phủ.
Trong mỗi outer-train (forward chaining K=5, như run_head_a): fit hồi quy phân vị trên 80% group sớm,
calibration conformal trên 20% group muộn nhất. Chuyển miền (X8 + RQ2): mô hình + calibration của nguồn
đánh giá trên fold đích ; thêm weighted conformal (Tibshirani et al. 2019) với tỉ số mật độ từ bộ phân loại
miền học trên X **không nhãn** của đích ; và hiệu chỉnh lại trên 20% group muộn nhất của outer-train đích.
Kết quả: docs/results/HEAD_A_CONFORMAL.md, ledger.
"""
from __future__ import annotations

import sys

import numpy as np
import pandas as pd
from lightgbm import LGBMClassifier, LGBMRegressor

from src.eval.report import append_ledger, data_hash, mean_sd
from src.eval.splits import assert_no_group_leak, assert_time_order, forward_chaining, held_out_time_split
from src.m3_health.labels import PROC, ROOT, load_config
ALPHA = 0.1
H = 3600.0


def _target(lab: pd.DataFrame, cap_s: float) -> tuple[np.ndarray, np.ndarray]:
    """y = log1p(min(T, cap)) ; known = T̃ biết chính xác (có tin kế tiếp, hoặc đã im ≥ cap)."""
    t = lab["duration_s"].to_numpy(float)
    known = lab["event"].to_numpy(bool) | (t >= cap_s)
    return np.log1p(np.minimum(t, cap_s)), known


def _qr(alpha: float, seed: int) -> LGBMRegressor:
    return LGBMRegressor(objective="quantile", alpha=alpha, n_estimators=300, learning_rate=0.05, num_leaves=31,
                         min_child_samples=50, subsample=0.8, subsample_freq=1, colsample_bytree=0.8,
                         random_state=seed, verbose=-1)


def _conf_q(scores: np.ndarray, alpha: float = ALPHA) -> float:
    s = np.sort(scores)
    k = int(np.ceil((len(s) + 1) * (1 - alpha)))
    return np.inf if k > len(s) else s[k - 1]


def _weighted_q(scores: np.ndarray, w_cal: np.ndarray, w_test: np.ndarray, alpha: float = ALPHA) -> np.ndarray:
    """Phân vị conformal có trọng số cho từng điểm test (khối lượng w_test đặt ở +∞)."""
    o = np.argsort(scores)
    s, cw = scores[o], np.cumsum(w_cal[o])
    need = (1 - alpha) * (cw[-1] + w_test)
    idx = np.searchsorted(cw, need, side="left")
    out = np.full(len(w_test), np.inf)
    ok = idx < len(s)
    out[ok] = s[idx[ok]]
    return out


class CQR:
    """Hai mô hình phân vị (α, 1 − α) + điểm conformal trên tập calibration."""

    def __init__(self, X, y, seed):
        self.lo, self.hi = _qr(ALPHA, seed).fit(X, y), _qr(1 - ALPHA, seed).fit(X, y)

    def scores(self, X, y, known):
        s_lo = self.lo.predict(X) - y                               # y là cận dưới đúng của log1p(T̃)
        s_hi = np.where(known, y - self.hi.predict(X), np.inf)      # không biết T̃ → bảo thủ
        return s_lo, s_hi

    def calibrate(self, X, y, known):
        self.s_lo, self.s_hi = self.scores(X, y, known)
        self.q_lo, self.q_hi = _conf_q(self.s_lo), _conf_q(self.s_hi)
        return self

    def bounds(self, X, q_lo=None, q_hi=None):
        q_lo = self.q_lo if q_lo is None else q_lo
        q_hi = self.q_hi if q_hi is None else q_hi
        return self.lo.predict(X) - q_lo, self.hi.predict(X) + q_hi


def _eval(y, known, L, U, cap_s) -> dict:
    """Coverage + độ chặt (giờ). Với mẫu chưa biết T̃: phủ L chỉ khi duration ≥ L ; U tính là không phủ."""
    lo_h, hi_h = np.expm1(np.clip(L, 0, None)) / H, np.minimum(np.expm1(np.clip(U, 0, 50)), cap_s) / H
    return {"cov_lower": float(np.mean(y >= L)), "cov_upper": float(np.mean(known & (y <= U))),
            "L_median_h": float(np.median(lo_h)), "L_ge_1h": float(np.mean(lo_h >= 1)),
            "U_median_h": float(np.median(hi_h)), "U_lt_cap": float(np.mean(hi_h < cap_s / H))}


def _fit_cqr(X, y, known, groups, start, seed) -> CQR:
    fit, cal = held_out_time_split(groups, start, 0.2)
    return CQR(X.iloc[fit], y[fit], seed).calibrate(X.iloc[cal], y[cal], known[cal]), cal


def within(d: str, lab, X, cfg) -> pd.DataFrame:
    cap_s, seed = cfg["head_a"]["survival"]["admin_cap_s"], cfg["test_seed"]
    y, known = _target(lab, cap_s)
    groups, start = lab["group_id"].to_numpy(), lab["conv_start_s"].to_numpy()
    rows = []
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, cfg["cv"]["outer_k"])):
        assert_no_group_leak(groups, tr, va)
        assert_time_order(groups, start, tr, va)
        m, cal_i = _fit_cqr(X.iloc[tr], y[tr], known[tr], groups[tr], start[tr], seed)
        cal = tr[cal_i]
        Xv, yv, kv = X.iloc[va], y[va], known[va]
        marg_lo = _conf_q(-y[cal])                            # không dùng x: L hằng = phân vị 10% của T̃
        marg_hi = _conf_q(np.where(known[cal], y[cal], np.inf))
        lo_raw, hi_raw = m.lo.predict(Xv), m.hi.predict(Xv)
        for method, (L, U) in {"marginal": (np.full(len(va), -marg_lo), np.full(len(va), marg_hi)),
                               "qr_raw": (lo_raw, hi_raw), "cqr": m.bounds(Xv)}.items():
            rows.append({"dataset": d, "fold": fold, "method": method, "n_cal": len(cal), "n_val": len(va),
                         **_eval(yv, kv, L, U, cap_s)})
        print("within", d, fold, rows[-1]["cov_lower"], rows[-1]["cov_upper"], flush=True)
    return pd.DataFrame(rows)


def shift(src: str, dst: str, data: dict, cfg) -> pd.DataFrame:
    cap_s, seed = cfg["head_a"]["survival"]["admin_cap_s"], cfg["test_seed"]
    (lab_s, X_s), (lab_t, X_t) = data[src], data[dst]
    y_s, k_s = _target(lab_s, cap_s)
    y_t, k_t = _target(lab_t, cap_s)
    m, cal_s = _fit_cqr(X_s, y_s, k_s, lab_s["group_id"].to_numpy(), lab_s["conv_start_s"].to_numpy(), seed)
    Xc = X_s.iloc[cal_s]
    groups, start = lab_t["group_id"].to_numpy(), lab_t["conv_start_s"].to_numpy()
    rows = []
    for fold, (tr, va) in enumerate(forward_chaining(groups, start, cfg["cv"]["outer_k"])):
        Xv, yv, kv = X_t.iloc[va], y_t[va], k_t[va]
        # tỉ số mật độ w(x) = P(đích | x) / P(nguồn | x) · n_nguồn / n_đích ; chỉ dùng X đích không nhãn (outer-train)
        dom = LGBMClassifier(n_estimators=200, learning_rate=0.05, num_leaves=31, random_state=seed, verbose=-1)
        dom.fit(pd.concat([Xc, X_t.iloc[tr]]), np.r_[np.zeros(len(Xc)), np.ones(len(tr))])
        odds = lambda Z: (lambda p: p / (1 - p))(np.clip(dom.predict_proba(Z)[:, 1], 1e-4, 1 - 1e-4))
        r = len(Xc) / len(tr)
        w_cal, w_val = odds(Xc) * r, odds(Xv) * r
        ess = w_cal.sum() ** 2 / (w_cal ** 2).sum()
        q_lo_w, q_hi_w = _weighted_q(m.s_lo, w_cal, w_val), _weighted_q(m.s_hi, w_cal, w_val)
        fit_i, cal_i = held_out_time_split(groups[tr], start[tr], 0.2)
        recal = tr[cal_i]
        s_lo_t, s_hi_t = m.scores(X_t.iloc[recal], y_t[recal], k_t[recal])
        for method, (L, U) in {f"{src} CQR": m.bounds(Xv),
                               f"{src} CQR + trọng số": m.bounds(Xv, q_lo_w, q_hi_w),
                               f"{src} mô hình, calib lại trên {dst}": m.bounds(Xv, _conf_q(s_lo_t), _conf_q(s_hi_t))
                               }.items():
            rows.append({"dst": dst, "fold": fold, "method": method, "ess": ess if "trọng số" in method else np.nan,
                         **_eval(yv, kv, L, U, cap_s)})
        print("shift", src, dst, fold, f"ess={ess:.0f}", rows[-3]["cov_lower"], rows[-2]["cov_lower"], flush=True)
    return pd.DataFrame(rows)


COLS = [("cov_lower", "Coverage L"), ("L_median_h", "L trung vị (giờ)"), ("L_ge_1h", "Tỉ lệ L ≥ 1h"),
        ("cov_upper", "Coverage U"), ("U_median_h", "U trung vị (giờ)"), ("U_lt_cap", "Tỉ lệ U < 7 ngày")]


def _table(df: pd.DataFrame, key: str, extra: list[tuple[str, str]] = ()) -> list[str]:
    cols = COLS + list(extra)
    out = ["| Phương pháp | " + " | ".join(h for _, h in cols) + " |", "|---|" + "---|" * len(cols)]
    for k, g in df.groupby(key, sort=False):
        out.append(f"| {k} | " + " | ".join(mean_sd(g[c], 2 if c.endswith("_h") or c == "ess" else 3)
                                           if g[c].notna().all() else "—" for c, _ in cols) + " |")
    return out


def write_report(win: pd.DataFrame, sh: pd.DataFrame, cfg: dict, info: dict) -> None:
    lines = ["# Head A — conformal cho thời gian tới tin kế tiếp (A6, X8)", "",
             "> Sinh bởi `python -m src.m3_health.run_conformal_a`. Chỉ tập train ; cùng hội thoại và fold forward "
             "chaining K = 5 với `HEAD_A_SURVIVAL.md`. Mục tiêu 1 − α = **90%** cho từng cận một phía ; "
             "T̃ = min(T, 7 ngày). Mean ± SD qua 5 fold.", "",
             "- **L(x)** — cận dưới: \"cặp này còn im ít nhất L\" với xác suất ≥ 90% → không nhắc trước L.",
             "- **U(x)** — cận trên: \"có tin kế tiếp trong U (hoặc im > 7 ngày)\" với xác suất ≥ 90%.",
             "- `marginal` = cận hằng (không dùng x, chỉ conformal trên T̃) — mốc so độ chặt ; `qr_raw` = hồi quy "
             "phân vị LightGBM **không** conformal ; `cqr` = CQR (Romano et al. 2019).",
             "- Censoring thật xử lý bảo thủ (xem docstring) ; số mẫu censored rất nhỏ: "
             + ", ".join(f"{d}: {n}" for d, n in info.items()) + ".", ""]
    for d, g in win.groupby("dataset", sort=False):
        lines += [f"## {d} — trong miền", "", *_table(g, "method"), ""]
    lines += ["## Chuyển miền E1 ↔ E2 (RQ2)", "",
              "Mô hình phân vị + calibration học trên nguồn (80/20 theo thời gian), đánh giá trên fold đích. "
              "`+ trọng số` = weighted conformal, w(x) từ bộ phân loại miền (LightGBM) học trên calibration nguồn "
              "và X **không nhãn** của outer-train đích ; ESS = cỡ mẫu hiệu dụng của trọng số calibration. "
              "`calib lại` dùng nhãn của 20% group muộn nhất trong outer-train đích (giữ nguyên mô hình nguồn).", ""]
    for dst, g in sh.groupby("dst", sort=False):
        lines += [f"### Đích {dst}", "", *_table(g, "method", [("ess", "ESS")]), ""]
    out = ROOT / "docs" / "results" / "HEAD_A_CONFORMAL.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("wrote", out)


def main(datasets: list[str]) -> None:
    from src.m3_health.run_survival import load  # import muộn: sksurv không cần cho test

    cfg = load_config()
    cap_s = cfg["head_a"]["survival"]["admin_cap_s"]
    data = {d: load(d, cfg) for d in datasets}
    info = {d: int((~_target(lab, cap_s)[1]).sum()) for d, (lab, _) in data.items()}
    win = pd.concat([within(d, *data[d], cfg) for d in datasets], ignore_index=True)
    sh = pd.concat([shift(s, t, data, cfg) for s, t in (("E1", "E2"), ("E2", "E1"))], ignore_index=True)
    write_report(win, sh, cfg, info)
    for d in datasets:
        dh = data_hash(PROC / d / "labels_headA_train.parquet", PROC / d / "messages.parquet")
        for df, key, tag in ((win[win.dataset == d], "method", "conformal"), (sh[sh.dst == d], "method", "conf_shift")):
            for k, g in df.groupby(key, sort=False):
                append_ledger("m3_headA", "A", cfg, dh, [cfg["test_seed"]], f"{d}:{tag}:{k}", "coverage_lower_90",
                              g.cov_lower, notes=f"X8 CQR α={ALPHA}; cov_upper={g.cov_upper.mean():.3f}")


if __name__ == "__main__":
    main(sys.argv[1:] or ["E1", "E2"])
