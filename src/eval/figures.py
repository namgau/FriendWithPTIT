"""Hình cho báo cáo M3 (docs/08) — chỉ số tổng hợp từ experiments/ledger.csv (đã commit) + OOF Head B.

Chạy:  python -m src.eval.figures      → docs/figures/*.png

Không chứa văn bản tin nhắn ; chỉ mean ± SD qua fold và đường hiệu chỉnh từ OOF của train.
"""
from __future__ import annotations

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from src.eval.report import LEDGER, ROOT

OUT = ROOT / "docs" / "figures"
BLUE, ORANGE, GRAY = "#2a78d6", "#eb6834", "#a3a29c"
INK, MUTED, GRID = "#0b0b0b", "#52514e", "#e6e5e0"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9, "axes.edgecolor": GRID,
                     "axes.labelcolor": MUTED, "xtick.color": MUTED, "ytick.color": INK,
                     "axes.spines.top": False, "axes.spines.right": False, "figure.dpi": 150})


def ledger() -> pd.DataFrame:
    led = pd.read_csv(LEDGER)
    return led.drop_duplicates(["module", "model", "metric_name"], keep="last")


def pick(led: pd.DataFrame, module: str, metric: str, names: dict[str, str]) -> pd.DataFrame:
    """names: tên model trong ledger → nhãn hiển thị ; giữ thứ tự của dict."""
    rows = led[(led.module == module) & (led.metric_name == metric)].set_index("model")
    keep = [k for k in names if k in rows.index]
    return pd.DataFrame({"label": [names[k] for k in keep], "mean": rows.loc[keep, "metric_mean"].to_numpy(),
                         "sd": rows.loc[keep, "metric_sd"].to_numpy()})


def _hbars(ax, df: pd.DataFrame, colors, title: str, xlabel: str = "PR-AUC (mean ± SD qua fold)") -> None:
    y = np.arange(len(df))[::-1]
    ax.barh(y, df["mean"], height=0.6, color=colors, xerr=df["sd"],
            error_kw={"ecolor": MUTED, "elinewidth": 1, "capsize": 2})
    xmax = min(1, (df["mean"] + df["sd"]).max() * 1.25)
    for yi, m, s in zip(y, df["mean"], df["sd"]):
        ax.text(m + s + 0.02 * xmax, yi, f"{m:.3f}", va="center", fontsize=8, color=INK)
    ax.set_yticks(y, df["label"])
    ax.set_xlim(0, xmax)
    ax.set_title(title, loc="left", fontsize=10, color=INK)
    ax.set_xlabel(xlabel)
    ax.grid(axis="x", color=GRID, linewidth=0.6)
    ax.set_axisbelow(True)


def _best(led: pd.DataFrame, module: str, d: str, model: str) -> str:
    rows = led[(led.module == module) & (led.metric_name == "pr_auc") & led.model.str.match(rf"{d}:{model}:[a-z_]+$")]
    return rows.loc[rows.metric_mean.idxmax(), "model"]


def head_a_models(led: pd.DataFrame) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(9, 2.8))
    for ax, d in zip(axes, ("E1", "E2")):
        names = {f"{d}:majority:none": "Đa số", f"{d}:rule:none": "Rule tài liệu gốc",
                 f"{d}:gap_last:none": "Chỉ gap_last", _best(led, "m3_headA", d, "lr"): "LR",
                 _best(led, "m3_headA", d, "lgbm"): "LightGBM"}
        df = pick(led, "m3_headA", "pr_auc", names)
        _hbars(ax, df, [GRAY] * 3 + [BLUE] * 2, f"{d} — Head A (Δ = 48h)")
    fig.suptitle("Baseline (xám) và mô hình học (xanh) ; LR/LightGBM: phương án mất cân bằng tốt nhất",
                 x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "headA_models.png")


ABLATION = ["T1_rhythm", "T1", "T1+T2 (đủ)", "T1+T2+T3", "T1+T2+T4", "T1+T2+T3+T4"]


def head_a_ablation(led: pd.DataFrame) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(9, 2.8))
    for ax, d in zip(axes, ("E1", "E2")):
        df = pick(led, "m3_headA", "pr_auc", {f"{d}:ablation:{s}": s for s in ABLATION})
        colors = [BLUE if "T3" in s or "T4" in s else GRAY for s in df["label"]]
        _hbars(ax, df, colors, f"{d} — thêm dần nhóm đặc trưng (LightGBM)")
    fig.suptitle("Xám: nhịp/hành vi (T1, T2) ; xanh: có thêm phong cách (T3) hoặc ngữ nghĩa embedding (T4)",
                 x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "headA_ablation.png")


def head_b_models(led: pd.DataFrame) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(10.5, 2.4))
    for ax, d in zip(axes, ("G1", "H1", "H2")):
        names = {f"{d}:majority": "Đa số", f"{d}:lexicon": "Từ điển", f"{d}:length_lr": "Độ dài + LR",
                 f"{d}:tfidf_lr": "TF-IDF + LR"}
        df = pick(led, "m3_headB", "pr_auc", names)
        _hbars(ax, df, [GRAY] * 3 + [BLUE], d)
    _save(fig, "headB_models.png")


