"""C2 Toxicity Dataset & Loss Functions for Vietnamese Toxicity Classification (PhoBERT/XLM-R).

Module cung cấp:
  1. ToxicityDataset: PyTorch Dataset bọc DataFrame text/label, tích hợp tokenizer PhoBERT/XLM-R,
     hỗ trợ truncation, padding max_length=128 và chuẩn hóa nhãn tensor float.
  2. WeightedBCEWithLogitsLoss: Loss nhị phân có trọng số pos_weight = N_neg / N_pos cho dữ liệu lệch.
  3. FocalLoss: Lin et al. (ICCV 2017) với alpha=0.75, gamma=2.0 giảm trọng số mẫu dễ.
  4. load_h1_train_data / load_h2_train_data: Đọc dữ liệu huấn luyện Head B trực tiếp từ
     data/processed/{H1,H2}/labels_headB_train.parquet (tuyệt đối không đọc data/locked/).
"""
from __future__ import annotations

from pathlib import Path
from typing import Any, Sequence

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F

ROOT = Path(__file__).resolve().parents[2]
PROC = ROOT / "data" / "processed"


def _assert_not_locked(path: Path | str) -> Path:
    """Đảm bảo đường dẫn tuyệt đối không xâm phạm data/locked."""
    resolved = Path(path).resolve()
    parts_lower = [part.lower() for part in resolved.parts]
    if "locked" in parts_lower:
        raise PermissionError(
            f"Bảo vệ dữ liệu khóa (A3): Tuyệt đối không được đọc dữ liệu từ data/locked! Đường dẫn: {resolved}"
        )
    return resolved


class ToxicityDataset(torch.utils.data.Dataset):
    """PyTorch Dataset cho phân loại độc hại từng câu/tin nhắn.

    Nhận DataFrame chứa text và label (0: sạch, 1: độc hại/xúc phạm), tokenizer PhoBERT/XLM-R.
    Trả về dict {'input_ids': ..., 'attention_mask': ..., 'label': torch.tensor(y, dtype=torch.float)}.
    """

    def __init__(
        self,
        df: pd.DataFrame,
        tokenizer: Any,
        max_length: int = 128,
        text_col: str = "text",
        label_col: str = "label",
    ):
        if df.empty:
            self.texts: list[str] = []
            self.labels: np.ndarray = np.array([], dtype=np.float32)
        else:
            # Linh hoạt nhận diện cột văn bản nếu text_col chưa khớp trực tiếp
            actual_text_col = text_col
            if actual_text_col not in df.columns:
                for candidate in ["free_text", "Comment", "raw_text", "comment"]:
                    if candidate in df.columns:
                        actual_text_col = candidate
                        break
                else:
                    raise KeyError(
                        f"Không tìm thấy cột văn bản '{text_col}' trong DataFrame. Các cột hiện có: {list(df.columns)}"
                    )

            # Linh hoạt nhận diện cột nhãn nếu label_col chưa khớp trực tiếp
            actual_label_col = label_col
            if actual_label_col not in df.columns:
                for candidate in ["toxic", "y", "derailed", "target"]:
                    if candidate in df.columns:
                        actual_label_col = candidate
                        break
                else:
                    raise KeyError(
                        f"Không tìm thấy cột nhãn '{label_col}' trong DataFrame. Các cột hiện có: {list(df.columns)}"
                    )

            self.texts = ["" if pd.isna(t) else str(t) for t in df[actual_text_col]]
            self.labels = np.asarray(df[actual_label_col], dtype=np.float32)

        self.tokenizer = tokenizer
        self.max_length = int(max_length)

    def __len__(self) -> int:
        return len(self.texts)

    def __getitem__(self, idx: int) -> dict[str, torch.Tensor]:
        text = self.texts[idx]
        label_val = float(self.labels[idx])

        encoding = self.tokenizer(
            text,
            max_length=self.max_length,
            padding="max_length",
            truncation=True,
            return_tensors="pt",
        )

        input_ids = encoding["input_ids"]
        if not isinstance(input_ids, torch.Tensor):
            input_ids = torch.tensor(input_ids, dtype=torch.long)
        if input_ids.dim() > 1:
            input_ids = input_ids.squeeze(0)

        attention_mask = encoding["attention_mask"]
        if not isinstance(attention_mask, torch.Tensor):
            attention_mask = torch.tensor(attention_mask, dtype=torch.long)
        if attention_mask.dim() > 1:
            attention_mask = attention_mask.squeeze(0)

        item: dict[str, torch.Tensor] = {
            "input_ids": input_ids.long(),
            "attention_mask": attention_mask.long(),
            "label": torch.tensor(label_val, dtype=torch.float),
        }

        if "token_type_ids" in encoding:
            token_type_ids = encoding["token_type_ids"]
            if not isinstance(token_type_ids, torch.Tensor):
                token_type_ids = torch.tensor(token_type_ids, dtype=torch.long)
            if token_type_ids.dim() > 1:
                token_type_ids = token_type_ids.squeeze(0)
            item["token_type_ids"] = token_type_ids.long()

        return item

    @property
    def pos_weight(self) -> float:
        """Tỷ số N_neg / N_pos cho WeightedBCEWithLogitsLoss."""
        if len(self.labels) == 0:
            return 1.0
        n_pos = float((self.labels == 1.0).sum())
        n_neg = float((self.labels == 0.0).sum())
        return (n_neg / n_pos) if n_pos > 0 else 1.0

    @property
    def prevalence(self) -> float:
        """Tỷ lệ mẫu dương tính N_pos / N_total."""
        if len(self.labels) == 0:
            return 0.0
        return float(np.mean(self.labels == 1.0))


