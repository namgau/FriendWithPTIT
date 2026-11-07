"""Encoder da ngon ngu dung chung cho C1/T4 va cache embedding B8.

Thu vien ``sentence-transformers`` chi duoc import khi khoi tao backend that, de
pipeline bang va unit test van chay duoc khi chua cai Torch/model nang.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Iterator, Mapping, Protocol, Sequence

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = ROOT / "configs" / "embedding.yaml"


class EmbeddingBackend(Protocol):
    dimension: int

    def encode(self, texts: Sequence[str], batch_size: int) -> np.ndarray: ...

    def token_lengths(self, texts: Sequence[str]) -> np.ndarray: ...


def load_embedding_config(path: str | Path = DEFAULT_CONFIG) -> dict:
    try:
        import yaml
    except ImportError as exc:
        raise RuntimeError("can PyYAML de doc configs/embedding.yaml") from exc
    with Path(path).open(encoding="utf-8") as stream:
        cfg = yaml.safe_load(stream)
    if not isinstance(cfg, dict) or "selected_model" not in cfg or "models" not in cfg:
        raise ValueError("embedding config phai co selected_model va models")
    if cfg["selected_model"] not in cfg["models"]:
        raise ValueError("selected_model khong co trong models")
    return cfg


def config_hash(config: Mapping) -> str:
    payload = json.dumps(
        config, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:12]


def selected_model_config(config: Mapping, model_name: str | None = None) -> tuple[str, dict]:
    name = model_name or str(config["selected_model"])
    try:
        model = dict(config["models"][name])
    except KeyError as exc:
        raise KeyError(f"khong co model embedding {name!r}") from exc
    required = {"model_id", "revision", "dimension", "max_length", "batch_size"}
    missing = required.difference(model)
    if missing:
        raise ValueError(f"model {name!r} thieu config: {sorted(missing)}")
    return name, model


class SentenceTransformerBackend:
    """Adapter cho SentenceTransformer, chi sinh dense sentence embedding."""

    def __init__(self, model: Mapping):
        try:
            import torch
            from sentence_transformers import SentenceTransformer
        except ImportError as exc:  # pragma: no cover - tuy thuoc moi truong GPU
            raise RuntimeError(
                "C1 can torch va sentence-transformers; cai dependencies Module 3 truoc khi encode"
            ) from exc

        requested = str(model.get("device", "auto"))
        self.device = "cuda" if requested == "auto" and torch.cuda.is_available() else requested
        if self.device == "auto":
            self.device = "cpu"
        self.model = SentenceTransformer(
            str(model["model_id"]), revision=str(model["revision"]), device=self.device
        )
        self.model.max_seq_length = int(model["max_length"])
        self.dimension = int(model["dimension"])
        dimension_method = getattr(self.model, "get_embedding_dimension", None)
        if dimension_method is None:  # sentence-transformers < 4
            dimension_method = self.model.get_sentence_embedding_dimension
        actual = dimension_method()
        if actual is not None and int(actual) != self.dimension:
            raise ValueError(f"dimension config={self.dimension}, model={actual}")
        if self.device.startswith("cuda") and str(model.get("compute_dtype")) == "float16":
            self.model.half()
        self.parameter_mib = float(
            sum(parameter.numel() * parameter.element_size() for parameter in self.model.parameters())
            / (1024**2)
        )

    def encode(self, texts: Sequence[str], batch_size: int) -> np.ndarray:
        return np.asarray(
            self.model.encode(
                list(texts), batch_size=batch_size, show_progress_bar=False,
                convert_to_numpy=True, normalize_embeddings=False, device=self.device,
            ), dtype=np.float32,
        )

    def token_lengths(self, texts: Sequence[str]) -> np.ndarray:
        encoded = self.model.tokenizer(
            list(texts), add_special_tokens=True, truncation=False, padding=False
        )
        return np.asarray([len(ids) for ids in encoded["input_ids"]], dtype=np.int32)


@dataclass
class EmbeddingEncoder:
    name: str
    model: dict
    normalize: bool = True
    empty_policy: str = "zero"
    cache_dtype: str = "float32"
    backend: EmbeddingBackend | None = None

    def __post_init__(self) -> None:
        if self.empty_policy != "zero":
            raise ValueError("C1 chi ho tro empty_policy=zero")
        try:
            dtype = np.dtype(self.cache_dtype)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"cache_dtype khong hop le: {self.cache_dtype!r}") from exc
        if dtype not in {np.dtype(np.float16), np.dtype(np.float32)}:
            raise ValueError("cache_dtype chi ho tro float16 hoac float32")
        self.cache_dtype = dtype.name

    @property
    def dimension(self) -> int:
        return int(self.model["dimension"])

    @property
    def batch_size(self) -> int:
        return int(self.model["batch_size"])

    def _backend(self) -> EmbeddingBackend:
        if self.backend is None:
            self.backend = SentenceTransformerBackend(self.model)
        if int(self.backend.dimension) != self.dimension:
            raise ValueError("backend tra sai dimension embedding")
        return self.backend

    def encode(self, texts: Sequence[str], batch_size: int | None = None) -> np.ndarray:
        values = ["" if value is None else str(value) for value in texts]
        out = np.zeros((len(values), self.dimension), dtype=np.float32)
        nonempty = [i for i, value in enumerate(values) if value.strip()]
        if not nonempty:
            return out
        encoded = np.asarray(
            self._backend().encode([values[i] for i in nonempty], batch_size or self.batch_size),
            dtype=np.float32,
        )
        if encoded.shape != (len(nonempty), self.dimension):
            raise ValueError(f"backend tra shape {encoded.shape}, can {(len(nonempty), self.dimension)}")
        if not np.isfinite(encoded).all():
            raise ValueError("embedding co NaN/Inf")
        if self.normalize:
            norm = np.linalg.norm(encoded, axis=1, keepdims=True)
            encoded = encoded / np.maximum(norm, np.finfo(np.float32).eps)
        out[np.asarray(nonempty)] = encoded
        return out

    def token_lengths(self, texts: Sequence[str]) -> np.ndarray:
        values = ["" if value is None else str(value) for value in texts]
        if not values:
            return np.empty(0, dtype=np.int32)
        return self._backend().token_lengths(values)

    def metadata(self, config: Mapping | None = None) -> dict:
        metadata = {
            "model_name": self.name, "model_id": self.model["model_id"],
            "revision": self.model["revision"], "dimension": self.dimension,
            "max_length": int(self.model["max_length"]), "normalize": self.normalize,
            "empty_policy": self.empty_policy,
            "config_hash": config_hash(config) if config is not None else None,
        }
        if config is not None and config.get("selection") is not None:
            metadata["selection"] = dict(config["selection"])
        if self.backend is not None and hasattr(self.backend, "parameter_mib"):
            metadata["model_parameter_mib"] = float(self.backend.parameter_mib)
        return metadata


def make_encoder(config: Mapping | None = None, model_name: str | None = None,
                 backend: EmbeddingBackend | None = None) -> EmbeddingEncoder:
    cfg = dict(config or load_embedding_config())
    name, model = selected_model_config(cfg, model_name)
    return EmbeddingEncoder(name=name, model=model, normalize=bool(cfg.get("normalize", True)),
                            empty_policy=str(cfg.get("empty_policy", "zero")),
                            cache_dtype=str(cfg.get("cache_dtype", "float32")), backend=backend)


@lru_cache(maxsize=1)
def _default_encoder() -> EmbeddingEncoder:
    return make_encoder()


def encode_messages(texts: list[str]) -> np.ndarray:
    """Chu ky da chot trong docs/06: giu nguyen thu tu va tra ``(N, D)``."""
    return _default_encoder().encode(texts)


def encode_batches(texts: Sequence[str], encoder: EmbeddingEncoder,
                   shard_size: int = 50_000) -> Iterator[tuple[slice, np.ndarray]]:
    """Encode theo shard de khong giu toan bo cache lon trong RAM."""
    if shard_size <= 0:
        raise ValueError("shard_size phai > 0")
    for start in range(0, len(texts), shard_size):
        stop = min(start + shard_size, len(texts))
        yield slice(start, stop), encoder.encode(texts[start:stop])


def encode_to_memmap(texts: Sequence[str], output: str | Path, encoder: EmbeddingEncoder,
                     shard_size: int = 50_000) -> np.memmap:
    """Ghi cache theo ``cache_dtype`` theo shard; B8 co the them checkpoint/resume."""
    path = Path(output)
    path.parent.mkdir(parents=True, exist_ok=True)
    cache = np.lib.format.open_memmap(
        path, mode="w+", dtype=np.dtype(encoder.cache_dtype),
        shape=(len(texts), encoder.dimension)
    )
    for rows, values in encode_batches(texts, encoder, shard_size):
        cache[rows] = values
        cache.flush()
    return cache
