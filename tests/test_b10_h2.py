import numpy as np
import pytest

from src.m3_health.b10_h2 import h2_splits, oversample_fit_indices
from src.eval.splits import group_kfold, held_out_group_split


def test_oversampling_is_balanced_deterministic_and_fit_only():
    y = np.array([0, 0, 0, 1, 1, 1, 1])
    fit = np.array([0, 1, 2, 3])
    selected = oversample_fit_indices(y, fit, seed=42)
    assert set(selected) == set(fit)
    assert np.bincount(y[selected]).tolist() == [3, 3]
    np.testing.assert_array_equal(selected, oversample_fit_indices(y, fit, seed=42))
    with pytest.raises(ValueError):
        oversample_fit_indices(y, [0, 1], seed=1)


def test_splits_match_a_and_no_group_leak():
    groups = np.repeat(np.arange(60), 4)
    y = np.tile([0, 1, 0, 1], 60)
    splits = list(h2_splits(y, groups))
    for fold, ((fit, cal, val), (tr, expected_val)) in enumerate(zip(splits, group_kfold(groups, 5, seed=42, y=y))):
        a, b = held_out_group_split(groups[tr], .2, seed=42 + fold, y=y[tr])
        np.testing.assert_array_equal(fit, tr[a])
        np.testing.assert_array_equal(cal, tr[b])
        np.testing.assert_array_equal(val, expected_val)
        assert set(groups[fit]).isdisjoint(groups[cal])
        assert set(groups[fit]).isdisjoint(groups[val])
        assert set(groups[cal]).isdisjoint(groups[val])
