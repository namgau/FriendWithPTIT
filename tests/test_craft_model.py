"""Unit tests for CRAFT-like Model Architecture (Head B).

Tests:
1. Output shapes for various batch configurations and pooling modes.
2. Backward pass gradients existence and finiteness.
3. Strict padding invariance: modifying embeddings at padded positions must not
   alter the model logits or extracted features.
4. Bahdanau attention weight distribution and masking properties.
5. Probability outputs and edge cases (T=1, 2D input, invalid configurations).
"""

import pytest

torch = pytest.importorskip("torch")
nn = pytest.importorskip("torch.nn")

from src.m3_health.craft_model import BahdanauAttention, CRAFTModel


def test_craft_model_shapes():
    """Kiểm tra output shape (B, 1) với các batch giả lập và chế độ pooling."""
    torch.manual_seed(42)

    # 1. Standard batch with pooling='last'
    B, T, D = 4, 8, 1024
    model_last = CRAFTModel(input_dim=D, proj_dim=256, hidden_dim=128, pooling="last")
    x = torch.randn(B, T, D)
    lengths = torch.tensor([1, 3, 5, 8])

    out_last = model_last(x, lengths=lengths)
    assert out_last.shape == (B, 1), f"Expected shape ({B}, 1), got {out_last.shape}"

    # 2. Standard batch with pooling='attention'
    model_attn = CRAFTModel(input_dim=D, proj_dim=256, hidden_dim=128, pooling="attention")
    out_attn = model_attn(x, lengths=lengths)
    assert out_attn.shape == (B, 1), f"Expected shape ({B}, 1), got {out_attn.shape}"

    # 3. Batch with lengths=None (all sequences at full length T)
    out_full = model_last(x)
    assert out_full.shape == (B, 1)

    # 4. Minimal batch: B=1, T=1
    x_single = torch.randn(1, 1, D)
    out_single = model_last(x_single, lengths=[1])
    assert out_single.shape == (1, 1)

    out_single_attn = model_attn(x_single, lengths=[1])
    assert out_single_attn.shape == (1, 1)

    # 5. Direct padding_mask input (boolean: True = padding)
    model_eval = CRAFTModel(input_dim=D, proj_dim=256, hidden_dim=128, pooling="last")
    model_eval.eval()
    with torch.no_grad():
        out_eval_lengths = model_eval(x, lengths=lengths)
        mask = torch.tensor([
            [False, True, True, True, True, True, True, True],
            [False, False, False, True, True, True, True, True],
            [False, False, False, False, False, True, True, True],
            [False, False, False, False, False, False, False, False],
        ])
        out_eval_mask = model_eval(x, padding_mask=mask)
        assert out_eval_mask.shape == (B, 1)
        assert torch.allclose(out_eval_mask, out_eval_lengths, atol=1e-5)

    # 6. 2D input (B, D) treated as single-turn sequence
    x_2d = torch.randn(3, D)
    out_2d = model_last(x_2d)
    assert out_2d.shape == (3, 1)

    # 7. Feature extractor returns (B, hidden_dim)
    feats = model_last.extract_features(x, lengths=lengths)
    assert feats.shape == (B, 128)


def test_craft_model_backward():
    """Kiểm tra backward pass, gradients tồn tại và hữu hạn."""
    torch.manual_seed(42)
    B, T, D = 4, 6, 1024
    criterion = nn.BCEWithLogitsLoss()
    targets = torch.tensor([[1.0], [0.0], [1.0], [0.0]])
    lengths = torch.tensor([2, 6, 1, 4])

    for pooling_mode in ["last", "attention"]:
        model = CRAFTModel(
            input_dim=D,
            proj_dim=256,
            hidden_dim=128,
            pooling=pooling_mode,
            proj_dropout=0.2,
            classifier_dropout=0.2,
        )
        model.train()

        x = torch.randn(B, T, D, requires_grad=True)
        logits = model(x, lengths=lengths)
        loss = criterion(logits, targets)
        loss.backward()

        # Check model parameters gradients
        for name, param in model.named_parameters():
            if pooling_mode == "last" and "attention" in name:
                # Attention sub-module unused in 'last' pooling mode
                continue
            assert param.grad is not None, f"Parameter {name} has no gradient in mode {pooling_mode}"
            assert torch.isfinite(param.grad).all(), f"Parameter {name} grad has non-finite values"

        # Check input gradients exist and are finite
        assert x.grad is not None, f"Input x has no gradient in mode {pooling_mode}"
        assert torch.isfinite(x.grad).all(), f"Input x grad has non-finite values in mode {pooling_mode}"

        # Verify gradient on padded positions is strictly zero (due to packed sequence)
        for i in range(B):
            valid_len = lengths[i].item()
            if valid_len < T:
                pad_grad = x.grad[i, valid_len:]
                assert pad_grad.abs().max().item() == 0.0, (
                    f"Padding position gradient must be 0 in mode {pooling_mode} for sample {i}"
                )


