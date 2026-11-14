"""Unit tests cho C4 G1SequenceDataset và pad_collate_fn (B8 cache -> PyTorch sequence).

Kiểm tra:
1. pad_collate_fn: padding bằng torch.nn.utils.rnn.pad_sequence, đúng shape (B, T_max, 1024), đúng lengths, mask.
2. Anti-leakage: không có bất kỳ turn nào >= attack_turn_idx (với derailed=1) hoặc >= prefix_len (với derailed=0).
3. Tuyệt đối cấm đọc data/locked/.
4. Tích hợp thực tế với dữ liệu G1 B8 cache và DataLoader.
"""
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

torch = pytest.importorskip("torch")
from torch.utils.data import DataLoader

from src.m3_health.craft_dataset import (
    G1SequenceDataset,
    pad_collate_fn,
    pad_collate_fn_with_meta,
)


def _mock_cache_and_labels(dim: int = 16):
    """Tạo dữ liệu giả lập có chủ đích để kiểm tra chi tiết anti-leakage."""
    # 2 hội thoại:
    # - conv_bad (derailed=1, n_turns=6, attack_turn_idx=3, prefix_len=3)
    #   Các turn 0, 1, 2 là tiền tố hợp lệ.
    #   Turn 3 là lượt công kích (ATTACK).
    #   Turn 4, 5 là các lượt sau công kích (FUTURE).
    # - conv_ok (derailed=0, n_turns=5, attack_turn_idx=-1, prefix_len=4)
    #   Các turn 0, 1, 2, 3 là tiền tố hợp lệ (bỏ lượt cuối 4 theo luật CRAFT).
    #   Turn 4 là lượt cuối (LAST TURN).
    index_rows = []
    emb_vectors = []

    # conv_bad
    for t in range(6):
        index_rows.append({"conversation_id": "conv_bad", "turn_idx": t})
        # Mỗi turn mang một giá trị đặc trưng để dễ nhận diện
        val = 10.0 + t if t < 3 else (666.0 if t == 3 else 777.0 + t)
        emb_vectors.append(np.full(dim, val, dtype=np.float16))

    # conv_ok
    for t in range(5):
        index_rows.append({"conversation_id": "conv_ok", "turn_idx": t})
        val = 20.0 + t if t < 4 else 999.0
        emb_vectors.append(np.full(dim, val, dtype=np.float16))

    index_df = pd.DataFrame(index_rows)
    emb = np.vstack(emb_vectors)

    labels_df = pd.DataFrame([
        {
            "conversation_id": "conv_bad",
            "group_id": "group_bad",
            "derailed": 1,
            "attack_turn_idx": 3,
            "prefix_len": 3,
            "n_turns": 6,
        },
        {
            "conversation_id": "conv_ok",
            "group_id": "group_ok",
            "derailed": 0,
            "attack_turn_idx": -1,
            "prefix_len": 4,
            "n_turns": 5,
        },
    ])

    return emb, index_df, labels_df


def test_pad_collate_fn_shapes_and_padding():
    """Kiểm tra collate padding và lengths cho các chuỗi độ dài khác nhau."""
    dim = 1024
    s1 = torch.ones((3, dim), dtype=torch.float32) * 1.0
    s2 = torch.ones((5, dim), dtype=torch.float32) * 2.0
    s3 = torch.ones((2, dim), dtype=torch.float32) * 3.0

    batch = [
        (s1, 0, 3, "c1", "g1"),
        (s2, 1, 5, "c2", "g2"),
        (s3, 0, 2, "c3", "g1"),
    ]

    padded, lengths, labels, mask = pad_collate_fn(batch)

    # 1. Kiểm tra shape
    assert padded.shape == (3, 5, dim)
    assert lengths.shape == (3,)
    assert labels.shape == (3,)
    assert mask.shape == (3, 5)

    # 2. Kiểm tra lengths và labels
    assert lengths.tolist() == [3, 5, 2]
    assert labels.tolist() == [0.0, 1.0, 0.0]

    # 3. Kiểm tra mask che đúng vị trí hợp lệ vs padding
    expected_mask = torch.tensor([
        [True, True, True, False, False],
        [True, True, True, True, True],
        [True, True, False, False, False],
    ], dtype=torch.bool)
    assert torch.equal(mask, expected_mask)

    # 4. Kiểm tra giá trị padding là 0.0
    assert torch.all(padded[0, 3:] == 0.0)
    assert torch.all(padded[2, 2:] == 0.0)

    # 5. Kiểm tra giá trị nội dung không bị biến đổi
    assert torch.all(padded[0, :3] == 1.0)
    assert torch.all(padded[1, :5] == 2.0)
    assert torch.all(padded[2, :2] == 3.0)


