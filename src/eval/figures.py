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


if __name__ == "__main__":
    main()
