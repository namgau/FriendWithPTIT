"""Test chống rò rỉ cho src/eval/splits.py — chạy trong CI, không được skip."""
import numpy as np
import pytest

from src.eval import splits as S


@pytest.fixture
def data():
    rng = np.random.default_rng(0)
    n_groups = 300
    sizes = rng.integers(1, 15, n_groups)                    # số mẫu mỗi group khác nhau
    groups = np.repeat([f"g{i}" for i in range(n_groups)], sizes)
    start = np.repeat(rng.uniform(0, 1e6, n_groups), sizes)  # group bắt đầu ở thời điểm ngẫu nhiên
    times = start + rng.uniform(0, 1e4, len(groups))         # các mẫu trong group lệch nhau một chút
    y = (rng.random(len(groups)) < 0.2).astype(int)
    return groups, times, y


def _covers_all(n, a, b):
    assert len(np.intersect1d(a, b)) == 0
    assert len(a) + len(b) == n


def test_held_out_time_split(data):
    g, t, _ = data
    tr, te = S.held_out_time_split(g, t, 0.2)
    _covers_all(len(g), tr, te)
    S.assert_no_group_leak(g, tr, te)
    S.assert_time_order(g, t, tr, te)
    assert abs(len(np.unique(g[te])) / len(np.unique(g)) - 0.2) < 0.01


def test_held_out_group_split_stratified(data):
    g, _, y = data
    tr, te = S.held_out_group_split(g, 0.2, seed=42, y=y)
    _covers_all(len(g), tr, te)
    S.assert_no_group_leak(g, tr, te)
    assert abs(y[te].mean() - y[tr].mean()) < 0.05


def test_forward_chaining(data):
    g, t, _ = data
    folds = list(S.forward_chaining(g, t, k=5))
    assert len(folds) == 5
    for tr, va in folds:
        S.assert_no_group_leak(g, tr, va)
        S.assert_time_order(g, t, tr, va)
    assert all(len(folds[i][0]) < len(folds[i + 1][0]) for i in range(4))  # train lớn dần


@pytest.mark.parametrize("use_y", [False, True])
def test_group_kfold(data, use_y):
    g, _, y = data
    folds = list(S.group_kfold(g, 5, seed=1, y=y if use_y else None))
    for tr, va in folds:
        _covers_all(len(g), tr, va)
        S.assert_no_group_leak(g, tr, va)
    all_val = np.concatenate([va for _, va in folds])
    assert np.array_equal(np.sort(all_val), np.arange(len(g)))  # mỗi mẫu vào val đúng 1 lần


def test_group_kfold_reproducible(data):
    g, _, _ = data
    a = [va for _, va in S.group_kfold(g, 5, seed=7)]
    b = [va for _, va in S.group_kfold(g, 5, seed=7)]
    c = [va for _, va in S.group_kfold(g, 5, seed=8)]
    assert all(np.array_equal(x, z) for x, z in zip(a, b))
    assert not all(np.array_equal(x, z) for x, z in zip(a, c))


def test_nested_group_cv(data):
    g, _, y = data
    for tr, va, inner in S.nested_group_cv(g, 5, 3, seed=42, y=y):
        S.assert_no_group_leak(g, tr, va)
        assert len(inner) == 3
        for itr, iva in inner:
            assert np.isin(itr, tr).all() and np.isin(iva, tr).all()  # vòng trong không chạm outer-val
            S.assert_no_group_leak(g, itr, iva)


def test_nested_forward_chaining(data):
    g, t, _ = data
    for tr, va, inner in S.nested_forward_chaining(g, t, 5, 3):
        S.assert_time_order(g, t, tr, va)
        for itr, iva in inner:
            assert np.isin(iva, tr).all()
            S.assert_time_order(g, t, itr, iva)


def test_leak_checks_catch_leaks(data):
    g, t, _ = data
    tr, te = S.held_out_time_split(g, t, 0.2)
    with pytest.raises(AssertionError):
        S.assert_no_group_leak(g, tr, np.concatenate([te, tr[:1]]))
    with pytest.raises(AssertionError):
        S.assert_time_order(g, t, te, tr)