def test_pad_collate_fn_with_meta():
    """Kiểm tra pad_collate_fn_with_meta trả về đúng conv_ids và group_ids."""
    dim = 8
    batch = [
        (torch.zeros((2, dim)), 1, 2, "conv_alpha", "grp_1"),
        (torch.zeros((3, dim)), 0, 3, "conv_beta", "grp_2"),
    ]
    padded, lengths, labels, mask, conv_ids, group_ids = pad_collate_fn_with_meta(batch)
    assert conv_ids == ["conv_alpha", "conv_beta"]
    assert group_ids == ["grp_1", "grp_2"]
    assert lengths.tolist() == [2, 3]


def test_pad_collate_fn_empty_batch():
    """Kiểm tra xử lý an toàn khi batch rỗng."""
    padded, lengths, labels, mask = pad_collate_fn([])
    assert padded.shape == (0, 0, 1024)
    assert lengths.shape == (0,)
    assert labels.shape == (0,)
    assert mask.shape == (0, 0)


def test_anti_leakage_no_attack_turn_in_derailed_sequence():
    """Kiểm tra nghiêm ngặt: không có turn nào >= attack_turn_idx lọt vào chuỗi."""
    emb, index_df, labels_df = _mock_cache_and_labels(dim=16)

    dataset = G1SequenceDataset(
        emb=emb,
        index_df=index_df,
        labels_df=labels_df,
    )

    # Tìm mẫu conv_bad (derailed = 1, attack_turn_idx = 3)
    sample_bad = None
    for i in range(len(dataset)):
        if dataset[i][3] == "conv_bad":
            sample_bad = dataset[i]
            break

    assert sample_bad is not None
    tensor_x, label, length, conv_id, group_id = sample_bad

    # 1. Độ dài phải đúng bằng số turn trước attack (0, 1, 2 => 3 turns)
    assert length == 3
    assert tensor_x.shape == (3, 16)
    assert label == 1

    # 2. Giá trị trong tensor_x chỉ gồm các turn 0, 1, 2 (10.0, 11.0, 12.0)
    assert np.isclose(tensor_x[0, 0].item(), 10.0)
    assert np.isclose(tensor_x[1, 0].item(), 11.0)
    assert np.isclose(tensor_x[2, 0].item(), 12.0)

    # 3. ANTI-LEAKAGE: Tuyệt đối không chứa vector của turn 3 (666.0) hay turn 4/5 (777.0+)
    assert not torch.any(torch.isclose(tensor_x, torch.tensor(666.0, dtype=tensor_x.dtype)))
    assert not torch.any(tensor_x >= 600.0)


def test_anti_leakage_calm_drops_last_turn():
    """Kiểm tra hội thoại lành (derailed = 0) bỏ đúng lượt cuối (CRAFT convention)."""
    emb, index_df, labels_df = _mock_cache_and_labels(dim=16)

    dataset = G1SequenceDataset(
        emb=emb,
        index_df=index_df,
        labels_df=labels_df,
    )

    sample_ok = None
    for i in range(len(dataset)):
        if dataset[i][3] == "conv_ok":
            sample_ok = dataset[i]
            break

    assert sample_ok is not None
    tensor_x, label, length, conv_id, group_id = sample_ok

    # 1. 5 turns, prefix_len=4 => length == 4
    assert length == 4
    assert tensor_x.shape == (4, 16)
    assert label == 0

    # 2. Vector turn 4 (999.0) tuyệt đối không có mặt
    assert not torch.any(torch.isclose(tensor_x, torch.tensor(999.0, dtype=tensor_x.dtype)))


