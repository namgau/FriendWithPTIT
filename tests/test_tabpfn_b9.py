import numpy as np
import pandas as pd
import pytest

from src.m3_health.run_tabpfn_b9 import group_subsets, run_fold_comparison


def test_subsets_are_nested_group_complete_and_bounded():
    groups = np.repeat(np.arange(12), 2)
    train = np.arange(20)
    subsets = group_subsets(groups, train, [2, 4], seed=42, max_rows=8)
    assert set(subsets[2]).issubset(subsets[4])
    assert len(subsets[2]) == 4 and len(subsets[4]) == 8
    assert set(groups[subsets[4]]).isdisjoint(groups[20:])
    assert np.array_equal(subsets[4], group_subsets(groups, train, [2, 4], 42, 8)[4])
    with pytest.raises(ValueError, match="exceeding"):
        group_subsets(groups, train, [5], 42, max_rows=9)
    with pytest.raises(ValueError, match="strictly increasing"):
        group_subsets(groups, train, [4, 2], 42)


def test_paired_models_use_exact_same_groups_and_validation():
    class Fake:
        def __init__(self, label):
            self.label = label
        def fit(self, x, y):
            calls.append((self.label, 'fit', tuple(x.index)))
            return self
        def predict_proba(self, x):
            calls.append((self.label, 'predict', tuple(x.index)))
            p = np.where(x['feature'].to_numpy() % 2, .7, .3)
            return np.stack([1 - p, p], axis=1)
    calls = []
    n = 60
    lab = pd.DataFrame({'group_id': np.arange(n), 'conv_start_s': np.arange(n), 'y': np.arange(n) % 2})
    x = pd.DataFrame({'feature': np.arange(n)})
    result = run_fold_comparison(lab, x, [8], 42, {'a': lambda _: Fake('a'), 'b': lambda _: Fake('b')}, k=2, predict_batch=7)
    assert len(result) == 4
    for fold in range(2):
        assert result.iloc[fold * 2].pr_auc == result.iloc[fold * 2 + 1].pr_auc
    fit_calls = [(label, idx) for label, typ, idx in calls if typ == 'fit']
    assert fit_calls[0][1] == fit_calls[1][1] and fit_calls[2][1] == fit_calls[3][1]
    for fold in range(2):
        per_fold = calls[fold * 8:(fold + 1) * 8]
        a = [x[2] for x in per_fold if x[:2] == ('a', 'predict')]
        b = [x[2] for x in per_fold if x[:2] == ('b', 'predict')]
        assert a == b


def test_no_partial_output_for_invalid_input():
    lab = pd.DataFrame({'group_id': range(12), 'conv_start_s': range(12), 'y': [0, 1] * 6})
    with pytest.raises(ValueError, match='identical indices'):
        run_fold_comparison(lab, pd.DataFrame({'x': range(12)}, index=range(1, 13)), [2], 42, {}, k=2)


def test_prediction_sink_receives_paired_validation_and_group_ids():
    class Fake:
        def fit(self, x, y):
            return self
        def predict_proba(self, x):
            p = np.where(x['feature'].to_numpy() % 2, .7, .3)
            return np.stack([1 - p, p], axis=1)
    n = 60
    lab = pd.DataFrame({'group_id': np.arange(n), 'conv_start_s': np.arange(n), 'y': np.arange(n) % 2})
    records = []
    run_fold_comparison(lab, pd.DataFrame({'feature': np.arange(n)}), [8], 42,
                        {'a': lambda _: Fake(), 'b': lambda _: Fake()}, k=2,
                        prediction_sink=lambda *args: records.append(args))
    assert len(records) == 4
    for a, b in zip(records[::2], records[1::2]):
        assert a[:2] == b[:2]
        for x, y in zip(a[3:], b[3:]):
            np.testing.assert_array_equal(x, y)