STRATEGIES = {"none": "none", "class_weight": "class\nweight", "undersample": "under-\nsample",
              "oversample": "over-\nsample", "smote": "SMOTE"}


def imbalance_b(led: pd.DataFrame) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.2), sharey=True)
    w = 0.38
    for ax, d in zip(axes, ("H1", "H2")):
        x = np.arange(len(STRATEGIES))
        for off, (feat, label, color) in zip((-w / 2, w / 2), (("tfidf_lr", "TF-IDF", BLUE),
                                                               ("emb_lr", "BGE-M3 đóng băng", ORANGE))):
            df = pick(led, "m3_headB", "pr_auc", {f"{d}:{feat}:{s}": s for s in STRATEGIES})
            ax.bar(x + off, df["mean"], width=w - 0.04, color=color, label=label, yerr=df["sd"],
                   error_kw={"ecolor": MUTED, "elinewidth": 1, "capsize": 2})
        ax.set_xticks(x, list(STRATEGIES.values()))
        ax.set_title(f"{d} — PR-AUC theo phương án mất cân bằng", loc="left", fontsize=10, color=INK)
        ax.grid(axis="y", color=GRID, linewidth=0.6)
        ax.set_axisbelow(True)
    axes[0].set_ylabel("PR-AUC (mean ± SD)")
    fig.legend(*axes[0].get_legend_handles_labels(), frameon=False, loc="upper right", ncol=2, fontsize=8)
    fig.suptitle("LR + chuẩn hóa ; xác suất hiệu chỉnh Platt", x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "imbalanceB.png")


def head_b_reliability(bins: int = 10) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(9, 3), sharey=True)
    for ax, d in zip(axes, ("G1", "H1", "H2")):
        path = ROOT / "reports" / f"headB_oof_{d}.csv"
        if not path.exists():
            ax.set_visible(False)
            continue
        oof = pd.read_csv(path, usecols=["y", "p"])
        q = pd.qcut(oof.p, bins, duplicates="drop")
        g = oof.groupby(q, observed=True).agg(p=("p", "mean"), y=("y", "mean"))
        ax.plot([0, 1], [0, 1], color=GRAY, linewidth=1, linestyle="--")
        ax.plot(g.p, g.y, color=BLUE, linewidth=2, marker="o", markersize=4)
        ece = float((oof.groupby(q, observed=True).size() / len(oof) * (g.p - g.y).abs()).sum())
        ax.set_title(f"{d} — ECE ≈ {ece:.3f}", loc="left", fontsize=10, color=INK)
        ax.set_xlabel("Xác suất dự đoán (TF-IDF + LR, Platt)")
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.grid(color=GRID, linewidth=0.6)
    axes[0].set_ylabel("Tỉ lệ dương thực tế")
    fig.suptitle(f"Đường hiệu chỉnh OOF ({bins} bin theo phân vị) ; đường chéo = hiệu chỉnh hoàn hảo",
                 x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "headB_reliability.png")


ERR = ROOT / "docs" / "results" / "ERROR_ANALYSIS.md"


def _md_rows(text: str, header: str) -> list[list[str]]:
    """Các dòng của bảng markdown ngay sau dòng tiêu đề bảng `header` (file do script sinh)."""
    lines = text.splitlines()
    i = next(k for k, s in enumerate(lines) if s.startswith(header)) + 2
    rows = []
    while i < len(lines) and lines[i].startswith("|"):
        rows.append([c.strip().replace(",", "") for c in lines[i].strip("|").split("|")])
        i += 1
    return rows


def learning_curve() -> None:
    rows = pd.DataFrame(_md_rows(ERR.read_text(encoding="utf-8"), "| dataset | tỉ lệ train"),
                        columns=["d", "frac", "n", "train", "val"]).astype({"n": int, "train": float, "val": float})
    fig, axes = plt.subplots(1, 2, figsize=(9, 2.8))
    for ax, (d, g) in zip(axes, rows.groupby("d")):
        ax.plot(g.n, g.train, color=GRAY, marker="o", markersize=4, label="train")
        ax.plot(g.n, g.val, color=BLUE, marker="o", markersize=4, linewidth=2, label="validation")
        for x, v in zip(g.n, g.val):
            ax.text(x, v - 0.06, f"{v:.3f}", ha="center", fontsize=7, color=INK)
        ax.set_xscale("log")
        ax.xaxis.set_minor_formatter(matplotlib.ticker.NullFormatter())
        ax.set_ylim(0, 1)
        ax.set_title(f"{d} — learning curve (LightGBM T1+T2)", loc="left", fontsize=10, color=INK)
        ax.set_xlabel("Số mẫu train (thang log)")
        ax.grid(color=GRID, linewidth=0.6)
    axes[0].set_ylabel("PR-AUC")
    axes[0].legend(frameon=False, fontsize=8)
    fig.suptitle("Fold cuối, lấy ngẫu nhiên 10–100% group của outer-train ; nguồn ERROR_ANALYSIS.md (E4)",
                 x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "headA_learning_curve.png")


