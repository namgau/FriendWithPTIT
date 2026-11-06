import numpy as np
import pandas as pd
import pytest

from src.m3_health.features_t4 import extract_t4_features


def _messages(n=12):
    rows = []
    embeddings = {}
    for conversation, offset in [("a", 0.0), ("b", 10.0)]:
        for turn in range(n):
            key = (conversation, turn)
            rows.append({
                "conversation_id": conversation,
                "turn_idx": turn,
                "sender_id": f"{conversation}{turn % 2}",
                "text": f"message {conversation} {turn}",
            })
            embeddings[key] = np.array([1.0 + offset, float(turn + 1), 0.5], dtype=np.float32)
    return pd.DataFrame(rows), embeddings


def test_future_messages_do_not_change_features():
    messages, embeddings = _messages()
    samples = pd.DataFrame({"conversation_id": ["a", "b"], "turn_idx": [6, 8]}, index=[9, 3])
    before = extract_t4_features(messages, samples, embeddings)
    changed = dict(embeddings)
    for key in list(changed):
        if key[1] > 8:
            changed[key] = np.array([999.0, -999.0, 500.0])
    after = extract_t4_features(messages, samples, changed)
    pd.testing.assert_frame_equal(before, after)
    assert list(before.index) == [9, 3]


def test_conversations_are_isolated():
    messages, embeddings = _messages()
    sample = pd.DataFrame({"conversation_id": ["a"], "turn_idx": [8]})
    before = extract_t4_features(messages, sample, embeddings)
    changed = dict(embeddings)
    for key in list(changed):
        if key[0] == "b":
            changed[key] = np.array([-1000.0, 3.0, 50.0])
    pd.testing.assert_frame_equal(before, extract_t4_features(messages, sample, changed))


def test_keyed_embeddings_survive_independent_row_shuffle():
    messages, embeddings = _messages()
    samples = pd.DataFrame({"conversation_id": ["b", "a"], "turn_idx": [9, 7]})
    expected = extract_t4_features(messages, samples, embeddings)
    embedding_frame = pd.DataFrame([
        {"conversation_id": key[0], "turn_idx": key[1], "embedding": value}
        for key, value in embeddings.items()
    ]).sample(frac=1, random_state=5)
    shuffled_messages = messages.sample(frac=1, random_state=8)
    actual = extract_t4_features(shuffled_messages, samples, embedding_frame)
    pd.testing.assert_frame_equal(expected, actual)


class _Projection:
    def __init__(self):
        self.fit_called = False

    def fit(self, values):
        self.fit_called = True
        raise AssertionError("extractor must not fit PCA")

    def transform(self, values):
        return np.asarray(values)[:, :2]


def test_short_empty_conversation_and_frozen_pca_have_finite_output():
    messages = pd.DataFrame([
        {"conversation_id": "x", "turn_idx": 0, "sender_id": None, "text": ""},
    ])
    samples = pd.DataFrame({"conversation_id": ["x"], "turn_idx": [0]}, index=[42])
    pca = _Projection()
    features = extract_t4_features(messages, samples, np.zeros((1, 3)), pca=pca)
    assert not pca.fit_called
    assert features.index.tolist() == [42]
    assert features["t4_missing_history"].iloc[0] == 1
    assert features["t4_no_reply_pairs"].iloc[0] == 1
    assert features["t4_empty_share"].iloc[0] == 1
    assert features["t4_current_empty"].iloc[0] == 1
    assert {"t4_pc_00", "t4_pc_01"}.issubset(features.columns)
    assert np.isfinite(features.to_numpy()).all()


def test_array_embeddings_are_positional_and_alignment_is_validated():
    messages, embeddings = _messages(n=3)
    matrix = np.stack([embeddings[key] for key in messages[["conversation_id", "turn_idx"]].itertuples(index=False, name=None)])
    samples = pd.DataFrame({"conversation_id": ["a"], "turn_idx": [2]})
    features = extract_t4_features(messages, samples, matrix)
    assert features["t4_no_reply_pairs"].iloc[0] == 0
    with pytest.raises(ValueError, match="shape"):
        extract_t4_features(messages, samples, matrix[:-1])
    with pytest.raises(KeyError, match="missing embedding"):
        extract_t4_features(messages, samples, {("a", 0): np.ones(3)})