class WeightedBCEWithLogitsLoss(nn.Module):
    """Binary Cross Entropy With Logits hỗ trợ pos_weight = N_neg / N_pos.

    Được tối ưu để phạt nặng lỗi bỏ sót ở lớp thiểu số (toxic=1).
    """

    def __init__(
        self,
        pos_weight: float | torch.Tensor = 1.0,
        reduction: str = "mean",
    ):
        super().__init__()
        if not isinstance(pos_weight, torch.Tensor):
            pos_weight_tensor = torch.tensor(float(pos_weight), dtype=torch.float32)
        else:
            pos_weight_tensor = pos_weight.float()

        self.register_buffer("pos_weight", pos_weight_tensor)
        self.reduction = reduction

    @classmethod
    def from_counts(
        cls, n_neg: int, n_pos: int, reduction: str = "mean"
    ) -> WeightedBCEWithLogitsLoss:
        """Khởi tạo từ số lượng mẫu âm và dương: pos_weight = N_neg / N_pos."""
        weight = (float(n_neg) / float(n_pos)) if n_pos > 0 else 1.0
        return cls(pos_weight=weight, reduction=reduction)

    @classmethod
    def from_labels(
        cls, labels: Sequence[Any] | np.ndarray | torch.Tensor | pd.Series, reduction: str = "mean"
    ) -> WeightedBCEWithLogitsLoss:
        """Khởi tạo tự động tính pos_weight từ mảng nhãn."""
        if isinstance(labels, torch.Tensor):
            arr = labels.detach().cpu().numpy()
        elif isinstance(labels, pd.Series):
            arr = labels.to_numpy()
        else:
            arr = np.asarray(labels)
        arr = arr.flatten()
        n_pos = int((arr == 1).sum())
        n_neg = int((arr == 0).sum())
        return cls.from_counts(n_neg=n_neg, n_pos=n_pos, reduction=reduction)

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        targets = targets.view_as(logits).float()
        return F.binary_cross_entropy_with_logits(
            logits,
            targets,
            pos_weight=self.pos_weight,
            reduction=self.reduction,
        )


