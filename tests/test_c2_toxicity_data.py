"""Unit tests for C2 Toxicity Dataset and Loss Functions.

Kiểm tra:
  - ToxicityDataset: shape tokenization, padding, truncation, DataLoader collation, nhãn torch.float.
  - WeightedBCEWithLogitsLoss: pos_weight = N_neg / N_pos, không bị NaN với extreme logits, gradient backward.
  - FocalLoss: alpha=0.75, gamma=2.0, không bị NaN với extreme logits, tính chất giảm trọng số mẫu dễ.
  - load_h1_train_data / load_h2_train_data: Đọc đúng bảng train và chặn tuyệt đối data/locked.
"""
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

torch = pytest.importorskip("torch")
nn = pytest.importorskip("torch.nn")
from torch.utils.data import DataLoader

from src.m3_health.c2_toxicity_data import (
    FocalLoss,
    ToxicityDataset,
    WeightedBCEWithLogitsLoss,
    load_h1_train_data,
    load_h2_train_data,
)


class MockTokenizer:
    """Mock Tokenizer mô phỏng HuggingFace Tokenizer cho unit test nhanh và độc lập."""

    def __init__(self, pad_token_id: int = 1, bos_token_id: int = 0, eos_token_id: int = 2):
        self.pad_token_id = pad_token_id
        self.bos_token_id = bos_token_id
        self.eos_token_id = eos_token_id

    def __call__(
        self,
        text: str,
        max_length: int = 128,
        padding: str = "max_length",
        truncation: bool = True,
        return_tensors: str = "pt",
    ) -> dict[str, torch.Tensor]:
        words = text.split()
        # Biến đổi text thành chuỗi token giả lập (có [BOS] và [EOS])
        tokens = [self.bos_token_id] + [hash(w) % 1000 + 10 for w in words] + [self.eos_token_id]

        if truncation and len(tokens) > max_length:
            tokens = tokens[:max_length]

        attn_mask = [1] * len(tokens)

        if padding == "max_length" and len(tokens) < max_length:
            pad_len = max_length - len(tokens)
            tokens = tokens + [self.pad_token_id] * pad_len
            attn_mask = attn_mask + [0] * pad_len

        return {
            "input_ids": torch.tensor([tokens], dtype=torch.long),
            "attention_mask": torch.tensor([attn_mask], dtype=torch.long),
        }


def test_toxicity_dataset_tokenization_shape_and_padding():
    """Kiểm tra shape của input_ids, attention_mask và cơ chế padding đúng max_length."""
    tokenizer = MockTokenizer()
    max_len = 128
    df = pd.DataFrame({
        "text": ["xin chào", "đây là một câu dài hơn để kiểm tra"],
        "label": [0, 1],
    })

    ds = ToxicityDataset(df, tokenizer=tokenizer, max_length=max_len)
    assert len(ds) == 2

    item0 = ds[0]
    assert "input_ids" in item0
    assert "attention_mask" in item0
    assert "label" in item0

    # Kiểm tra shape 1D (để DataLoader ghép thành batch 2D)
    assert item0["input_ids"].shape == (max_len,)
    assert item0["attention_mask"].shape == (max_len,)
    assert item0["label"].shape == ()
    assert item0["label"].dtype == torch.float32
    assert item0["label"].item() == 0.0

    # Kiểm tra padding mask: từ ngắn có mask=1 ở đầu và mask=0 ở phần đệm
    mask = item0["attention_mask"].tolist()
    assert 0 in mask
    assert mask.count(1) == 4  # BOS + "xin" + "chào" + EOS
    assert mask.count(0) == max_len - 4

    # Kiểm tra pad_token_id xuất hiện ở phần đệm
    pad_tokens = item0["input_ids"][4:].tolist()
    assert all(t == tokenizer.pad_token_id for t in pad_tokens)


def test_toxicity_dataset_truncation():
    """Kiểm tra câu dài hơn max_length được cắt bớt chính xác về max_length."""
    tokenizer = MockTokenizer()
    max_len = 16
    long_text = " ".join([f"word_{i}" for i in range(100)])
    df = pd.DataFrame({"text": [long_text], "label": [1.0]})

    ds = ToxicityDataset(df, tokenizer=tokenizer, max_length=max_len)
    item = ds[0]

    assert item["input_ids"].shape == (max_len,)
    assert item["attention_mask"].shape == (max_len,)
    assert (item["attention_mask"] == 1).all()


