"""B10 H2 group splits and independent imbalance strategies for C2 PhoBERT."""
from __future__ import annotations

import numpy as np

from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split


def h2_splits(y, groups, *, folds=5, split_seed=42, calibration_fraction=.2):
    y, groups = np.asarray(y, dtype=int), np.asarray(groups)
    if len(y) != len(groups) or not set(np.unique(y)).issubset({0, 1}):
        raise ValueError('invalid labels or groups')
    for fold, (outer_train, validation) in enumerate(group_kfold(groups, folds, seed=split_seed, y=y)):
        fit_idx, cal_idx = held_out_group_split(groups[outer_train], calibration_fraction,
                                                  seed=split_seed + fold, y=y[outer_train])
        fit, cal = outer_train[fit_idx], outer_train[cal_idx]
        for left, right in ((fit, cal), (fit, validation), (cal, validation)):
            assert_no_group_leak(groups, left, right)
        if any(len(np.unique(y[idx])) != 2 for idx in (fit, cal, validation)):
            raise ValueError(f'fold {fold}: each split must contain both classes')
        yield fit, cal, validation


def oversample_fit_indices(y, fit, *, seed):
    """Balance binary classes via replacement only inside fit; preserve all originals."""
    y, fit = np.asarray(y, dtype=int), np.asarray(fit, dtype=int)
    classes, counts = np.unique(y[fit], return_counts=True)
    if not np.array_equal(classes, [0, 1]):
        raise ValueError('fit requires both binary classes')
    rng = np.random.default_rng(seed)
    target = counts.max()
    extra = [rng.choice(fit[y[fit] == cls], size=int(target - count), replace=True)
             for cls, count in zip(classes, counts) if count < target]
    combined = np.concatenate([fit, *extra]) if extra else fit.copy()
    return rng.permutation(combined)
