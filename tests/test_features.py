"""Đặc trưng Head A không được nhìn tin sau mốc t."""
import numpy as np
import pandas as pd

from src.m3_health.features import extract_features


def _msgs(n=12, seed=0):
    rng = np.random.default_rng(seed)
    rows = []
    for c in ["a", "b"]:
        ts = np.cumsum(rng.exponential(600, n))
        for i in range(n):
            rows.append({"conversation_id": c, "turn_idx": i, "sender_id": f"{c}{i % 2}", "timestamp_s": ts[i],
                         "text": "ok" if i % 3 == 0 else "bạn đi học chưa?" * (i % 4 + 1)})
    return pd.DataFrame(rows)


def test_no_future_leak():
    msgs = _msgs()
    samples = pd.DataFrame({"conversation_id": ["a", "a", "b"], "turn_idx": [5, 7, 9]})
    f1 = extract_features(msgs, samples)
    later = msgs.copy()
    future = later["turn_idx"] > 9
    later.loc[future, "text"] = "xyz " * 50
    later.loc[future, "timestamp_s"] += 1e6
    later.loc[future, "sender_id"] = "someone"
    pd.testing.assert_frame_equal(f1, extract_features(later, samples))


def test_values():
    msgs = _msgs()
    f = extract_features(msgs, pd.DataFrame({"conversation_id": ["a"], "turn_idx": [6]}))
    a = msgs[msgs.conversation_id == "a"]
    assert f["n_msgs"].iloc[0] == 7
    assert np.isclose(f["gap_last"].iloc[0], np.log1p(a.timestamp_s.iloc[6] - a.timestamp_s.iloc[5]))
    assert f["short_last"].iloc[0] == 1  # tin 6 là "ok"
    assert f["double_text"].iloc[0] == 0  # 2 bên nhắn xen kẽ
    assert not f.isna().any().any()
