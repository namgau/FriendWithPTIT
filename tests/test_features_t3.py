import numpy as np
import pandas as pd
import pytest

from src.m3_health.features_t3 import T3_COLUMNS, extract_t3_features


def messages(texts, senders=None, turns=None, cid="c"):
    n = len(texts)
    return pd.DataFrame({"conversation_id": [cid] * n, "turn_idx": turns or list(range(n)),
                         "sender_id": senders if senders is not None else ["a", "b"] * (n // 2) + (["a"] if n % 2 else []),
                         "text": texts})


def sample(turn, cid="c"):
    return pd.DataFrame({"conversation_id": [cid], "turn_idx": [turn]})


def test_known_lsm_jaccard_turns_and_slopes():
    m = messages(["I like tea", "you like tea", "you like tea", "I like tea"], ["a", "b", "b", "a"])
    f = extract_t3_features(m, sample(3)).iloc[0]
    assert f.t3_lsm == pytest.approx(1)
    assert f.t3_mirroring_mean == pytest.approx(0.5)
    assert f.t3_mirroring_slope == pytest.approx(0)
    assert f.t3_turn_length_mean == pytest.approx(4 / 3)
    assert f.t3_turn_change_rate == pytest.approx(2 / 3)
    assert f.t3_lsm_slope == pytest.approx(0)


def test_unbalanced_lsm_and_slope_use_actual_time():
    m = messages(["I I", "you", "tea", "tea", "I tea"], ["a", "b", "a", "b", "a"])
    f = extract_t3_features(m, sample(4)).iloc[0]
    assert f.t3_lsm == pytest.approx(1 - abs(3 / 5 - 1 / 2) / (3 / 5 + 1 / 2 + .0001))
    assert np.isfinite(f.t3_lsm_slope)


def test_future_cannot_affect_features_and_order_preserved():
    m = messages(["I tea", "you tea", "unknown", "you", "I coffee"], turns=[2, 5, 7, 10, 40])
    s = pd.DataFrame({"conversation_id": ["c", "c", "c"], "turn_idx": [10, 5, 10]}, index=[8, 2, 8])
    expected = extract_t3_features(m, s, window=3)
    changed = pd.concat([m.iloc[:4], messages(["bad future"], ["other"], [40])], ignore_index=True)
    pd.testing.assert_frame_equal(expected, extract_t3_features(changed, s, window=3))
    assert list(expected.index) == [8, 2, 8]
    pd.testing.assert_frame_equal(expected.iloc[[0]].reset_index(drop=True), expected.iloc[[2]].reset_index(drop=True))


def test_missing_empty_multisender_and_unknown_sender():
    m = messages(["", None, "I", "you"], ["a", None, "b", "c"])
    f = extract_t3_features(m, sample(3)).iloc[0]
    assert np.isnan(f.t3_lsm) and f.t3_lsm_missing == 1 and f.t3_multi_sender == 1
    assert f.t3_mirroring_mean == 0 and f.t3_mirroring_missing == 0
    assert f.t3_turn_change_rate == 1 and f.t3_turn_length_mean == 1
    f = extract_t3_features(m, sample(1)).iloc[0]
    assert np.isnan(f.t3_turn_change_rate) and f.t3_turn_change_missing == 1
    assert np.isnan(f.t3_mirroring_mean) and f.t3_mirroring_missing == 1


def test_no_function_words_and_one_sided_are_missing_not_perfect():
    m = messages(["tea", "coffee", "tea"], ["a", "b", "b"])
    f = extract_t3_features(m, sample(1)).iloc[0]
    assert np.isnan(f.t3_lsm) and f.t3_lsm_missing == 1
    f = extract_t3_features(m, sample(0)).iloc[0]
    assert np.isnan(f.t3_lsm) and np.isnan(f.t3_lsm_slope)
    assert f.t3_turn_length_mean == 1


def test_vietnamese_unicode_normalization():
    m = messages(["MÌNH nhé", "mi\u0300nh NHE\u0301"], ["a", "b"])
    f = extract_t3_features(m, sample(1), language="vi").iloc[0]
    assert f.t3_lsm == pytest.approx(1)
    assert f.t3_mirroring_mean == pytest.approx(1)


def test_sparse_indices_duplicates_invalid_inputs_and_empty():
    m = messages(["I", "you"], turns=[5, 12])
    assert len(extract_t3_features(m, sample(12))) == 1
    with pytest.raises(KeyError):
        extract_t3_features(m, sample(6))
    with pytest.raises(ValueError, match="unique"):
        extract_t3_features(pd.concat([m, m]), sample(5))
    with pytest.raises(ValueError, match="language"):
        extract_t3_features(m, sample(5), language="fr")
    with pytest.raises(ValueError, match="window"):
        extract_t3_features(m, sample(5), window=0)
    empty = extract_t3_features(m, sample(5).iloc[:0])
    assert empty.empty and list(empty.columns) == T3_COLUMNS
