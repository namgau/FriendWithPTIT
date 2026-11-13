"""Paired, within-fold group bootstrap for B9 OOF predictions.

The statistic is the unweighted mean of fold PR-AUC differences. Models and
folds stay fixed; group resampling is independent within each validation fold.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score


def paired_ci(frame: pd.DataFrame, *, repeats: int = 2000, seed: int = 42) -> dict:
    columns = {"fold", "row", "group_id", "y", "model", "probability"}
    if missing := columns.difference(frame.columns):
        raise ValueError(f"missing columns: {sorted(missing)}")
    if repeats < 2 or frame[list(columns)].isna().any().any():
        raise ValueError("invalid repeats or missing values")
    if frame.duplicated(["fold", "row", "model"]).any():
        raise ValueError("duplicate predictions")
    a = frame[frame.model == "tabpfn_v2"].set_index(["fold", "row"]).sort_index()
    b = frame[frame.model == "lightgbm"].set_index(["fold", "row"]).sort_index()
    if not a.index.equals(b.index) or not a[["group_id", "y"]].equals(b[["group_id", "y"]]):
        raise ValueError("unpaired predictions")
    if len(frame) != len(a) + len(b):
        raise ValueError("unknown model")
    rng = np.random.default_rng(seed)
    folds = []
    for fold in a.index.get_level_values("fold").unique():
        left, right = a.xs(fold), b.xs(fold)
        y = left.y.to_numpy(dtype=int)
        if set(np.unique(y)) != {0, 1}:
            raise ValueError("each fold requires both classes")
        _, inv = np.unique(left.group_id.to_numpy(), return_inverse=True)
        groups = inv.max() + 1
        folds.append((y, left.probability.to_numpy(), right.probability.to_numpy(), inv, groups))
    point = np.mean([average_precision_score(y, pa) - average_precision_score(y, pb)
                     for y, pa, pb, _, _ in folds])
    reps = []
    for _ in range(repeats):
        scores = []
        for y, pa, pb, inv, ngroups in folds:
            weights = rng.multinomial(ngroups, np.full(ngroups, 1 / ngroups))[inv]
            positive = weights[y == 1].sum()
            if positive == 0 or positive == weights.sum():
                break
            scores.append(average_precision_score(y, pa, sample_weight=weights) -
                          average_precision_score(y, pb, sample_weight=weights))
        if len(scores) == len(folds):
            reps.append(np.mean(scores))
    if len(reps) < repeats * .95:
        raise ValueError("too many degenerate bootstrap replicates")
    return {"estimate": float(point), "ci_low": float(np.quantile(reps, .025)),
            "ci_high": float(np.quantile(reps, .975)), "valid": len(reps),
            "repeats": repeats, "seed": seed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repeats", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("output exists")
    frame = pd.read_parquet(args.input)
    rows = []
    for size, group in frame.groupby("groups", sort=True):
        print(f"Bootstrapping {size} groups", flush=True)
        rows.append({"groups": size, **paired_ci(group, repeats=args.repeats, seed=args.seed)})
    with args.output.open("x", encoding="utf-8", newline="") as out:
        pd.DataFrame(rows).to_csv(out, index=False)


if __name__ == "__main__":
    main()
