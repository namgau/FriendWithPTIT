"""Fit and freeze the T4 PCA projection on DailyDialog only.

The fitted PCA is an immutable preprocessing artifact.  Downstream datasets
(E1/E2/G1/H1/H2) must only call :func:`transform_frozen`; they must never
refit it.  The command line accepts a local DailyDialog export so the fit is
auditable without relying on a mutable network dataset at run time.

Examples::

    python -m src.m3_health.fit_pca --input data/interim/daily_dialog
    python -m src.m3_health.fit_pca --hf-dataset daily_dialog
"""
from __future__ import annotations

import argparse
import hashlib
import inspect
import json
import os
import tempfile
from pathlib import Path
from typing import Callable, Iterable, Sequence

import joblib
import numpy as np
import pandas as pd
from sklearn.decomposition import PCA

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARTIFACT = ROOT / "models" / "m3" / "t4" / "pca.pkl"
DEFAULT_METADATA = ROOT / "models" / "m3" / "t4" / "metadata.json"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def texts_hash(texts: Iterable[str]) -> str:
    """Content hash with explicit lengths, so adjacent texts cannot collide."""
    digest = hashlib.sha256()
    for text in texts:
        raw = str(text).encode("utf-8")
        digest.update(len(raw).to_bytes(8, "big"))
        digest.update(raw)
    return digest.hexdigest()


def _flatten_text_column(values: Iterable[object]) -> list[str]:
    texts: list[str] = []
    for value in values:
        if isinstance(value, (list, tuple, np.ndarray)):
            texts.extend(str(item) for item in value if item is not None)
        elif value is not None and not pd.isna(value):
            texts.append(str(value))
    return texts


def _read_table(path: Path) -> pd.DataFrame:
    suffix = path.suffix.lower()
    if suffix in {".parquet", ".pq"}:
        return pd.read_parquet(path)
    if suffix in {".jsonl", ".ndjson"}:
        return pd.read_json(path, lines=True)
    if suffix == ".json":
        return pd.read_json(path)
    if suffix in {".csv", ".tsv"}:
        return pd.read_csv(path, sep="\t" if suffix == ".tsv" else ",")
    raise ValueError(f"Unsupported DailyDialog file type: {path}")


def load_local_split(path: str | Path, split: str, text_column: str = "dialog") -> list[str]:
    """Load and flatten a DailyDialog split from a file or split directory.

    A directory may contain ``train.parquet`` / ``validation.jsonl`` (and the
    other supported extensions).  A single table may instead contain a
    ``split`` column.  Both the common ``dialog`` list column and a scalar
    ``text`` column are supported.
    """
    source = Path(path)
    if source.is_dir():
        matches = [
            candidate
            for suffix in ("parquet", "jsonl", "json", "csv", "tsv")
            if (candidate := source / f"{split}.{suffix}").exists()
        ]
        matches.extend(source.rglob(f"dialogues_{split}.txt"))
        if len(matches) != 1:
            raise FileNotFoundError(
                f"Expected one table or dialogues_{split}.txt in {source}; found {matches}"
            )
        source = matches[0]
    if not source.exists():
        raise FileNotFoundError(source)
    if source.suffix.lower() == ".txt":
        texts = [
            utterance.strip()
            for line in source.read_text(encoding="utf-8").splitlines()
            for utterance in line.split("__eou__")
            if utterance.strip()
        ]
        if not texts:
            raise ValueError(f"DailyDialog split {split!r} contains no text")
        return texts
    frame = _read_table(source)
    if "split" in frame.columns:
        frame = frame[frame["split"].astype(str) == split]
    column = text_column if text_column in frame.columns else "text"
    if column not in frame.columns:
        raise KeyError(f"Neither {text_column!r} nor 'text' exists in {source}")
    texts = _flatten_text_column(frame[column])
    if not texts:
        raise ValueError(f"DailyDialog split {split!r} contains no text")
    return texts


def load_huggingface_split(dataset_name: str, split: str, text_column: str = "dialog") -> list[str]:
    """Load DailyDialog through ``datasets`` when explicitly requested."""
    try:
        from datasets import load_dataset
    except ImportError as exc:  # optional dependency; local input is preferred
        raise RuntimeError("Install `datasets` or pass --input with a local export") from exc
    dataset = load_dataset(dataset_name, split=split)
    column = text_column if text_column in dataset.column_names else "text"
    if column not in dataset.column_names:
        raise KeyError(f"Neither {text_column!r} nor 'text' exists in {dataset_name}:{split}")
    texts = _flatten_text_column(dataset[column])
    if not texts:
        raise ValueError(f"DailyDialog split {split!r} contains no text")
    return texts


