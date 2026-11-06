import json

import joblib
import numpy as np
import pandas as pd
import pytest

from src.m3_health.fit_pca import (
    fit_from_texts,
    fit_projection,
    load_frozen,
    load_local_split,
    projection_metrics,
    transform_frozen,
)


def _encoder(texts):
    rows = []
    for text in texts:
        value = sum(text.encode("utf-8"))
        rows.append([len(text), value % 17, value % 13, value % 11, value % 7, value % 5])
    return np.asarray(rows, dtype=np.float32)


def test_fit_uses_train_and_load_transform_never_refits(tmp_path):
    train = [f"train utterance {i}" for i in range(20)]
    validation = [f"validation utterance {i}" for i in range(5)]
    artifact, metadata_path = tmp_path / "pca.pkl", tmp_path / "metadata.json"
    metadata = fit_from_texts(
        train,
        validation,
        _encoder,
        artifact,
        metadata_path,
        n_components=3,
        seed=42,
        encoder_metadata={"model_id": "fake/model", "revision": "abc123"},
    )

    projection, loaded_metadata = load_frozen(artifact, metadata_path)
    before_components = projection.components_.copy()
    output = transform_frozen(_encoder(["new one", "new two"]), artifact, metadata_path)
    reloaded, _ = load_frozen(artifact, metadata_path)

    assert output.shape == (2, 3)
    np.testing.assert_array_equal(reloaded.components_, before_components)
    assert metadata == loaded_metadata
    assert metadata["fit_split"] == "train"
    assert metadata["validation_split"] == "validation"
    assert metadata["encoder"]["revision"] == "abc123"
    assert metadata["data"]["n_train_utterances"] == 20
    assert metadata["pca"]["whiten"] is False


def test_checksum_rejects_modified_artifact(tmp_path):
    artifact, metadata_path = tmp_path / "pca.pkl", tmp_path / "metadata.json"
    fit_from_texts(
        [f"train {i}" for i in range(10)],
        [f"validation {i}" for i in range(3)],
        _encoder,
        artifact,
        metadata_path,
        n_components=2,
    )
    artifact.write_bytes(artifact.read_bytes() + b"changed")
    with pytest.raises(ValueError, match="checksum"):
        load_frozen(artifact, metadata_path)


def test_metadata_dimension_mismatch_is_rejected(tmp_path):
    projection = fit_projection(np.arange(60, dtype=np.float32).reshape(10, 6), n_components=2)
    artifact, metadata_path = tmp_path / "pca.pkl", tmp_path / "metadata.json"
    joblib.dump(projection, artifact)
    import hashlib

    checksum = hashlib.sha256(artifact.read_bytes()).hexdigest()
    metadata_path.write_text(
        json.dumps(
            {
                "artifact_sha256": checksum,
                "pca": {"input_dim": 999, "n_components": 2},
            }
        ),
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="dimension"):
        load_frozen(artifact, metadata_path)


def test_projection_validation_and_metrics():
    rng = np.random.default_rng(42)
    train = rng.normal(size=(30, 8)).astype(np.float32)
    validation = rng.normal(size=(7, 8)).astype(np.float32)
    projection = fit_projection(train, n_components=4, seed=42)
    metrics = projection_metrics(projection, validation)
    assert 0 < metrics["train_explained_variance_ratio"] <= 1
    assert metrics["validation_reconstruction_rmse"] >= 0
    with pytest.raises(ValueError, match="exceeds"):
        fit_projection(train[:3], n_components=4)
    with pytest.raises(ValueError, match="NaN"):
        fit_projection(np.array([[1.0, np.nan], [2.0, 3.0]]), n_components=1)


def test_local_daily_dialog_split_directory_and_single_table(tmp_path):
    split_dir = tmp_path / "splits"
    split_dir.mkdir()
    pd.DataFrame({"dialog": [["hello", "hi"], ["bye"]]}).to_json(
        split_dir / "train.jsonl", orient="records", lines=True
    )
    assert load_local_split(split_dir, "train") == ["hello", "hi", "bye"]

    table = tmp_path / "all.jsonl"
    pd.DataFrame(
        {"split": ["train", "validation"], "text": ["train text", "validation text"]}
    ).to_json(table, orient="records", lines=True)
    assert load_local_split(table, "validation") == ["validation text"]


def test_local_daily_dialog_official_text_format(tmp_path):
    nested = tmp_path / "train" / "train"
    nested.mkdir(parents=True)
    (nested / "dialogues_train.txt").write_text(
        "hello __eou__ hi there __eou__\nbye __eou__\n", encoding="utf-8"
    )
    assert load_local_split(tmp_path, "train") == ["hello", "hi there", "bye"]
