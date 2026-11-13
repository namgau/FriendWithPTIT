"""B9 / X7: paired learning curves on Head A train-only forward folds.

No locked test input is read. The two models receive identical group subsets,
features and validation rows. TabPFN v2 requires an explicit checkpoint path.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import time
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from src.eval.splits import assert_no_group_leak, assert_time_order, forward_chaining
from src.m3_health.labels import PROC, load_config
from src.m3_health.run_head_a import MODELS, load


def group_subsets(groups, train_rows, sizes, seed, max_rows=10000):
    """Deterministic nested subsets of complete outer-train groups; never truncate rows."""
    groups = np.asarray(groups)
    train_rows = np.asarray(train_rows, dtype=int)
    if not sizes or any(not isinstance(n, int) or n <= 0 for n in sizes) or sorted(set(sizes)) != list(sizes):
        raise ValueError("sizes must be strictly increasing positive integers")
    unique = np.unique(groups[train_rows])
    if len(unique) < max(sizes):
        raise ValueError(f"outer-train has only {len(unique)} groups; requested {max(sizes)}")
    order = np.random.default_rng(seed).permutation(unique)
    result = {}
    for n in sizes:
        selected = train_rows[np.isin(groups[train_rows], order[:n])]
        if len(selected) > max_rows:
            raise ValueError(f"{n} groups contain {len(selected)} rows, exceeding TabPFN v2 limit {max_rows}")
        result[n] = selected
    return result


def run_fold_comparison(lab, X, sizes, seed, models, *, k=5, max_rows=10000, predict_batch=2048):
    if len(lab) != len(X) or not lab.index.equals(X.index):
        raise ValueError("labels and features must have identical indices")
    if predict_batch < 1:
        raise ValueError("predict_batch must be positive")
    groups, times = lab.group_id.to_numpy(), lab.conv_start_s.to_numpy()
    y = lab.y.to_numpy(dtype=int)
    X = X.replace([np.inf, -np.inf], np.nan)
    rows = []
    for fold, (tr, va) in enumerate(forward_chaining(groups, times, k)):
        assert_no_group_leak(groups, tr, va)
        assert_time_order(groups, times, tr, va)
        if len(np.unique(y[va])) != 2:
            raise ValueError(f"fold {fold}: validation has only one class")
        subsets = group_subsets(groups, tr, sizes, seed + fold, max_rows)
        for size, selected in subsets.items():
            if len(np.unique(y[selected])) != 2:
                raise ValueError(f"fold {fold}, size {size}: training has only one class")
            assert_no_group_leak(groups, selected, va)
            for name, make_model in models.items():
                model = make_model(seed + fold)
                start = time.perf_counter()
                model.fit(X.iloc[selected], y[selected])
                fit_seconds = time.perf_counter() - start
                start = time.perf_counter()
                p = np.concatenate([model.predict_proba(X.iloc[va[i:i + predict_batch]])[:, 1]
                                    for i in range(0, len(va), predict_batch)])
                predict_seconds = time.perf_counter() - start
                if not np.isfinite(p).all() or ((p < 0) | (p > 1)).any():
                    raise ValueError("invalid predicted probabilities")
                rows.append(dict(fold=fold, groups=size, train_rows=len(selected), val_rows=len(va),
                                 train_prevalence=float(y[selected].mean()), val_prevalence=float(y[va].mean()),
                                 model=name, pr_auc=average_precision_score(y[va], p),
                                 roc_auc=roc_auc_score(y[va], p), brier=brier_score_loss(y[va], p),
                                 fit_seconds=fit_seconds, predict_seconds=predict_seconds))
    return pd.DataFrame(rows)


def sha256(path: Path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(8 * 1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", choices=["E1", "E2"], required=True)
    parser.add_argument("--checkpoint", type=Path, required=True, help="explicit TabPFN v2 checkpoint")
    parser.add_argument("--output", type=Path, required=True, help="new CSV path; never overwritten")
    parser.add_argument("--groups", type=int, nargs="+", default=[100, 300, 1000])
    parser.add_argument("--max-rows", type=int, default=10000)
    parser.add_argument("--predict-batch", type=int, default=2048)
    parser.add_argument("--device", choices=["cuda", "cpu"], default="cuda")
    args = parser.parse_args()
    if args.max_rows < 1 or args.max_rows > 10000:
        parser.error("max-rows must be between 1 and 10000 for TabPFN v2")
    if args.output.suffix.lower() != ".csv":
        parser.error("output must be a .csv file")
    if args.output.exists() or args.output.with_suffix(".json").exists():
        parser.error("output already exists")
    if not args.checkpoint.is_file():
        parser.error("checkpoint does not exist")
    from importlib.metadata import version
    import torch
    from tabpfn import TabPFNClassifier
    if version("tabpfn") != "2.0.9":
        raise RuntimeError("B9 requires tabpfn==2.0.9")
    if args.device == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("CUDA not available; use GPU or explicitly choose --device cpu")
    cfg = load_config()
    lab, X = load(args.dataset, cfg)
    seed = int(cfg["test_seed"])
    models = {"lightgbm": lambda s: MODELS["lgbm"](s),
              "tabpfn_v2": lambda s: TabPFNClassifier(model_path=str(args.checkpoint), device=args.device, random_state=s)}
    result = run_fold_comparison(lab, X, args.groups, seed, models, k=cfg["cv"]["outer_k"],
                                 max_rows=args.max_rows, predict_batch=args.predict_batch)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="") as f:
        result.to_csv(f, index=False)
    def git(*cmd):
        try:
            return subprocess.run(["git", *cmd], text=True, capture_output=True, check=True).stdout.strip()
        except (OSError, subprocess.CalledProcessError):
            return None

    git_commit = git("rev-parse", "HEAD") or os.environ.get("B9_SOURCE_COMMIT", "unknown")
    git_status = git("status", "--porcelain", "--untracked-files=no")
    metadata = {"dataset": args.dataset, "checkpoint_sha256": sha256(args.checkpoint),
                "tabpfn": version("tabpfn"), "torch": version("torch"), "lightgbm": version("lightgbm"),
                "device": args.device, "gpu": torch.cuda.get_device_name(0) if args.device == "cuda" else None,
                "seed": seed, "groups": args.groups, "max_rows": args.max_rows,
                "predict_batch": args.predict_batch, "features": list(X.columns), "config": cfg,
                "input_sha256": {name: sha256(PROC / args.dataset / name)
                                 for name in ("messages.parquet", "labels_headA_train.parquet")},
                "git_commit": git_commit,
                "git_dirty": bool(git_status) if git_status is not None else None,
                "runner_sha256": sha256(Path(__file__))}
    with args.output.with_suffix(".json").open("x", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)
    print(f"Wrote {args.output} and metadata", flush=True)


if __name__ == "__main__":
    main()
