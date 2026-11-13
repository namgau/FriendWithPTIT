"""Generate a reproducible B9 learning-curve figure from the saved fold CSVs."""
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]


def main():
    fig, axes = plt.subplots(1, 2, figsize=(10, 4), sharey=True, constrained_layout=True)
    for ax, dataset in zip(axes, ("E1", "E2")):
        data = pd.read_csv(ROOT / "docs" / "results" / f"B9_{dataset}.csv")
        for model, name in (("lightgbm", "LightGBM"), ("tabpfn_v2", "TabPFN v2")):
            values = data[data.model == model].groupby("groups").pr_auc.agg(["mean", "std"])
            ax.errorbar(values.index, values["mean"], yerr=values["std"], marker="o", capsize=4, label=name)
        ax.set_xscale("log")
        ax.set_xticks([100, 300, 1000], ["100", "300", "1,000"])
        ax.set_title(dataset)
        ax.set_xlabel("Số group train")
        ax.grid(alpha=.25)
    axes[0].set_ylabel("PR-AUC validation (mean ± SD, 5 fold)")
    axes[1].legend()
    fig.suptitle("B9: learning curve Head A — CV trên train")
    fig.savefig(ROOT / "docs" / "results" / "B9_learning_curve.png", dpi=160)
    plt.close(fig)


if __name__ == "__main__":
    main()
