"""T4 multilingual semantic features computed from cached message embeddings.

The extractor deliberately accepts precomputed embeddings.  Encoding millions of
messages belongs to the cache job; feature extraction must be deterministic and
must never load a transformer implicitly.
"""
from __future__ import annotations

from collections.abc import Mapping

import numpy as np
import pandas as pd

KEY_COLUMNS = ["conversation_id", "turn_idx"]
BASE_COLUMNS = [
    "t4_reply_cos_last",
    "t4_reply_cos_mean",
    "t4_reply_cos_min",
    "t4_reply_cos_slope",
    "t4_topic_drift",
    "t4_topic_novelty",
    "t4_missing_history",
    "t4_no_reply_pairs",
    "t4_empty_share",
    "t4_current_empty",
]


def _as_vector(value) -> np.ndarray:
    vector = np.asarray(value, dtype=np.float32)
    if vector.ndim != 1:
        raise ValueError("each message embedding must be a one-dimensional vector")
    return vector


def _embedding_matrix(messages: pd.DataFrame, embeddings) -> np.ndarray:
    """Align embeddings to the *current* row order of ``messages``.

    Arrays are positional.  A mapping uses ``(conversation_id, turn_idx)`` keys.
    A DataFrame may contain an ``embedding`` object column or numeric vector
    columns in addition to the two key columns.
    """
    if isinstance(embeddings, np.ndarray):
        matrix = np.asarray(embeddings, dtype=np.float32)
        if matrix.ndim != 2 or len(matrix) != len(messages):
            raise ValueError("embedding array must have shape (len(messages), dimension)")
        return matrix

    keys = list(messages[KEY_COLUMNS].itertuples(index=False, name=None))
    if isinstance(embeddings, Mapping):
        missing = [key for key in keys if key not in embeddings]
        if missing:
            raise KeyError(f"missing embedding for message {missing[0]!r}")
        vectors = [_as_vector(embeddings[key]) for key in keys]
    elif isinstance(embeddings, pd.DataFrame):
        if not set(KEY_COLUMNS).issubset(embeddings.columns):
            raise ValueError("embedding DataFrame must contain conversation_id and turn_idx")
        if embeddings.duplicated(KEY_COLUMNS).any():
            raise ValueError("embedding keys must be unique")
        if "embedding" in embeddings.columns:
            value_columns = ["embedding"]
        else:
            value_columns = [column for column in embeddings.columns if column not in KEY_COLUMNS]
            if not value_columns:
                raise ValueError("embedding DataFrame has no vector columns")
        aligned = messages[KEY_COLUMNS].merge(
            embeddings[KEY_COLUMNS + value_columns], on=KEY_COLUMNS, how="left", validate="one_to_one", sort=False
        )
        if len(value_columns) == 1 and value_columns[0] == "embedding":
            if aligned["embedding"].isna().any():
                raise KeyError("embedding DataFrame does not cover every message")
            vectors = [_as_vector(value) for value in aligned["embedding"]]
        else:
            matrix = aligned[value_columns].to_numpy(dtype=np.float32)
            if not np.isfinite(matrix).all():
                raise KeyError("embedding DataFrame does not cover every message")
            return matrix
    else:
        raise TypeError("embeddings must be a numpy array, mapping, or DataFrame")

    dimensions = {len(vector) for vector in vectors}
    if len(dimensions) != 1:
        raise ValueError("all message embeddings must have the same dimension")
    matrix = np.stack(vectors)
    if not np.isfinite(matrix).all():
        raise ValueError("message embeddings must be finite")
    return matrix


def _cosine(left: np.ndarray, right: np.ndarray) -> float:
    denominator = float(np.linalg.norm(left) * np.linalg.norm(right))
    return float(np.dot(left, right) / denominator) if denominator > 0 else 0.0


def _centroid(vectors: np.ndarray, valid: np.ndarray) -> np.ndarray | None:
    return vectors[valid].mean(axis=0) if valid.any() else None


