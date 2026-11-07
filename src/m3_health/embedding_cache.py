"""Đọc cache embedding của B8 và tính đặc trưng T4 cho mẫu Head A theo từng khối hội thoại.

Định dạng cache (docs/02 B8), trong data/processed/<ID>/ (không commit):
- `emb_bge-m3.npy`            — (N_tin, 1024) float16, ghi bằng `embeddings.encode_to_memmap`
- `emb_bge-m3_index.parquet`  — cột `conversation_id, turn_idx`, dòng i ứng với dòng i của `.npy`

T4 của C1 (`features_t4.extract_t4_features`) giữ cả ma trận float32 của mọi tin trong RAM ;
với E1 (≈ 1,2 triệu tin của 60k hội thoại CV × 1024 chiều ≈ 5 GB) không vừa máy 16 GB, nên ở đây
cắt theo khối hội thoại và chỉ đọc đúng các dòng cần từ memmap.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from src.m3_health.features_t4 import KEY_COLUMNS, extract_t4_features
from src.m3_health.labels import PROC

MODEL_TAG = "bge-m3"


def cache_paths(d: str, root: Path = PROC) -> tuple[Path, Path]:
    base = Path(root) / d
    return base / f"emb_{MODEL_TAG}.npy", base / f"emb_{MODEL_TAG}_index.parquet"


def has_cache(d: str, root: Path = PROC) -> bool:
    return all(p.exists() for p in cache_paths(d, root))


def load_cache(d: str, root: Path = PROC) -> tuple[np.ndarray, pd.DataFrame]:
    emb_path, idx_path = cache_paths(d, root)
    emb = np.load(emb_path, mmap_mode="r")
    index = pd.read_parquet(idx_path, columns=KEY_COLUMNS)
    if len(index) != len(emb):
        raise ValueError(f"{d}: index có {len(index)} dòng nhưng cache có {len(emb)}")
    if index.duplicated(KEY_COLUMNS).any():
        raise ValueError(f"{d}: index cache có khóa trùng")
    return emb, index


def t4_for_samples(msgs: pd.DataFrame, samples: pd.DataFrame, emb: np.ndarray, index: pd.DataFrame,
                   pca=None, chunk_conversations: int = 5000) -> pd.DataFrame:
    """T4 cho `samples` (giữ nguyên index/thứ tự), chỉ nạp embedding của từng khối hội thoại."""
    row_of = pd.Series(np.arange(len(index)), index=pd.MultiIndex.from_frame(index[KEY_COLUMNS]))
    convs = samples["conversation_id"].unique()
    parts = []
    for start in range(0, len(convs), chunk_conversations):
        block = convs[start:start + chunk_conversations]
        m = msgs[msgs["conversation_id"].isin(block)]
        s = samples[samples["conversation_id"].isin(block)]
        keys = pd.MultiIndex.from_frame(m[KEY_COLUMNS])
        rows = row_of.reindex(keys)
        if rows.isna().any():
            miss = keys[rows.isna().to_numpy()][0]
            raise KeyError(f"cache thiếu embedding của tin {miss!r} — B8 chưa embed đủ danh sách embed_ids?")
        order = rows.to_numpy(np.int64)
        sort = np.argsort(order)  # đọc memmap theo thứ tự tăng cho nhanh
        vectors = np.empty((len(order), emb.shape[1]), np.float32)
        vectors[sort] = emb[order[sort]]
        parts.append(extract_t4_features(m, s, vectors, pca=pca))
    return pd.concat(parts).loc[samples.index]