def test_toxicity_dataset_dataloader_collate():
    """Kiểm tra tích hợp DataLoader tạo batch 2D chuẩn xác."""
    tokenizer = MockTokenizer()
    max_len = 32
    df = pd.DataFrame({
        "text": ["câu 1", "câu số 2", "câu thứ 3 dài hơn", "câu 4"],
        "label": [0, 1, 0, 1],
    })

    ds = ToxicityDataset(df, tokenizer=tokenizer, max_length=max_len)
    loader = DataLoader(ds, batch_size=2, shuffle=False)

    batches = list(loader)
    assert len(batches) == 2

    batch0 = batches[0]
    assert batch0["input_ids"].shape == (2, max_len)
    assert batch0["attention_mask"].shape == (2, max_len)
    assert batch0["label"].shape == (2,)
    assert batch0["label"].dtype == torch.float32
    torch.testing.assert_close(batch0["label"], torch.tensor([0.0, 1.0]))


def test_toxicity_dataset_pos_weight_and_prevalence():
    """Kiểm tra thuộc tính pos_weight và prevalence tính toán chính xác."""
    df = pd.DataFrame({
        "text": ["a", "b", "c", "d", "e"],
        "label": [0, 0, 0, 0, 1],  # 4 neg, 1 pos
    })
    ds = ToxicityDataset(df, tokenizer=MockTokenizer())
    assert ds.pos_weight == 4.0
    assert ds.prevalence == pytest.approx(0.2)


def test_weighted_bce_loss_no_nan_and_stability():
    """Kiểm tra WeightedBCEWithLogitsLoss không bị NaN với logit cực đoan và phạt nặng pos_weight."""
    loss_fn = WeightedBCEWithLogitsLoss(pos_weight=5.0)

    # Logit cực đoan: -100, +100
    logits = torch.tensor([-100.0, -10.0, 0.0, 10.0, 100.0], requires_grad=True)
    targets = torch.tensor([0.0, 1.0, 0.0, 1.0, 1.0])

    loss = loss_fn(logits, targets)

    assert not torch.isnan(loss)
    assert not torch.isinf(loss)
    assert loss.item() > 0.0

    # Kiểm tra backward gradient tính toán ổn định, không NaN
    loss.backward()
    assert logits.grad is not None
    assert not torch.isnan(logits.grad).any()
    assert not torch.isinf(logits.grad).any()


def test_weighted_bce_pos_weight_penalizes_minority_false_negatives():
    """Kiểm tra pos_weight > 1 phạt nặng FN hơn FP."""
    w_loss = WeightedBCEWithLogitsLoss(pos_weight=4.0)
    unweighted = WeightedBCEWithLogitsLoss(pos_weight=1.0)

    # False negative: target=1 nhưng logit=-3.0
    fn_logit = torch.tensor([-3.0])
    fn_target = torch.tensor([1.0])

    # False positive: target=0 nhưng logit=+3.0
    fp_logit = torch.tensor([3.0])
    fp_target = torch.tensor([0.0])

    # Unweighted BCE: lỗi đối xứng nên loss FN == loss FP
    torch.testing.assert_close(unweighted(fn_logit, fn_target), unweighted(fp_logit, fp_target))

    # Weighted BCE: FN bị nhân trọng số 4.0, cao hơn rõ rệt so với FP
    loss_fn_val = w_loss(fn_logit, fn_target).item()
    loss_fp_val = w_loss(fp_logit, fp_target).item()
    assert loss_fn_val > 3.0 * loss_fp_val


def test_focal_loss_no_nan_and_stability():
    """Kiểm tra FocalLoss (Alpha=0.75, Gamma=2.0) không bị NaN với mọi dải giá trị logit."""
    focal = FocalLoss(alpha=0.75, gamma=2.0)

    # Thử nghiệm dải rộng từ -500 đến +500
    logits = torch.tensor([-500.0, -100.0, -5.0, 0.0, 5.0, 100.0, 500.0], requires_grad=True)
    targets = torch.tensor([0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 1.0])

    loss = focal(logits, targets)

    assert not torch.isnan(loss)
    assert not torch.isinf(loss)
    assert loss.item() >= 0.0

    # Gradient backward kiểm tra tính liên tục và không tràn số
    loss.backward()
    assert logits.grad is not None
    assert not torch.isnan(logits.grad).any()
    assert not torch.isinf(logits.grad).any()


