"""C4: Dataset và collate function cho mô hình chuỗi Head B (CRAFT-like GRU) từ cache B8.

Quy tắc chống rò rỉ (strict anti-leakage):
- Hội thoại hỏng (derailed == 1): CHỈ lấy các lượt `turn_idx < attack_turn_idx`.
  Tuyệt đối không lấy lượt công kích đầu tiên và mọi lượt sau.
- Hội thoại lành (derailed == 0): CHỈ lấy các lượt `turn_idx < prefix_len` (bỏ lượt cuối, như CRAFT).
- Dữ liệu tập test khóa trong `data/locked/` tuyệt đối không được đọc (chỉ trưởng nhóm A mở ngày 20/11).
"""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path
from typing import Sequence

import numpy as np
import pandas as pd
try:
    import torch
    from torch.nn.utils.rnn import pad_sequence
    from torch.utils.data import Dataset
    _TORCH_AVAILABLE = True
except ImportError:
    torch = None
    pad_sequence = None
    Dataset = object
    _TORCH_AVAILABLE = False

from src.m3_health.embedding_cache import load_cache
from src.m3_health.labels import PROC


def _assert_not_locked(path: Path | str) -> None:
    resolved_parts = Path(path).resolve().parts
    if "locked" in resolved_parts:
        raise PermissionError(
            f"Truy cập tập test khóa trong '{path}' bị nghiêm cấm theo quy định bảo mật audit!"
        )


class G1SequenceDataset(Dataset):
    """Dataset chuỗi lượt nói cho Head B trên G1 (Conversations Gone Awry).

    Mỗi mẫu trả về một chuỗi vector embedding các lượt tiền tố (trước công kích hoặc bỏ lượt cuối).
    """

    def __init__(
        self,
        root: Path | str = PROC,
        emb: np.ndarray | None = None,
        index_df: pd.DataFrame | None = None,
        labels_df: pd.DataFrame | None = None,
        conversation_ids: Sequence[str] | None = None,
        dtype: Any = None,
        drop_empty: bool = True,
    ) -> None:
        """Khởi tạo G1SequenceDataset.

        Parameters
        ----------
        root : Path | str
            Thư mục `data/processed` (mặc định: `src.m3_health.labels.PROC`).
            Tuyệt đối cấm trỏ vào `data/locked`.
        emb : np.ndarray | None
            Mảng embedding (N, 1024). Nếu None, nạp qua `load_cache('G1', root)`.
        index_df : pd.DataFrame | None
            DataFrame chỉ mục cache (`conversation_id`, `turn_idx`).
            Nếu None, nạp qua `load_cache('G1', root)`.
        labels_df : pd.DataFrame | None
            DataFrame nhãn Head B train (`labels_headB_train.parquet`).
            Nếu None, đọc từ `root / 'G1' / 'labels_headB_train.parquet'`.
        conversation_ids : Sequence[str] | None
            Danh sách ID hội thoại cần giữ (ví dụ theo fold GroupKFold).
            Nếu None, giữ tất cả hội thoại trong `labels_df`.
        dtype : torch.dtype
            Kiểu dữ liệu cho tensor vector embedding (mặc định: torch.float32).
        drop_empty : bool
            Nếu True, loại bỏ các hội thoại không có lượt tiền tố hợp lệ nào (length == 0).
        """
        if not _TORCH_AVAILABLE:
            raise ImportError("PyTorch is required for G1SequenceDataset. Install torch.")
        if dtype is None:
            dtype = torch.float32
        _assert_not_locked(root)

        # 1. Nạp cache embedding nếu chưa được truyền trực tiếp
        if emb is None or index_df is None:
            loaded_emb, loaded_index = load_cache("G1", root=Path(root))
            emb = loaded_emb if emb is None else emb
            index_df = loaded_index if index_df is None else index_df

        # 2. Nạp labels_headB_train.parquet nếu chưa được truyền trực tiếp
        if labels_df is None:
            labels_path = Path(root) / "G1" / "labels_headB_train.parquet"
            _assert_not_locked(labels_path)
            if not labels_path.exists():
                raise FileNotFoundError(
                    f"Không tìm thấy nhãn train Head B tại '{labels_path}'."
                )
            labels_df = pd.read_parquet(labels_path)
        else:
            labels_df = labels_df.copy()

        # 3. Lọc theo conversation_ids (nếu có)
        if conversation_ids is not None:
            allowed_set = set(conversation_ids)
            labels_df = labels_df[labels_df["conversation_id"].isin(allowed_set)].copy()

        # 4. Gom nhóm chỉ mục cache theo conversation_id: conv_id -> [(turn_idx, emb_row_idx), ...]
        conv_to_turns: dict[str, list[tuple[int, int]]] = defaultdict(list)
        for row_idx, (c_id, t_idx) in enumerate(
            zip(index_df["conversation_id"].values, index_df["turn_idx"].values)
        ):
            conv_to_turns[str(c_id)].append((int(t_idx), row_idx))

        # 5. Xây dựng danh sách mẫu với cơ chế chống rò rỉ dữ liệu nghiêm ngặt
        samples: list[dict] = []
        for row in labels_df.itertuples(index=False):
            conv_id = str(row.conversation_id)
            group_id = str(getattr(row, "group_id", conv_id))
            derailed = int(row.derailed)
            attack_turn_idx = int(getattr(row, "attack_turn_idx", -1))
            prefix_len = int(getattr(row, "prefix_len", 0))

            turns = conv_to_turns.get(conv_id, [])

            # Lọc turn tiền tố theo quy tắc chống rò rỉ:
            # - Với derailed = 1: turn_idx < attack_turn_idx
            # - Với derailed = 0: turn_idx < prefix_len
            if derailed == 1:
                if attack_turn_idx >= 0:
                    kept = [(t_idx, r_idx) for t_idx, r_idx in turns if t_idx < attack_turn_idx]
                else:
                    kept = [(t_idx, r_idx) for t_idx, r_idx in turns if t_idx < prefix_len]
            else:
                kept = [(t_idx, r_idx) for t_idx, r_idx in turns if t_idx < prefix_len]

            # Đảm bảo thứ tự thời gian tăng dần theo turn_idx
            kept.sort(key=lambda item: item[0])

            # Kiểm tra chống rò rỉ tại thời điểm nạp (strict anti-leakage audit check)
            for t_idx, _ in kept:
                if derailed == 1 and attack_turn_idx >= 0 and t_idx >= attack_turn_idx:
                    raise ValueError(
                        f"RÒ RỈ DỮ LIỆU: Hội thoại hỏng '{conv_id}' chứa turn_idx {t_idx} "
                        f">= attack_turn_idx {attack_turn_idx}!"
                    )
                if derailed == 0 and t_idx >= prefix_len:
                    raise ValueError(
                        f"RÒ RỈ DỮ LIỆU: Hội thoại lành '{conv_id}' chứa turn_idx {t_idx} "
                        f">= prefix_len {prefix_len}!"
                    )

            if drop_empty and len(kept) == 0:
                continue

            row_indices = np.array([r_idx for _, r_idx in kept], dtype=np.int64)
            samples.append({
                "conv_id": conv_id,
                "group_id": group_id,
                "label": derailed,
                "length": len(kept),
                "row_indices": row_indices,
            })

        self.emb = emb
        self.samples = samples
        self.dtype = dtype

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, int, int, str, str]:
        """Lấy một mẫu hội thoại.

        Returns
        -------
        tensor_x : torch.Tensor
            Tensor chứa chuỗi vector lượt nói tiền tố, shape (length, D) với D=1024.
        label : int
            Nhãn derailed (0: bình thường, 1: công kích cá nhân).
        length : int
            Số lượt nói tiền tố thực tế trong chuỗi.
        conv_id : str
            Mã định danh hội thoại.
        group_id : str
            Mã định danh nhóm trang / cặp ghép để phân nhóm đánh giá.
        """
        sample = self.samples[idx]
        row_indices = sample["row_indices"]
        if len(row_indices) > 0:
            vecs = self.emb[row_indices]
            tensor_x = torch.as_tensor(vecs, dtype=self.dtype)
        else:
            dim = self.emb.shape[1] if hasattr(self.emb, "shape") else 1024
            tensor_x = torch.zeros((0, dim), dtype=self.dtype)

        return (
            tensor_x,
            sample["label"],
            sample["length"],
            sample["conv_id"],
            sample["group_id"],
        )