def test_craft_model_padding_invariance():
    """Kiểm tra thay đổi embedding ở vị trí padding không làm đổi logit của chuỗi."""
    torch.manual_seed(42)
    B, T, D = 4, 7, 1024
    lengths = torch.tensor([2, 7, 1, 4])

    x_orig = torch.randn(B, T, D)
    x_corrupted = x_orig.clone()

    # Heavily corrupt embeddings strictly at padding positions
    x_corrupted[0, 2:] = 999.0
    x_corrupted[2, 1:] = -1234.5
    x_corrupted[3, 4:] = 42.0

    for pooling_mode in ["last", "attention"]:
        model = CRAFTModel(
            input_dim=D,
            proj_dim=256,
            hidden_dim=128,
            pooling=pooling_mode,
            proj_dropout=0.0,
            classifier_dropout=0.0,
        )
        model.eval()

        with torch.no_grad():
            out_orig = model(x_orig, lengths=lengths)
            out_corrupted = model(x_corrupted, lengths=lengths)

            diff = torch.max(torch.abs(out_orig - out_corrupted)).item()
            assert diff == 0.0, (
                f"Padding invariance violated in {pooling_mode} pooling: max diff = {diff}"
            )

            # Also check extracted features invariance
            feats_orig = model.extract_features(x_orig, lengths=lengths)
            feats_corrupted = model.extract_features(x_corrupted, lengths=lengths)
            feat_diff = torch.max(torch.abs(feats_orig - feats_corrupted)).item()
            assert feat_diff == 0.0, (
                f"Extracted features invariance violated in {pooling_mode}: max diff = {feat_diff}"
            )


def test_craft_model_attention_weights():
    """Kiểm tra cơ chế Bahdanau attention: trọng số hợp lệ sum=1 và padding=0."""
    torch.manual_seed(42)
    B, T, D = 3, 5, 1024
    lengths = torch.tensor([2, 5, 1])

    model = CRAFTModel(input_dim=D, pooling="attention")
    model.eval()

    x = torch.randn(B, T, D)
    with torch.no_grad():
        logits, weights = model(x, lengths=lengths, return_attention=True)

    assert weights.shape == (B, T)

    # Check that padding weights are strictly 0.0
    for i in range(B):
        valid_len = lengths[i].item()
        if valid_len < T:
            pad_weights = weights[i, valid_len:]
            assert torch.all(pad_weights == 0.0), f"Sample {i} has non-zero padding weights: {pad_weights}"

        # Check valid weights sum to 1.0
        valid_sum = weights[i, :valid_len].sum().item()
        assert abs(valid_sum - 1.0) < 1e-5, f"Sample {i} weights do not sum to 1.0: {valid_sum}"


def test_craft_model_predict_proba():
    """Kiểm tra predict_proba trả về xác suất hợp lệ trong [0, 1]."""
    torch.manual_seed(42)
    model = CRAFTModel(input_dim=1024)
    model.eval()

    x = torch.randn(5, 4, 1024)
    probs = model.predict_proba(x)

    assert probs.shape == (5, 1)
    assert (probs >= 0.0).all() and (probs <= 1.0).all()


def test_craft_model_invalid_pooling():
    """Kiểm tra quăng lỗi ValueError khi cấu hình pooling không hợp lệ."""
    with pytest.raises(ValueError, match="Invalid pooling mode"):
        CRAFTModel(pooling="max")
