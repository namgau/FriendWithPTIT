"""Đặc trưng Head B: G1 chỉ dùng tiền tố, H1/H2 dùng một bình luận."""
import numpy as np
import pandas as pd

from src.m3_health.features_b import NUMERIC_COLUMNS, build_g1, build_g1_prefixes, build_single


def _g1_messages():
    return pd.DataFrame({
        "conversation_id": ["bad"] * 4 + ["ok"] * 3,
        "group_id": ["pair_1"] * 4 + ["pair_2"] * 3,
        "turn_idx": [0, 1, 2, 3, 0, 1, 2],
        "sender_id": ["u0", "u1", "u0", "u1", "v0", "v1", "v0"],
        "timestamp_s": [0.0, 10.0, 20.0, 30.0, 0.0, 5.0, 10.0],
        "text": ["hello", "how are you?", "ATTACK_MARKER", "FUTURE_MARKER",
                 "hello", "goodbye", "FUTURE_CALM"],
        "is_attack": [False, False, True, False, False, False, False],
    })


def _g1_labels():
    return pd.DataFrame({
        "conversation_id": ["bad", "ok"],
        "group_id": ["pair_1", "pair_2"],
        "pair_id": ["ok", "bad"],
        "derailed": [1, 0],
        "prefix_len": [2, 2],
    })


def test_g1_only_sees_prefix_and_drops_future_text():
    out = build_g1(_g1_messages(), _g1_labels()).set_index("conversation_id")
    assert out.loc["bad", "text"] == "hello how are you?"
    assert out.loc["bad", "last_text"] == "how are you?"
    assert out.loc["bad", "n_messages"] == 2
    assert out.loc["bad", "n_senders"] == 2
    assert "ATTACK_MARKER" not in out.loc["bad", "text"]
    assert "FUTURE_MARKER" not in out.loc["bad", "text"]
    assert "FUTURE_CALM" not in out.loc["ok", "text"]
    assert out.loc["bad", "y"] == 1 and out.loc["ok", "y"] == 0


def test_g1_future_edits_do_not_change_features():
    original = build_g1(_g1_messages(), _g1_labels())
    changed = _g1_messages()
    changed.loc[changed["turn_idx"] >= changed["conversation_id"].map({"bad": 2, "ok": 2}), "text"] = "CHANGED"
    changed.loc[changed["turn_idx"] >= 2, "timestamp_s"] += 1e6
    edited = build_g1(changed, _g1_labels())
    columns = ["conversation_id", "text", "last_text", *NUMERIC_COLUMNS]
    pd.testing.assert_frame_equal(original[columns], edited[columns])


def test_g1_prefix_grid_never_includes_attack_or_future():
    labels = _g1_labels().assign(attack_turn_idx=[2, -1], n_turns=[4, 3])
    out = build_g1_prefixes(_g1_messages(), labels).set_index(["conversation_id", "eval_len"])
    bad = out.loc["bad"]
    assert bad.index.tolist() == [1, 2]
    assert bad.loc[1, "text"] == "hello"
    assert bad.loc[2, "text"] == "hello how are you?"
    assert bad["n_messages"].tolist() == [1, 2]
    assert "ATTACK_MARKER" not in " ".join(bad["text"])
    assert "FUTURE_MARKER" not in " ".join(bad["text"])
    ok = out.loc["ok"]
    assert ok.index.tolist() == [1, 2]
    assert "FUTURE_CALM" not in " ".join(ok["text"])


def test_single_comment_features():
    messages = pd.DataFrame({
        "conversation_id": ["x"],
        "text": ["You are stupid and an idiot?"],
        "group_id": ["g"],
    })
    labels = pd.DataFrame({"conversation_id": ["x"], "group_id": ["g"], "toxic": [1]})
    out = build_single(messages, labels)
    row = out.iloc[0]
    assert row["n_messages"] == 1 and row["last_question"] == 1
    assert row["toxic_hits"] == 2 and row["last_toxic_hits"] == 2
    assert row["y"] == 1
    assert not out[NUMERIC_COLUMNS].isna().any().any()


def test_empty_prefix_is_safe():
    messages = pd.DataFrame({
        "conversation_id": ["empty"],
        "group_id": ["g"],
        "turn_idx": [0],
        "sender_id": ["u"],
        "timestamp_s": [0.0],
        "text": ["ATTACK"],
    })
    labels = pd.DataFrame({
        "conversation_id": ["empty"],
        "group_id": ["g"],
        "derailed": [1],
        "prefix_len": [0],
    })
    out = build_g1(messages, labels)
    assert out.loc[0, "text"] == "" and out.loc[0, "n_messages"] == 0
    assert not pd.isna(out.loc[0, "span_log"])
    assert np.isfinite(out.loc[0, "span_log"])
