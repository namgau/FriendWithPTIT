"""B8: stream selected messages into a resumable BGE-M3 cache.

The final .npy is published last, so embedding_cache.has_cache cannot see an
unfinished pair. Never run two workers against the same dataset directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import time
from pathlib import Path

import numpy as np
import pandas as pd
import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.parquet as pq

from src.m3_health.embeddings import config_hash, encode_to_memmap, load_embedding_config, make_encoder

DATASETS = ("E1", "E2", "G1", "H1", "H2")
KEYS = ["conversation_id", "turn_idx"]
COLUMNS = KEYS + ["text"]
TAG = "emb_bge-m3"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, value: dict) -> None:
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def selected_batches(source: Path, ids: set[str], chunk_size: int):
    parquet = pq.ParquetFile(source)
    field = parquet.schema_arrow.field("conversation_id")
    if not (pa.types.is_string(field.type) or pa.types.is_large_string(field.type)):
        raise ValueError("conversation_id phai la chuoi trong messages.parquet")
    allowed = pa.array(sorted(ids), type=field.type)
    for batch in parquet.iter_batches(batch_size=chunk_size, columns=COLUMNS, use_threads=False):
        table = pa.Table.from_batches([batch])
        yield table.filter(pc.is_in(table["conversation_id"], value_set=allowed))


def _ids(path: Path) -> set[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or any(not line for line in lines) or len(set(lines)) != len(lines):
        raise ValueError(f"Danh sach ID rong, co dong rong hoac trung: {path}")
    return set(lines)


def _build_index(source: Path, path: Path, ids: set[str], chunk_size: int, dataset: str) -> int:
    schema = pq.ParquetFile(source).schema_arrow
    index_schema = pa.schema([schema.field(key) for key in KEYS])
    seen = set()
    count = 0
    with pq.ParquetWriter(path, index_schema) as writer:
        for table in selected_batches(source, ids, chunk_size):
            if not len(table):
                continue
            index = table.select(KEYS).cast(index_schema)
            if any(index.column(key).null_count for key in KEYS):
                raise ValueError("Khoa index co gia tri null")
            writer.write_table(index)
            seen.update(index.column("conversation_id").to_pylist())
            count += len(index)
    missing = ids - seen
    if missing:
        raise ValueError(f"{len(missing)} conversation_id khong co trong messages.parquet")
    if not count:
        raise ValueError("Khong co tin nhan duoc chon")
    if dataset in {"G1", "H1", "H2"} and count != pq.ParquetFile(source).metadata.num_rows:
        raise ValueError(f"{dataset} phai bao gom tat ca hoi thoai")
    index_df = pd.read_parquet(path, columns=KEYS)
    if index_df.duplicated(KEYS).any():
        raise ValueError("Trung khoa (conversation_id, turn_idx) trong messages.parquet")
    return count


def _rows_sha256(cache: np.ndarray, start: int, end: int) -> str:
    digest = hashlib.sha256()
    for first in range(start, end, 2048):
        digest.update(np.ascontiguousarray(cache[first:min(first + 2048, end)]).tobytes())
    return digest.hexdigest()


def run_dataset(dataset: str, root: Path, encoder, cfg: dict, *, chunk_size: int,
                batch_size: int, resume: bool = False, device_info: str = "unknown") -> dict:
    """Produce one cache; injectable encoder allows CPU tests without a model."""
    if dataset not in DATASETS or chunk_size <= 0 or batch_size <= 0:
        raise ValueError("Dataset hoac batch/chunk size khong hop le")
    base = Path(root) / dataset
    source = base / "messages.parquet"
    ids_path = base / "embed_conversation_ids.txt"
    for required in (source, ids_path):
        if not required.is_file():
            raise FileNotFoundError(f"Thieu {required}; chuan bi du lieu va chay embed_ids truoc")
    ids = _ids(ids_path)
    final = base / f"{TAG}.npy"
    final_index = base / f"{TAG}_index.parquet"
    part = base / f"{TAG}.partial.npy"
    chunk_path = base / f"{TAG}.chunk.npy"
    part_index = base / f"{TAG}_index.partial.parquet"
    state_path = base / f"{TAG}.checkpoint.json"
    metadata_path = base / f"{TAG}.metadata.json"
    encoder_metadata = encoder.metadata()
    encoder_metadata.pop("model_parameter_mib", None)
    fingerprint = {
        "version": 2, "dataset": dataset, "source_sha256": sha256(source),
        "ids_sha256": sha256(ids_path), "config_hash": config_hash(cfg),
        "encoder": encoder_metadata, "dtype": encoder.cache_dtype,
    }
    if encoder.cache_dtype != "float16":
        raise ValueError("B8 yeu cau cache_dtype float16")
    if resume:
        if not state_path.exists():
            raise FileNotFoundError(f"Khong co checkpoint: {state_path}")
        state = json.loads(state_path.read_text(encoding="utf-8"))
        if state["fingerprint"] != fingerprint:
            raise ValueError("Du lieu, danh sach ID hoac cau hinh da doi; khong the resume")
    else:
        existing = [p for p in (final, final_index, part, part_index, chunk_path, state_path, metadata_path) if p.exists()]
        if existing:
            raise FileExistsError(f"Da co output/checkpoint: {existing}; dung --resume hoac luu va xoa thu cong")
        try:
            count = _build_index(source, part_index, ids, chunk_size, dataset)
            cache = np.lib.format.open_memmap(part, mode="w+", dtype=np.float16,
                                              shape=(count, encoder.dimension))
            cache.flush()
            del cache
        except Exception:
            part.unlink(missing_ok=True)
            part_index.unlink(missing_ok=True)
            raise
        state = {"fingerprint": fingerprint, "rows": count, "completed": 0,
                 "index_sha256": sha256(part_index), "active_seconds": 0.0,
                 "devices": [], "chunks": [], "status": "encoding"}
        write_json(state_path, state)
    index_path = part_index if part_index.exists() else final_index
    if not index_path.exists() or sha256(index_path) != state["index_sha256"]:
        raise ValueError("Index tam bi thieu hoac da thay doi")
    if not 0 <= state["completed"] <= state["rows"]:
        raise ValueError("Checkpoint co so dong khong hop le")
    cache_path = part if part.exists() else final
    if not cache_path.exists() or (cache_path == final and state["completed"] != state["rows"]):
        raise ValueError("File embedding tam bi thieu hoac checkpoint chua hoan thanh")
    cache = np.load(cache_path, mmap_mode="r+", allow_pickle=False)
    if cache.shape != (state["rows"], encoder.dimension) or cache.dtype != np.float16:
        raise ValueError("Shape/dtype cache khong khop checkpoint")
    verified = 0
    for chunk in state["chunks"]:
        if (chunk["start"] != verified or not verified < chunk["end"] <= state["completed"]
                or _rows_sha256(cache, verified, chunk["end"]) != chunk["sha256"]):
            raise ValueError("Du lieu embedding da checkpoint bi thay doi hoac khong hop le")
        verified = chunk["end"]
    if verified != state["completed"]:
        raise ValueError("Checkpoint thieu checksum embedding")
    if device_info not in state["devices"]:
        state["devices"].append(device_info)
    position = 0
    for table in selected_batches(source, ids, chunk_size):
        end = position + len(table)
        start = max(position, state["completed"])
        if start < end:
            started = time.monotonic()
            texts = table.column("text").slice(start - position).to_pylist()
            # C1 opens its output with w+: give it a disposable chunk, never the
            # persistent cache. Restore the encoder config even after failure.
            original_model = encoder.model
            encoder.model = {**original_model, "batch_size": batch_size}
            try:
                encoded = encode_to_memmap(texts, chunk_path, encoder, shard_size=chunk_size)
                try:
                    if encoded.shape != (end - start, encoder.dimension) or encoded.dtype != np.float16:
                        raise ValueError("Chunk C1 co shape/dtype khong hop le")
                    cache[start:end] = encoded
                finally:
                    del encoded
                chunk_path.unlink(missing_ok=True)
            finally:
                # On encoder failure, C1's traceback may retain the mmap on
                # Windows. Leave the uncommitted chunk for the next resume.
                encoder.model = original_model
            cache.flush()
            with cache_path.open("r+b") as stream:
                os.fsync(stream.fileno())
            state["chunks"].append({"start": start, "end": end,
                                    "sha256": _rows_sha256(cache, start, end)})
            state["completed"] = end
            state["active_seconds"] += time.monotonic() - started
            write_json(state_path, state)
            print(f"{dataset}: {end:,}/{state['rows']:,} rows", flush=True)
        position = end
    if position != state["rows"] or state["completed"] != state["rows"]:
        raise ValueError("So dong duoc chon khong khop checkpoint")
    if (sha256(source) != fingerprint["source_sha256"]
            or sha256(ids_path) != fingerprint["ids_sha256"]
            or sha256(index_path) != state["index_sha256"]):
        raise ValueError("Du lieu dau vao hoac index da thay doi trong khi encode")
    del cache
    if part_index.exists():
        os.replace(part_index, final_index)
    if part.exists():
        os.replace(part, final)
    state["status"] = "complete"
    write_json(metadata_path, state)
    write_json(state_path, state)
    return state


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("datasets", nargs="*", metavar="DATASET", help="E1 E2 G1 H1 H2; default G1 H1 H2")
    parser.add_argument("--root", type=Path, default=Path("data/processed"))
    parser.add_argument("--config", type=Path, default=Path("configs/embedding.yaml"))
    parser.add_argument("--chunk-size", type=int, default=2048, help="rows per durable checkpoint")
    parser.add_argument("--batch-size", type=int, default=32, help="model inference batch size")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--allow-cpu", action="store_true", help="explicitly permit slow CPU inference")
    args = parser.parse_args()
    args.datasets = args.datasets or ["G1", "H1", "H2"]
    if any(dataset not in DATASETS for dataset in args.datasets):
        parser.error(f"Dataset phai thuoc {', '.join(DATASETS)}")
    cfg = load_embedding_config(args.config)
    if cfg["selected_model"] != "bge_m3":
        parser.error("B8 yeu cau model BAAI/bge-m3")
    model = cfg["models"]["bge_m3"]
    if (model.get("model_id") != "BAAI/bge-m3" or model.get("dimension") != 1024
            or model.get("max_length") != 256 or model.get("compute_dtype") != "float16"
            or cfg.get("cache_dtype") != "float16" or cfg.get("normalize") is not True):
        parser.error("B8 yeu cau BGE-M3 dense 1024 chieu, max_length 256, fp16 va L2")
    try:
        import torch
    except ImportError:
        parser.error("Can cai torch va sentence-transformers truoc khi chay")
    if not torch.cuda.is_available() and not args.allow_cpu:
        parser.error("Khong tim thay GPU CUDA; chon GPU hoac dung --allow-cpu cho smoke test")
    requested_device = str(cfg["models"]["bge_m3"].get("device", "auto"))
    if requested_device == "cpu" and not args.allow_cpu:
        parser.error("Config dang chon CPU; dung config GPU hoac --allow-cpu")
    device_index = requested_device if requested_device.startswith("cuda:") else 0
    device = (torch.cuda.get_device_name(device_index) if torch.cuda.is_available() and requested_device != "cpu"
              else f"CPU {platform.processor() or platform.machine()}")
    encoder = make_encoder(cfg)
    for dataset in args.datasets:
        run_dataset(dataset, args.root, encoder, cfg, chunk_size=args.chunk_size,
                    batch_size=args.batch_size, resume=args.resume, device_info=device)


if __name__ == "__main__":
    main()
