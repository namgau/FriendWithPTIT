"""Danh sách hội thoại cần tính embedding (B8) — để không phải embed toàn bộ 9 triệu tin của E1.

Chạy:  python -m src.m3_health.embed_ids [E1 E2 G1 H1 H2]
Ra:    data/processed/<ID>/embed_conversation_ids.txt (không commit), 1 conversation_id mỗi dòng, đã sắp xếp.

E1, E2: hội thoại CV dùng (`labels.cv_conversations`, giống hệt mọi script Head A)
        ∪ mọi hội thoại đủ dài để có mẫu Head A nhưng không nằm trong train (= phần test, suy ra từ
        bảng tin nhắn ; không đọc data/locked/). Danh sách gộp và sắp xếp nên không phân biệt train/test,
        không có nhãn → người chạy B8 không biết hội thoại nào thuộc test.
G1, H1, H2: toàn bộ hội thoại (nhỏ).
"""
from __future__ import annotations

import sys

import numpy as np
import pandas as pd

from src.m3_health.labels import PROC, cv_conversations, load_config, load_train

HEAD_A = {"E1", "E2"}


def conversations_to_embed(d: str, cfg: dict) -> tuple[np.ndarray, pd.Series]:
    msgs = pd.read_parquet(PROC / d / "messages.parquet", columns=["conversation_id"])
    n_msgs = msgs["conversation_id"].value_counts()
    if d not in HEAD_A:
        return np.sort(n_msgs.index.to_numpy()), n_msgs
    lab = load_train(d)
    ids = n_msgs.index
    not_train = (n_msgs > cfg["head_a"]["start_turn"]).to_numpy() & ~ids.isin(lab["conversation_id"].unique())
    keep = not_train | ids.isin(cv_conversations(lab, cfg))
    return np.sort(ids[keep].to_numpy()), n_msgs


def main(datasets: list[str]) -> None:
    cfg = load_config()
    for d in datasets:
        ids, n_msgs = conversations_to_embed(d, cfg)
        out = PROC / d / "embed_conversation_ids.txt"
        out.write_text("\n".join(map(str, ids)) + "\n", encoding="utf-8", newline="\n")
        print(f"{d}: {len(ids):,} conversations, {int(n_msgs.loc[ids].sum()):,} / {int(n_msgs.sum()):,} messages -> {out}")


if __name__ == "__main__":
    main(sys.argv[1:] or ["E1", "E2", "G1", "H1", "H2"])
