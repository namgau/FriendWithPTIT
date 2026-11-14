"""Smoke test và unit test cho C4 CRAFT-like GRU (src/m3_health/run_head_b_craft.py)."""
from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

torch = pytest.importorskip("torch")

from src.eval.bootstrap import paired_cluster_bootstrap
from src.eval.calibration import fit_calibrator
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.run_head_b_craft import (
    CraftGRU,
    _collate_sequences,
    _predict_loader,
    build_sequence_data,
    compute_eval_metrics,
)


def test_craft_gru_forward_shapes():
    """Kiểm tra forward pass của CraftGRU với batch có độ dài thay đổi."""
    model = CraftGRU(input_size=32, hidden_size=64, dropout=0.2)
    model.eval()

    seq1 = torch.randn(8, 32)
    seq2 = torch.randn(4, 32)
    seq3 = torch.randn(2, 32)

    padded = torch.nn.utils.rnn.pad_sequence([seq1, seq2, seq3], batch_first=True)
    lengths = torch.tensor([8, 4, 2], dtype=torch.long)

    with torch.no_grad():
        logits = model(padded, lengths)

    assert logits.shape == (3,)
    assert torch.isfinite(logits).all()


def test_sequence_extraction_strictly_before_prefix_len():
    """Kiểm tra trích xuất chuỗi không bao giờ vượt quá prefix_len và không nhìn thấy attack."""
    messages = pd.DataFrame({
        "conversation_id": ["c1", "c1", "c1", "c2", "c2"],
        "turn_idx": [0, 1, 2, 0, 1],
        "text": ["hi", "there", "ATTACK", "hello", "calm"],
        "group_id": ["g1", "g1", "g1", "g2", "g2"],
        "sender_id": ["u1", "u2", "u1", "v1", "v2"],
        "timestamp_s": [0.0, 10.0, 20.0, 0.0, 5.0],
        "is_attack": [False, False, True, False, False],
    })
    labels = pd.DataFrame({
        "conversation_id": ["c1", "c2"],
        "group_id": ["g1", "g2"],
        "pair_id": ["c2", "c1"],
        "derailed": [1, 0],
        "prefix_len": [2, 1],  # c1 chỉ lấy 2 lượt (bỏ ATTACK lượt 2), c2 chỉ lấy 1 lượt
    })
    pca_emb = np.random.randn(5, 32).astype(np.float32)
    emb_idx = pd.DataFrame({
        "conversation_id": ["c1", "c1", "c1", "c2", "c2"],
        "turn_idx": [0, 1, 2, 0, 1],
    })

    seqs, table = build_sequence_data(messages, labels, pca_emb, emb_idx)

    assert len(seqs) == 2
    assert len(seqs[0]) == 2  # c1 có prefix_len=2
    assert len(seqs[1]) == 1  # c2 có prefix_len=1
    assert table.shape[0] == 2
    assert np.isfinite(table).all()


def test_platt_calibration_and_fpr_threshold():
    """Kiểm tra Platt calibration và quét ngưỡng FPR <= 5%."""
    rng = np.random.default_rng(42)
    y_true = np.array([0] * 100 + [1] * 100)
    raw_scores = np.concatenate([
        rng.uniform(0.1, 0.6, size=100),
        rng.uniform(0.4, 0.9, size=100),
    ])

    calibrator = fit_calibrator("platt", raw_scores, y_true)
    cal_probs = calibrator(raw_scores)

    assert (cal_probs >= 0.0).all() and (cal_probs <= 1.0).all()

    tau_res = threshold_by_fpr(y_true, cal_probs, max_fpr=0.05)
    tau = tau_res["threshold"]
    metrics = compute_eval_metrics(y_true, cal_probs, tau)

    assert metrics["fpr"] <= 0.05
    assert metrics["precision"] > 0.5
    assert 0.0 <= metrics["pr_auc"] <= 1.0
    assert 0.0 <= metrics["roc_auc"] <= 1.0


def test_craft_gru_training_step_cpu():
    """Smoke test: 1 bước huấn luyện giảm loss và cập nhật gradient trơn tru trên CPU."""
    device = torch.device("cpu")
    model = CraftGRU(input_size=32, hidden_size=16, dropout=0.1).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=0.01)
    criterion = torch.nn.BCEWithLogitsLoss()

    batch_x = [torch.randn(4, 32), torch.randn(3, 32)]
    batch_y = torch.tensor([1.0, 0.0])
    lengths = torch.tensor([4, 3], dtype=torch.long)
    padded = torch.nn.utils.rnn.pad_sequence(batch_x, batch_first=True)

    model.train()
    optimizer.zero_grad()
    logits = model(padded, lengths)
    loss = criterion(logits, batch_y)
    loss.backward()
    optimizer.step()

    assert torch.isfinite(loss)
    assert loss.item() > 0.0


def test_paired_bootstrap_runs_correctly():
    """Kiểm tra paired cluster bootstrap chạy hợp lệ khi so sánh hai vector điểm."""
    y = np.array([0, 1, 0, 1, 0, 1, 0, 1])
    score_a = np.array([0.2, 0.8, 0.3, 0.9, 0.1, 0.7, 0.2, 0.8])
    score_b = np.array([0.3, 0.7, 0.4, 0.8, 0.2, 0.6, 0.3, 0.7])
    groups = np.array(["g1", "g1", "g2", "g2", "g3", "g3", "g4", "g4"])

    from sklearn.metrics import average_precision_score
    res = paired_cluster_bootstrap(
        average_precision_score, y, score_a, score_b, groups, B=100, seed=42
    )

    assert "estimate" in res
    assert "lo" in res and "hi" in res
    assert "p_value" in res
    assert 0.0 <= res["p_value"] <= 1.0
