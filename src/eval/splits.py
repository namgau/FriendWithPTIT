"""Chia dữ liệu theo nhóm và theo thời gian (docs/01_CHUAN_BAO_CAO_AUDITABLE.md §2, 06 §4).

Mọi hàm nhận mảng theo **từng mẫu** (một dòng = một mẫu) và trả về chỉ số dòng.
- ``groups``: đơn vị độc lập (cột ``group_id``): mọi mẫu cùng group luôn nằm cùng một phía.
- ``times``: thời điểm bắt đầu hội thoại của mẫu; group bắt đầu lúc mẫu sớm nhất của nó.
"""
from __future__ import annotations

from collections.abc import Iterator

import numpy as np
from sklearn.model_selection import StratifiedGroupKFold

Split = tuple[np.ndarray, np.ndarray]


def _group_index(groups) -> tuple[np.ndarray, np.ndarray]:
    uniq, inv = np.unique(np.asarray(groups), return_inverse=True)
    return uniq, inv


def _rows_of(group_mask: np.ndarray, inv: np.ndarray) -> np.ndarray:
    return np.flatnonzero(group_mask[inv])


def _groups_by_start(groups, times) -> tuple[np.ndarray, np.ndarray]:
    """Trả về (thứ tự group theo thời điểm bắt đầu, inv)."""
    uniq, inv = _group_index(groups)
    start = np.full(len(uniq), np.inf)
    np.minimum.at(start, inv, np.asarray(times, dtype=float))
    return np.argsort(start, kind="stable"), inv


# ------------------------------------------------------------- tập test

def held_out_time_split(groups, times, test_fraction: float = 0.2) -> Split:
    """Test = ``test_fraction`` số group bắt đầu muộn nhất (dùng cho E1, E2)."""
    order, inv = _groups_by_start(groups, times)
    n_test = max(1, int(round(test_fraction * len(order))))
    is_test = np.zeros(len(order), bool)
    is_test[order[-n_test:]] = True
    return _rows_of(~is_test, inv), _rows_of(is_test, inv)


def held_out_group_split(groups, test_fraction: float = 0.2, seed: int = 42, y=None) -> Split:
    """Test ngẫu nhiên theo group (dùng cho G1, H1, H2: không có/không cần trục thời gian).

    Có ``y`` thì phân tầng theo nhãn để prevalence test ≈ train.
    """
    k = int(round(1 / test_fraction))
    return next(group_kfold(groups, k, seed=seed, y=y))


# ------------------------------------------------------------- CV trên train

def forward_chaining(groups, times, k: int = 5) -> Iterator[Split]:
    """Chia group theo thời gian thành k+1 khối liên tiếp; fold i: train = khối 0..i, val = khối i+1."""
    order, inv = _groups_by_start(groups, times)
    blocks = np.array_split(order, k + 1)
    for i in range(k):
        tr = np.zeros(len(order), bool)
        tr[np.concatenate(blocks[: i + 1])] = True
        va = np.zeros(len(order), bool)
        va[blocks[i + 1]] = True
        yield _rows_of(tr, inv), _rows_of(va, inv)


def group_kfold(groups, k: int = 5, seed: int = 42, y=None) -> Iterator[Split]:
    """K-fold theo group, xáo trộn bằng ``seed``; có ``y`` thì phân tầng (StratifiedGroupKFold)."""
    groups = np.asarray(groups)
    if y is not None:
        cv = StratifiedGroupKFold(n_splits=k, shuffle=True, random_state=seed)
        yield from cv.split(np.zeros(len(groups)), np.asarray(y), groups)
        return
    uniq, inv = _group_index(groups)
    perm = np.random.default_rng(seed).permutation(len(uniq))
    for fold in np.array_split(perm, k):
        va = np.zeros(len(uniq), bool)
        va[fold] = True
        yield _rows_of(~va, inv), _rows_of(va, inv)


def repeated_group_kfold(groups, k: int = 5, seeds=(42, 43, 44), y=None) -> Iterator[tuple[int, int, np.ndarray, np.ndarray]]:
    """Lặp group k-fold với nhiều seed; yield (seed, fold, train, val)."""
    for s in seeds:
        for f, (tr, va) in enumerate(group_kfold(groups, k, seed=s, y=y)):
            yield s, f, tr, va


def nested_group_cv(groups, outer_k: int = 5, inner_k: int = 3, seed: int = 42, y=None
                    ) -> Iterator[tuple[np.ndarray, np.ndarray, list[Split]]]:
    """Nested CV: vòng ngoài đánh giá, vòng trong chọn siêu tham số chỉ trên outer-train.

    Yield (outer_train, outer_val, inner_splits) — mọi chỉ số đều là chỉ số dòng gốc.
    """
    groups = np.asarray(groups)
    yy = None if y is None else np.asarray(y)
    for tr, va in group_kfold(groups, outer_k, seed=seed, y=yy):
        inner = [(tr[a], tr[b]) for a, b in
                 group_kfold(groups[tr], inner_k, seed=seed + 1, y=None if yy is None else yy[tr])]
        yield tr, va, inner


def nested_forward_chaining(groups, times, outer_k: int = 5, inner_k: int = 3
                            ) -> Iterator[tuple[np.ndarray, np.ndarray, list[Split]]]:
    """Nested CV giữ thứ tự thời gian (E1, E2): vòng trong cũng là forward chaining trên outer-train."""
    groups, times = np.asarray(groups), np.asarray(times)
    for tr, va in forward_chaining(groups, times, outer_k):
        inner = [(tr[a], tr[b]) for a, b in forward_chaining(groups[tr], times[tr], inner_k)]
        yield tr, va, inner


# ------------------------------------------------------------- kiểm tra rò rỉ

def assert_no_group_leak(groups, a, b) -> None:
    groups = np.asarray(groups)
    shared = np.intersect1d(groups[a], groups[b])
    if len(shared):
        raise AssertionError(f"{len(shared)} group nằm ở cả hai phía, vd {shared[:3].tolist()}")


def assert_time_order(groups, times, train, val) -> None:
    """Mọi group của train phải bắt đầu không muộn hơn group sớm nhất của val."""
    groups, times = np.asarray(groups), np.asarray(times, dtype=float)
    _, inv = _group_index(groups)
    start = np.full(inv.max() + 1, np.inf)
    np.minimum.at(start, inv, times)
    if start[inv[train]].max() > start[inv[val]].min():
        raise AssertionError("có group train bắt đầu sau group validation")