def _as_embedding_matrix(values: np.ndarray, name: str) -> np.ndarray:
    matrix = np.asarray(values, dtype=np.float32)
    if matrix.ndim != 2 or not matrix.shape[0] or not matrix.shape[1]:
        raise ValueError(f"{name} embeddings must be a non-empty 2-D matrix")
    if not np.isfinite(matrix).all():
        raise ValueError(f"{name} embeddings contain NaN or infinity")
    return matrix


def fit_projection(
    train_embeddings: np.ndarray,
    n_components: int = 32,
    seed: int = 42,
) -> PCA:
    """Fit PCA once on DailyDialog train embeddings."""
    train = _as_embedding_matrix(train_embeddings, "train")
    maximum = min(train.shape)
    if not 1 <= n_components <= maximum:
        raise ValueError(f"n_components={n_components} exceeds min(train shape)={maximum}")
    projection = PCA(n_components=n_components, whiten=False, svd_solver="auto", random_state=seed)
    projection.fit(train)
    return projection


def projection_metrics(projection: PCA, embeddings: np.ndarray) -> dict[str, float]:
    """Report train-fitted retained variance and validation reconstruction RMSE."""
    matrix = _as_embedding_matrix(embeddings, "validation")
    if matrix.shape[1] != projection.n_features_in_:
        raise ValueError(
            f"Embedding width {matrix.shape[1]} does not match PCA width {projection.n_features_in_}"
        )
    reduced = projection.transform(matrix)
    reconstructed = projection.inverse_transform(reduced)
    return {
        "train_explained_variance_ratio": float(np.sum(projection.explained_variance_ratio_)),
        "validation_reconstruction_rmse": float(np.sqrt(np.mean((matrix - reconstructed) ** 2))),
    }