def _slope(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    y = np.asarray(values, dtype=float)
    x = np.arange(len(y), dtype=float)
    x -= x.mean()
    denominator = float(np.dot(x, x))
    return float(np.dot(x, y - y.mean()) / denominator) if denominator else 0.0


def _sample_features(prefix: pd.DataFrame, window: int, recent: int) -> tuple[dict, np.ndarray]:
    current_window = prefix.tail(window)
    vectors = np.stack(current_window["_embedding"].to_numpy())
    texts = current_window["text"].fillna("").astype(str).str.strip().to_numpy()
    norms = np.linalg.norm(vectors, axis=1)
    valid = (texts != "") & (norms > 0)

    reply_scores: list[float] = []
    senders = current_window["sender_id"].to_numpy()
    for index in range(1, len(current_window)):
        cross_sender = pd.notna(senders[index - 1]) and pd.notna(senders[index]) and senders[index - 1] != senders[index]
        if cross_sender and valid[index - 1] and valid[index]:
            reply_scores.append(_cosine(vectors[index - 1], vectors[index]))

    recent_start = max(len(vectors) - recent, 0)
    previous_start = max(recent_start - recent, 0)
    recent_centroid = _centroid(vectors[recent_start:], valid[recent_start:])
    previous_centroid = _centroid(vectors[previous_start:recent_start], valid[previous_start:recent_start])
    drift = 0.0 if recent_centroid is None or previous_centroid is None else 1.0 - _cosine(recent_centroid, previous_centroid)

    history_centroid = _centroid(vectors[:-1], valid[:-1])
    novelty = 0.0 if history_centroid is None or not valid[-1] else 1.0 - _cosine(vectors[-1], history_centroid)
    mean_embedding = _centroid(vectors, valid)
    if mean_embedding is None:
        mean_embedding = np.zeros(vectors.shape[1], dtype=np.float32)

    row = {
        "t4_reply_cos_last": reply_scores[-1] if reply_scores else 0.0,
        "t4_reply_cos_mean": float(np.mean(reply_scores)) if reply_scores else 0.0,
        "t4_reply_cos_min": float(np.min(reply_scores)) if reply_scores else 0.0,
        "t4_reply_cos_slope": _slope(reply_scores),
        "t4_topic_drift": drift,
        "t4_topic_novelty": novelty,
        "t4_missing_history": float(len(current_window) < 2),
        "t4_no_reply_pairs": float(not reply_scores),
        "t4_empty_share": float(1.0 - valid.mean()),
        "t4_current_empty": float(not valid[-1]),
    }
    return row, mean_embedding


def extract_t4_features(
    messages: pd.DataFrame,
    samples: pd.DataFrame,
    embeddings,
    *,
    pca=None,
    window: int = 10,
    recent: int = 5,
) -> pd.DataFrame:
    """Return semantic features for each ``(conversation_id, turn_idx)`` sample.

    Only messages at or before the sample's ``turn_idx`` are inspected.  The
    result preserves both the order and index of ``samples``.  ``pca`` is an
    already-fitted object exposing ``transform``; this function never fits it.
    """
    required_messages = {*KEY_COLUMNS, "sender_id", "text"}
    required_samples = set(KEY_COLUMNS)
    if missing := required_messages.difference(messages.columns):
        raise ValueError(f"messages missing columns: {sorted(missing)}")
    if missing := required_samples.difference(samples.columns):
        raise ValueError(f"samples missing columns: {sorted(missing)}")
    if window < 1 or recent < 1:
        raise ValueError("window and recent must be positive")
    if messages.duplicated(KEY_COLUMNS).any():
        raise ValueError("message keys must be unique")

    frame = messages.copy()
    frame["_embedding"] = list(_embedding_matrix(messages, embeddings))
    frame = frame.sort_values(KEY_COLUMNS, kind="stable")
    grouped = {key: value for key, value in frame.groupby("conversation_id", sort=False)}

    rows: list[dict] = []
    means: list[np.ndarray] = []
    for conversation_id, turn_idx in samples[KEY_COLUMNS].itertuples(index=False, name=None):
        if conversation_id not in grouped:
            raise KeyError(f"unknown conversation_id {conversation_id!r}")
        conversation = grouped[conversation_id]
        prefix = conversation[conversation["turn_idx"] <= turn_idx]
        if prefix.empty or not (prefix["turn_idx"] == turn_idx).any():
            raise KeyError(f"sample message {(conversation_id, turn_idx)!r} is missing")
        row, mean_embedding = _sample_features(prefix, window, recent)
        rows.append(row)
        means.append(mean_embedding)

    result = pd.DataFrame(rows, columns=BASE_COLUMNS, index=samples.index, dtype=float)
    if pca is not None and means:
        projected = np.asarray(pca.transform(np.stack(means)), dtype=float)
        if projected.ndim != 2 or len(projected) != len(samples):
            raise ValueError("PCA transform returned an invalid shape")
        projected_frame = pd.DataFrame(
            projected,
            columns=[f"t4_pc_{index:02d}" for index in range(projected.shape[1])],
            index=samples.index,
        )
        result = pd.concat([result, projected_frame], axis=1)
    if not np.isfinite(result.to_numpy()).all():
        raise ValueError("T4 feature extraction produced non-finite values")
    return result