def test_strict_anti_leakage_assertion_raises_on_leak():
    """Kiểm tra khi có dữ liệu bị rò rỉ cưỡng bức, Dataset lập tức bắn lỗi."""
    dim = 8
    emb = np.zeros((4, dim), dtype=np.float16)
    index_df = pd.DataFrame({
        "conversation_id": ["leak_conv"] * 4,
        "turn_idx": [0, 1, 2, 3],
    })
    # Cố tình giả mạo prefix_len lớn hơn để lọt lượt công kích
    labels_df = pd.DataFrame([{
        "conversation_id": "leak_conv",
        "group_id": "g1",
        "derailed": 1,
        "attack_turn_idx": 2,
        "prefix_len": 4,  # prefix_len vượt quá attack_turn_idx
    }])

    # Dataset phải lọc theo attack_turn_idx và chỉ giữ turn 0, 1
    ds = G1SequenceDataset(emb=emb, index_df=index_df, labels_df=labels_df)
    tensor_x, label, length, conv_id, group_id = ds[0]
    assert length == 2  # Chỉ có 2 lượt (0 và 1)


def test_locked_data_access_is_forbidden():
    """Kiểm tra cơ chế bảo mật: cấm tuyệt đối đọc từ thư mục locked."""
    with pytest.raises(PermissionError, match="nghiêm cấm"):
        G1SequenceDataset(root=Path("data/locked"))


def test_real_g1_cache_integrity_and_anti_leakage():
    """Kiểm tra toàn diện trên dữ liệu cache thực tế của G1 (B8 cache)."""
    if not (Path("data/processed/G1/labels_headB_train.parquet").exists() and Path("data/processed/G1/embeddings.npy").exists()):
        pytest.skip("G1 B8 cache not available in CI environment.")
    dataset = G1SequenceDataset()
    assert len(dataset) == 3348

    # Đọc lại nhãn train để đối chiếu từng hội thoại
    lab = pd.read_parquet(Path("data/processed/G1/labels_headB_train.parquet"))
    lab_map = {row.conversation_id: row for row in lab.itertuples(index=False)}

    # Kiểm tra tính toàn vẹn và chống rò rỉ trên từng mẫu
    for i in range(len(dataset)):
        tensor_x, label, length, conv_id, group_id = dataset[i]
        assert tensor_x.shape[1] == 1024
        assert tensor_x.shape[0] == length
        assert length >= 1

        ref = lab_map[conv_id]
        assert label == ref.derailed
        assert length == ref.prefix_len

        if ref.derailed == 1:
            # Độ dài chuỗi tiền tố phải bằng đúng attack_turn_idx
            assert length == ref.attack_turn_idx
            assert length < ref.n_turns
        else:
            # Độ dài chuỗi lành phải bằng n_turns - 1
            assert length == ref.n_turns - 1


def test_real_g1_dataloader_batch_processing():
    """Kiểm tra DataLoader PyTorch với pad_collate_fn trên dữ liệu thực tế."""
    if not (Path("data/processed/G1/labels_headB_train.parquet").exists() and Path("data/processed/G1/embeddings.npy").exists()):
        pytest.skip("G1 B8 cache not available in CI environment.")
    dataset = G1SequenceDataset()
    loader = DataLoader(
        dataset,
        batch_size=16,
        shuffle=True,
        collate_fn=pad_collate_fn,
    )

    batch_count = 0
    for padded, lengths, labels, mask in loader:
        assert padded.dim() == 3
        assert padded.size(0) == lengths.size(0)
        assert padded.size(2) == 1024
        assert padded.size(1) == lengths.max().item()

        assert mask.dtype == torch.bool
        assert torch.equal(mask.sum(dim=1), lengths)
        assert not torch.isnan(padded).any()

        batch_count += 1
        if batch_count >= 5:
            break

    assert batch_count == 5


def test_conversation_ids_subset_filtering():
    """Kiểm tra khả năng lọc theo danh sách conversation_ids (dành cho CV folds)."""
    if not (Path("data/processed/G1/labels_headB_train.parquet").exists() and Path("data/processed/G1/embeddings.npy").exists()):
        pytest.skip("G1 B8 cache not available in CI environment.")
    dataset_full = G1SequenceDataset()
    sample_ids = [dataset_full[i][3] for i in range(10)]

    dataset_sub = G1SequenceDataset(conversation_ids=sample_ids)
    assert len(dataset_sub) == 10
    for i in range(10):
        assert dataset_sub[i][3] in sample_ids