def save_frozen(
    projection: PCA,
    artifact_path: str | Path,
    metadata_path: str | Path,
    metadata: dict,
) -> dict:
    """Atomically replace the binary artifact and its self-verifying metadata."""
    artifact = Path(artifact_path)
    metadata_file = Path(metadata_path)
    artifact.parent.mkdir(parents=True, exist_ok=True)
    metadata_file.parent.mkdir(parents=True, exist_ok=True)

    fd, temporary_name = tempfile.mkstemp(prefix=f".{artifact.name}.", suffix=".tmp", dir=artifact.parent)
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        joblib.dump(projection, temporary)
        os.replace(temporary, artifact)
    finally:
        temporary.unlink(missing_ok=True)

    complete = {
        **metadata,
        "artifact": artifact.name,
        "artifact_sha256": _sha256_file(artifact),
        "pca": {
            "n_components": int(projection.n_components_),
            "input_dim": int(projection.n_features_in_),
            "whiten": bool(projection.whiten),
            "explained_variance_ratio": float(np.sum(projection.explained_variance_ratio_)),
        },
    }
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{metadata_file.name}.", suffix=".tmp", dir=metadata_file.parent
    )
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        temporary.write_text(json.dumps(complete, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, metadata_file)
    finally:
        temporary.unlink(missing_ok=True)
    return complete


def load_frozen(
    artifact_path: str | Path = DEFAULT_ARTIFACT,
    metadata_path: str | Path = DEFAULT_METADATA,
    verify_checksum: bool = True,
) -> tuple[PCA, dict]:
    """Load the frozen PCA and reject a mismatched/corrupt artifact."""
    artifact, metadata_file = Path(artifact_path), Path(metadata_path)
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    if verify_checksum and metadata.get("artifact_sha256") != _sha256_file(artifact):
        raise ValueError("PCA artifact checksum does not match metadata")
    projection = joblib.load(artifact)
    if not isinstance(projection, PCA):
        raise TypeError(f"Expected sklearn PCA, got {type(projection).__name__}")
    expected = metadata.get("pca", {})
    if expected.get("input_dim") != int(projection.n_features_in_):
        raise ValueError("PCA input dimension does not match metadata")
    if expected.get("n_components") != int(projection.n_components_):
        raise ValueError("PCA component count does not match metadata")
    return projection, metadata


def transform_frozen(
    embeddings: np.ndarray,
    artifact_path: str | Path = DEFAULT_ARTIFACT,
    metadata_path: str | Path = DEFAULT_METADATA,
) -> np.ndarray:
    """Apply the frozen projection.  This function never calls ``fit``."""
    projection, _ = load_frozen(artifact_path, metadata_path)
    matrix = _as_embedding_matrix(embeddings, "input")
    return projection.transform(matrix).astype(np.float32, copy=False)


def fit_from_texts(
    train_texts: Sequence[str],
    validation_texts: Sequence[str],
    encoder: Callable[[Sequence[str]], np.ndarray],
    artifact_path: str | Path = DEFAULT_ARTIFACT,
    metadata_path: str | Path = DEFAULT_METADATA,
    n_components: int = 32,
    seed: int = 42,
    encoder_metadata: dict | None = None,
) -> dict:
    """Encode DailyDialog, fit on train only, evaluate validation, then freeze."""
    train_embeddings = _as_embedding_matrix(encoder(train_texts), "train")
    validation_embeddings = _as_embedding_matrix(encoder(validation_texts), "validation")
    if train_embeddings.shape[1] != validation_embeddings.shape[1]:
        raise ValueError("Train and validation embedding widths differ")
    projection = fit_projection(train_embeddings, n_components=n_components, seed=seed)
    metrics = projection_metrics(projection, validation_embeddings)
    metadata = {
        "schema_version": 1,
        "status": "frozen",
        "fit_dataset": "DailyDialog",
        "fit_split": "train",
        "validation_split": "validation",
        "seed": seed,
        "encoder": encoder_metadata or {},
        "data": {
            "n_train_utterances": len(train_texts),
            "n_validation_utterances": len(validation_texts),
            "train_text_sha256": texts_hash(train_texts),
            "validation_text_sha256": texts_hash(validation_texts),
        },
        "evaluation": metrics,
    }
    return save_frozen(projection, artifact_path, metadata_path, metadata)


def _project_encoder(
    config_path: Path | None, batch_size: int, device: str
) -> tuple[Callable[[Sequence[str]], np.ndarray], dict]:
    """Construct the project's configured encoder without coupling PCA to its backend."""
    from src.m3_health.embeddings import load_embedding_config, make_encoder

    config = load_embedding_config(config_path) if config_path is not None else load_embedding_config()
    selected = config["selected_model"]
    config["models"][selected]["device"] = device
    config["models"][selected]["batch_size"] = batch_size
    if device == "cpu":
        config["models"][selected]["compute_dtype"] = "float32"
    runtime = make_encoder(config)
    method = runtime.encode
    supported = inspect.signature(method).parameters

    def encode(texts: Sequence[str]) -> np.ndarray:
        kwargs = {}
        if "batch_size" in supported:
            kwargs["batch_size"] = batch_size
        if "device" in supported:
            kwargs["device"] = device
        return method(list(texts), **kwargs)

    metadata_method = getattr(runtime, "metadata", None)
    metadata = metadata_method(config) if callable(metadata_method) else {}
    return encode, metadata


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", type=Path, help="DailyDialog file or directory with split files")
    source.add_argument("--hf-dataset", help="Hugging Face dataset name, e.g. daily_dialog")
    parser.add_argument("--train-split", default="train")
    parser.add_argument("--validation-split", default="validation")
    parser.add_argument("--text-column", default="dialog")
    parser.add_argument("--embedding-config", type=Path, default=ROOT / "configs" / "embedding.yaml")
    parser.add_argument("--n-components", type=int, default=32)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--device", default="auto")
    parser.add_argument("--artifact", type=Path, default=DEFAULT_ARTIFACT)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    loader = load_local_split if args.input is not None else load_huggingface_split
    source = args.input if args.input is not None else args.hf_dataset
    train = loader(source, args.train_split, args.text_column)
    validation = loader(source, args.validation_split, args.text_column)
    encoder, runtime_meta = _project_encoder(args.embedding_config, args.batch_size, args.device)
    try:
        display_config_path = str(args.embedding_config.resolve().relative_to(ROOT.resolve()))
    except ValueError:
        display_config_path = str(args.embedding_config)
    encoder_meta = {
        **runtime_meta,
        "config_path": display_config_path,
        "config_sha256": _sha256_file(args.embedding_config) if args.embedding_config.exists() else None,
    }
    metadata = fit_from_texts(
        train,
        validation,
        encoder,
        artifact_path=args.artifact,
        metadata_path=args.metadata,
        n_components=args.n_components,
        seed=args.seed,
        encoder_metadata=encoder_meta,
    )
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