class FocalLoss(nn.Module):
    """Focal Loss cho phân loại nhị phân (Lin et al., ICCV 2017).

    FL(p_t) = -alpha_t * (1 - p_t)^gamma * log(p_t)
    với p_t = p nếu y=1, (1-p) nếu y=0.
    Mặc định: Alpha=0.75, Gamma=2.0 (chuẩn xử lý mất cân bằng lớp mạnh).
    """

    def __init__(
        self,
        alpha: float | None = 0.75,
        gamma: float = 2.0,
        reduction: str = "mean",
    ):
        super().__init__()
        self.alpha = float(alpha) if alpha is not None else None
        self.gamma = float(gamma)
        self.reduction = reduction

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        targets = targets.view_as(logits).float()
        bce = F.binary_cross_entropy_with_logits(logits, targets, reduction="none")

        p = torch.sigmoid(logits)
        p_t = p * targets + (1.0 - p) * (1.0 - targets)
        p_t = torch.clamp(p_t, min=0.0, max=1.0)

        if self.alpha is not None:
            alpha_t = self.alpha * targets + (1.0 - self.alpha) * (1.0 - targets)
        else:
            alpha_t = 1.0

        focal_weight = alpha_t * torch.pow(1.0 - p_t, self.gamma)
        loss = focal_weight * bce

        if self.reduction == "mean":
            return loss.mean()
        elif self.reduction == "sum":
            return loss.sum()
        elif self.reduction == "none":
            return loss
        else:
            raise ValueError(f"Không hỗ trợ reduction: {self.reduction}. Chọn 'mean', 'sum' hoặc 'none'.")


def load_h1_train_data(
    labels_path: Path | str | None = None,
    messages_path: Path | str | None = None,
) -> pd.DataFrame:
    """Tải tập huấn luyện H1 (ViHSD) trực tiếp từ data/processed/H1/labels_headB_train.parquet.

    Ghép nối với messages.parquet để bổ sung cột nội dung 'text'.
    Tuyệt đối không đọc dữ liệu từ data/locked/.
    """
    p = PROC / "H1" / "labels_headB_train.parquet" if labels_path is None else Path(labels_path)
    _assert_not_locked(p)
    if not p.exists():
        raise FileNotFoundError(f"Không tìm thấy tập train H1 tại: {p}")

    labels_df = pd.read_parquet(p)

    if "text" not in labels_df.columns:
        mp = p.parent / "messages.parquet" if messages_path is None else Path(messages_path)
        _assert_not_locked(mp)
        if mp.exists():
            msgs = pd.read_parquet(mp)[["conversation_id", "text"]]
            df = labels_df.merge(msgs, on="conversation_id", how="left")
        else:
            df = labels_df.copy()
            df["text"] = ""
    else:
        df = labels_df.copy()

    df["text"] = df["text"].fillna("")
    if "label" not in df.columns:
        if "toxic" in df.columns:
            df["label"] = df["toxic"].astype(int)
        elif "y" in df.columns:
            df["label"] = df["y"].astype(int)

    return df.reset_index(drop=True)


def load_h2_train_data(
    labels_path: Path | str | None = None,
    messages_path: Path | str | None = None,
) -> pd.DataFrame:
    """Tải tập huấn luyện H2 (ViCTSD) trực tiếp từ data/processed/H2/labels_headB_train.parquet.

    Ghép nối với messages.parquet để bổ sung cột nội dung 'text'.
    Tuyệt đối không đọc dữ liệu từ data/locked/.
    """
    p = PROC / "H2" / "labels_headB_train.parquet" if labels_path is None else Path(labels_path)
    _assert_not_locked(p)
    if not p.exists():
        raise FileNotFoundError(f"Không tìm thấy tập train H2 tại: {p}")

    labels_df = pd.read_parquet(p)

    if "text" not in labels_df.columns:
        mp = p.parent / "messages.parquet" if messages_path is None else Path(messages_path)
        _assert_not_locked(mp)
        if mp.exists():
            msgs = pd.read_parquet(mp)[["conversation_id", "text"]]
            df = labels_df.merge(msgs, on="conversation_id", how="left")
        else:
            df = labels_df.copy()
            df["text"] = ""
    else:
        df = labels_df.copy()

    df["text"] = df["text"].fillna("")
    if "label" not in df.columns:
        if "toxic" in df.columns:
            df["label"] = df["toxic"].astype(int)
        elif "y" in df.columns:
            df["label"] = df["y"].astype(int)

    return df.reset_index(drop=True)