def pad_collate_fn(
    batch: list[tuple[torch.Tensor, int | float, int, str, str]],
    label_dtype: torch.dtype = torch.float32,
    mask_dtype: torch.dtype = torch.bool,
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
    """Hàm collate ghép các mẫu độ dài khác nhau thành batch đệm zero.

    Sử dụng `torch.nn.utils.rnn.pad_sequence` để padding thành shape (B, T_max, 1024).

    Parameters
    ----------
    batch : list[tuple[torch.Tensor, int | float, int, str, str]]
        Danh sách mẫu từ `G1SequenceDataset.__getitem__`.
    label_dtype : torch.dtype
        Kiểu dữ liệu cho tensor nhãn (mặc định: torch.float32 phù hợp BCEWithLogitsLoss).
    mask_dtype : torch.dtype
        Kiểu dữ liệu cho mask che (mặc định: torch.bool, True tại vị trí hợp lệ).

    Returns
    -------
    padded_x : torch.Tensor
        Batch tensor có shape `(B, T_max, 1024)`, đệm bằng 0.0.
    lengths : torch.Tensor
        Tensor chứa độ dài thực tế của từng chuỗi, shape `(B,)`, int64.
    labels : torch.Tensor
        Tensor nhãn `(B,)`, float32.
    mask : torch.Tensor
        Tensor mask có shape `(B, T_max)`, True cho các lượt hợp lệ, False cho vị trí padding.
    """
    if not batch:
        return (
            torch.zeros((0, 0, 1024), dtype=torch.float32),
            torch.zeros((0,), dtype=torch.long),
            torch.zeros((0,), dtype=label_dtype),
            torch.zeros((0, 0), dtype=mask_dtype),
        )

    sequences = [item[0] for item in batch]
    labels = torch.as_tensor([item[1] for item in batch], dtype=label_dtype)
    lengths = torch.as_tensor([item[2] for item in batch], dtype=torch.long)

    padded_x = pad_sequence(sequences, batch_first=True, padding_value=0.0)
    B, max_len = padded_x.size(0), padded_x.size(1)

    if max_len > 0:
        mask = (torch.arange(max_len).unsqueeze(0) < lengths.unsqueeze(1)).to(mask_dtype)
    else:
        mask = torch.zeros((B, 0), dtype=mask_dtype)

    return padded_x, lengths, labels, mask


def pad_collate_fn_with_meta(
    batch: list[tuple[torch.Tensor, int | float, int, str, str]],
    label_dtype: torch.dtype = torch.float32,
    mask_dtype: torch.dtype = torch.bool,
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, list[str], list[str]]:
    """Tương tự `pad_collate_fn`, nhưng trả về thêm metadata conv_ids và group_ids."""
    padded_x, lengths, labels, mask = pad_collate_fn(
        batch, label_dtype=label_dtype, mask_dtype=mask_dtype
    )
    conv_ids = [item[3] for item in batch]
    group_ids = [item[4] for item in batch]
    return padded_x, lengths, labels, mask, conv_ids, group_ids