def test_focal_loss_downweights_easy_examples():
    """Kiểm tra tính chất cơ bản của Focal Loss: giảm mạnh tổn thất ở mẫu dễ phân loại."""
    focal = FocalLoss(alpha=0.5, gamma=2.0, reduction="none")
    bce = nn.BCEWithLogitsLoss(reduction="none")

    # Mẫu dễ: logit=+5.0, target=1 (xác suất ~ 0.993)
    easy_logit = torch.tensor([5.0])
    easy_target = torch.tensor([1.0])

    # Mẫu khó: logit=0.0, target=1 (xác suất 0.5)
    hard_logit = torch.tensor([0.0])
    hard_target = torch.tensor([1.0])

    bce_easy = bce(easy_logit, easy_target)
    focal_easy = focal(easy_logit, easy_target)

    bce_hard = bce(hard_logit, hard_target)
    focal_hard = focal(hard_logit, hard_target)

    # Tỷ lệ focal/bce ở mẫu dễ phải nhỏ hơn nhiều so với mẫu khó
    ratio_easy = (focal_easy / bce_easy).item()
    ratio_hard = (focal_hard / bce_hard).item()
    assert ratio_easy < 0.01 * ratio_hard


def test_strictly_forbid_reading_locked_data(tmp_path):
    """Bảo mật split A3: Kiểm tra hệ thống ném PermissionError khi trỏ tới bất kỳ đường dẫn nào chứa 'locked'."""
    locked_dir = tmp_path / "data" / "locked" / "H1"
    locked_dir.mkdir(parents=True)
    fake_locked_file = locked_dir / "labels_headB_test.parquet"
    fake_locked_file.touch()

    with pytest.raises(PermissionError, match="Tuyệt đối không được đọc dữ liệu từ data/locked"):
        load_h1_train_data(labels_path=fake_locked_file)

    with pytest.raises(PermissionError, match="Tuyệt đối không được đọc dữ liệu từ data/locked"):
        load_h2_train_data(labels_path=fake_locked_file)


def test_load_h1_and_h2_train_data_real_or_fixture(tmp_path):
    """Kiểm tra nạp dữ liệu huấn luyện H1 và H2 (dùng file đã xử lý hoặc mock fixture)."""
    h1_path = Path("data/processed/H1/labels_headB_train.parquet")
    h2_path = Path("data/processed/H2/labels_headB_train.parquet")

    if h1_path.exists():
        df_h1 = load_h1_train_data()
        assert len(df_h1) == 26719
        assert "text" in df_h1.columns
        assert "label" in df_h1.columns
        assert "toxic" in df_h1.columns
        assert df_h1["label"].isna().sum() == 0
        assert df_h1["label"].dtype in (int, np.int64, np.int32)
        assert df_h1["label"].sum() == 4620

    if h2_path.exists():
        df_h2 = load_h2_train_data()
        assert len(df_h2) == 8001
        assert "text" in df_h2.columns
        assert "label" in df_h2.columns
        assert "toxic" in df_h2.columns
        assert df_h2["label"].isna().sum() == 0
        assert df_h2["label"].dtype in (int, np.int64, np.int32)
        assert df_h2["label"].sum() == 882

    # Thử nghiệm thêm trên dummy directory
    dummy_labels = pd.DataFrame({
        "conversation_id": ["c1", "c2"],
        "toxic": [0, 1],
    })
    dummy_msgs = pd.DataFrame({
        "conversation_id": ["c1", "c2"],
        "text": ["bình thường", "xúc phạm nặng"],
    })
    lp = tmp_path / "labels_headB_train.parquet"
    mp = tmp_path / "messages.parquet"
    dummy_labels.to_parquet(lp, index=False)
    dummy_msgs.to_parquet(mp, index=False)

    loaded = load_h1_train_data(labels_path=lp, messages_path=mp)
    assert len(loaded) == 2
    assert loaded.loc[0, "text"] == "bình thường"
    assert loaded.loc[1, "label"] == 1


def test_toxicity_dataset_with_phobert_tokenizer():
    """Kiểm tra tích hợp trực tiếp với AutoTokenizer PhoBERT thực tế."""
    try:
        from transformers import AutoTokenizer
        tok = AutoTokenizer.from_pretrained("vinai/phobert-base-v2")
    except Exception:
        pytest.skip("PhoBERT tokenizer không khả dụng trong môi trường offline.")

    df = pd.DataFrame({
        "text": ["Chào bạn, hôm nay thời tiết đẹp quá.", "Mày là đồ ngu ngốc mất dạy."],
        "label": [0, 1],
    })
    ds = ToxicityDataset(df, tokenizer=tok, max_length=128)
    assert len(ds) == 2

    item0 = ds[0]
    assert item0["input_ids"].shape == (128,)
    assert item0["attention_mask"].shape == (128,)
    assert item0["label"].item() == 0.0

    item1 = ds[1]
    assert item1["input_ids"].shape == (128,)
    assert item1["attention_mask"].shape == (128,)
    assert item1["label"].item() == 1.0