def shap_summary() -> None:
    lines = [s for s in ERR.read_text(encoding="utf-8").splitlines() if s.startswith("**E3 — đóng góp SHAP")]
    fig, axes = plt.subplots(1, 2, figsize=(9, 2.8))
    for ax, d, s in zip(axes, ("E1", "E2"), lines[:2]):  # thứ tự trong file: Head A E1 rồi E2
        pairs = [p.strip().rsplit(" ", 1) for p in s.split(":", 1)[1].strip(" .").split(",")]
        df = pd.DataFrame({"label": [a for a, _ in pairs], "mean": [float(b) for _, b in pairs], "sd": 0.0})
        _hbars(ax, df, [BLUE] * len(df), f"{d} — top 8 đặc trưng", "mean |SHAP| (log-odds)")
        ax.set_xlim(0, df["mean"].max() * 1.25)  # _hbars chặn trục ở 1 (thang xác suất), SHAP có thể > 1
    fig.suptitle("LightGBM T1+T2 trên OOF train ; nguồn ERROR_ANALYSIS.md (E3)", x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "headA_shap.png")


def survival_km(cap_h: float = 168) -> None:
    from lifelines import KaplanMeierFitter

    from src.m3_health.labels import cv_conversations, load_config, load_train
    cfg = load_config()
    fig, ax = plt.subplots(figsize=(6, 3))
    for d, color in (("E1", BLUE), ("E2", ORANGE)):
        lab = load_train(d)
        lab = lab[lab["conversation_id"].isin(cv_conversations(lab, cfg))]
        t = lab["duration_s"].to_numpy(float) / 3600
        e = lab["event"].to_numpy(bool) & (t <= cap_h)
        km = KaplanMeierFitter().fit(np.minimum(t, cap_h), e)
        sf = km.survival_function_.iloc[:, 0]
        ax.step(sf.index, sf.values, where="post", color=color, linewidth=2, label=f"{d} (n = {len(t):,})")
        s48 = float(km.predict(48))
        ax.annotate(f"{s48:.2f}", (48, s48), xytext=(6, 4), textcoords="offset points", fontsize=8, color=color)
    ax.axvline(48, color=GRAY, linestyle="--", linewidth=1)
    ax.set_xscale("symlog", linthresh=1)
    ax.set_xlim(0, cap_h)
    ax.set_ylim(0, 1)
    ax.set_xlabel("Giờ kể từ mốc t (thang symlog) ; nét đứt: Δ = 48h")
    ax.set_ylabel("P(chưa có tin mới)")
    ax.set_title("Kaplan–Meier thời gian tới tin kế tiếp (train)", loc="left", fontsize=10, color=INK)
    ax.grid(color=GRID, linewidth=0.6)
    ax.legend(frameon=False, fontsize=8)
    _save(fig, "headA_survival_km.png")


TEST_CHOSEN = {"E1": "lgbm_T1-T4", "E2": "lgbm_T1-T4", "G1": "tfidf_lr", "H1": "phobert_c2", "H2": "tfidf_lr"}


def reliability_test(bins: int = 10) -> None:
    """Đường hiệu chỉnh trên TEST (A8) — chỉ để báo cáo, không dùng chỉnh mô hình."""
    fig, axes = plt.subplots(1, 5, figsize=(13, 2.9), sharey=True)
    for ax, (d, col) in zip(axes, TEST_CHOSEN.items()):
        path = ROOT / "reports" / f"test_scores_{d}.parquet"
        if not path.exists():
            ax.set_visible(False)
            continue
        s = pd.read_parquet(path, columns=["y", col]).rename(columns={col: "p"})
        q = pd.qcut(s.p, bins, duplicates="drop")
        g = s.groupby(q, observed=True).agg(p=("p", "mean"), y=("y", "mean"), n=("y", "size"))
        ece = float((g.n / len(s) * (g.p - g.y).abs()).sum())
        ax.plot([0, 1], [0, 1], color=GRAY, linewidth=1, linestyle="--")
        ax.plot(g.p, g.y, color=BLUE, linewidth=2, marker="o", markersize=4)
        ax.set_title(f"{d} — ECE ≈ {ece:.3f}", loc="left", fontsize=10, color=INK)
        ax.set_xlabel(col.replace("lgbm_", "LightGBM ").replace("tfidf_lr", "TF-IDF + LR").replace("phobert_c2", "PhoBERT"))
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.grid(color=GRID, linewidth=0.6)
    axes[0].set_ylabel("Tỉ lệ dương thực tế")
    fig.suptitle(f"Đường hiệu chỉnh của mô hình chốt trên TEST ({bins} bin theo phân vị) ; đường chéo = hoàn hảo",
                 x=0.01, ha="left", fontsize=8, color=MUTED)
    _save(fig, "reliability_test.png")


def _save(fig, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(OUT / name, facecolor="white")
    plt.close(fig)
    print("→", (OUT / name).relative_to(ROOT))


def main() -> None:
    led = ledger()
    head_a_models(led)
    head_a_ablation(led)
    head_b_models(led)
    imbalance_b(led)
    head_b_reliability()
    learning_curve()
    shap_summary()
    reliability_test()
    survival_km()


if __name__ == "__main__":
    main()
