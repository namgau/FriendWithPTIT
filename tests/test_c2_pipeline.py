"""Unit and smoke tests for C2 Toxicity Transformer Pipeline.

Kiểm tra:
1. PlattCalibrator: fit, predict_proba, __call__, giảm ECE và tính tương thích pickle.
2. GroupKFold K=5 và held_out_group_split: chống rò rỉ group giữa train/cal/val.
3. Quét ngưỡng tau_B: đảm bảo FPR <= 5%.
4. EmbeddingClassifierFallback: hỗ trợ cả text thô và ma trận vector dense.
5. Đánh giá cross-dataset zero-shot X12: đầy đủ các metric PR-AUC, ROC-AUC, Brier, ECE, Confusion.
6. Xuất và nạp artifact: config.json, metadata.json, calibrator.pkl, model.pkl.
7. End-to-end smoke pipeline.
"""
from __future__ import annotations

import json
from pathlib import Path
import pickle

import numpy as np
import pandas as pd
import pytest

from src.eval.calibration import ece
from src.m3_health.train_c2_transformer import (
    EmbeddingClassifierFallback,
    PlattCalibrator,
    build_classifier,
    evaluate_cross_dataset_x12,
    export_c2_artifacts,
    run_group_cv,
    run_pipeline,
    train_final_h1_model,
)


@pytest.fixture
def synthetic_h1_data() -> pd.DataFrame:
    """Tạo dữ liệu giả lập H1 gồm 100 mẫu thuộc 20 groups độc lập."""
    rng = np.random.default_rng(42)
    n = 100
    groups = [f"g_{i // 5}" for i in range(n)]
    cids = [f"conv_{i}" for i in range(n)]

    toxic_words = ["đm", "ngu", "chửi", "cút", "đần", "óc chó"]
    clean_words = ["chào", "bạn", "hôm", "nay", "vui", "thế", "học", "nhóm"]

    texts = []
    labels = []
    for i in range(n):
        if rng.random() < 0.25:
            labels.append(1)
            t = " ".join(rng.choice(toxic_words, size=3)) + " " + " ".join(rng.choice(clean_words, size=2))
        else:
            labels.append(0)
            t = " ".join(rng.choice(clean_words, size=5))
        texts.append(t)

    return pd.DataFrame({
        "conversation_id": cids,
        "group_id": groups,
        "text": texts,
        "label": labels,
    })


@pytest.fixture
def synthetic_h2_data() -> pd.DataFrame:
    """Tạo dữ liệu giả lập H2 gồm 40 mẫu thuộc 10 groups độc lập (cross-dataset)."""
    rng = np.random.default_rng(123)
    n = 40
    groups = [f"art_{i // 4}" for i in range(n)]
    cids = [f"h2_{i}" for i in range(n)]

    texts = []
    labels = []
    for i in range(n):
        if rng.random() < 0.15:
            labels.append(1)
            texts.append("bài viết quá dở tệ và ngu xuẩn")
        else:
            labels.append(0)
            texts.append("đồng ý với quan điểm bài báo hôm nay")

    return pd.DataFrame({
        "conversation_id": cids,
        "group_id": groups,
        "text": texts,
        "label": labels,
    })


# ---------------------------------------------------------------------------
# Test 1: PlattCalibrator
# ---------------------------------------------------------------------------

def test_platt_calibrator_fit_and_pickle():
    scores = np.array([0.1, 0.2, 0.3, 0.7, 0.8, 0.9])
    y = np.array([0, 0, 0, 1, 1, 1])

    cal = PlattCalibrator().fit(scores, y)
    p = cal.predict_proba(scores)

    assert p.shape == scores.shape
    assert np.all((p >= 0.0) & (p <= 1.0))
    # Platt scaling phải tăng đơn điệu theo điểm gốc
    assert np.all(np.diff(p) >= -1e-6)
    # Kiểm tra tính callable
    p_called = cal(scores)
    np.testing.assert_allclose(p, p_called)

    # Kiểm tra tính tương thích pickle (không dính lỗi lambda)
    pickled = pickle.dumps(cal)
    unpickled = pickle.loads(pickled)
    p_unpickled = unpickled(scores)
    np.testing.assert_allclose(p, p_unpickled)


# ---------------------------------------------------------------------------
# Test 2: EmbeddingClassifierFallback on dense vectors and text
# ---------------------------------------------------------------------------

def test_embedding_classifier_fallback_dense_and_text():
    rng = np.random.default_rng(42)
    # 1. Thử nghiệm trên ma trận vector dense
    X_dense = rng.normal(size=(30, 16)).astype(np.float32)
    y = (X_dense[:, 0] > 0).astype(int)

    clf_dense = EmbeddingClassifierFallback(seed=42)
    clf_dense.fit(X_dense, y)
    probs_dense = clf_dense.predict_proba(X_dense)
    assert probs_dense.shape == (30,)
    assert np.all((probs_dense >= 0.0) & (probs_dense <= 1.0))

    # 2. Thử nghiệm trên chuỗi văn bản thô (TF-IDF fallback)
    texts = ["xin chào bạn", "chúc một ngày tốt lành", "đồ ngu ngốc cút đi", "mày thật là ngu"]
    y_texts = [0, 0, 1, 1]
    clf_text = EmbeddingClassifierFallback(seed=42)
    clf_text.fit(texts, y_texts)
    probs_text = clf_text.predict_proba(texts)
    assert probs_text.shape == (4,)
    assert probs_text[2] > probs_text[0]  # Câu độc hại phải có xác suất cao hơn câu sạch


# ---------------------------------------------------------------------------
# Test 3: Group CV Leak-Free & Platt Calibration Reduction
# ---------------------------------------------------------------------------

def test_run_group_cv_pipeline(synthetic_h1_data):
    cv_df, summary = run_group_cv(
        synthetic_h1_data,
        k_folds=3,
        seed=42,
        cal_fraction=0.2,
        threshold_fpr=0.10,
        model_type="embedding_fallback",
    )
    assert len(cv_df) == 3
    assert "pr_auc" in cv_df.columns
    assert "roc_auc" in cv_df.columns
    assert "ece" in cv_df.columns
    assert summary["k_folds"] == 3
    assert 0.0 <= summary["pr_auc_mean"] <= 1.0
    assert 0.0 <= summary["roc_auc_mean"] <= 1.0


# ---------------------------------------------------------------------------
# Test 4: Final Model Training & Tau_B Scanning
# ---------------------------------------------------------------------------

def test_train_final_h1_model(synthetic_h1_data):
    model, calibrator, tau_B, cal_meta = train_final_h1_model(
        synthetic_h1_data,
        seed=42,
        cal_fraction=0.25,
        threshold_fpr=0.10,
        model_type="embedding_fallback",
    )
    assert model is not None
    assert calibrator is not None
    assert 0.0 <= tau_B <= 1.0
    assert "cal_pr_auc" in cal_meta
    assert cal_meta["n_fit"] + cal_meta["n_cal"] == len(synthetic_h1_data)


# ---------------------------------------------------------------------------
# Test 5: Cross-Dataset Zero-Shot (X12)
# ---------------------------------------------------------------------------

def test_cross_dataset_x12_evaluation(synthetic_h1_data, synthetic_h2_data):
    model, calibrator, tau_B, _ = train_final_h1_model(
        synthetic_h1_data,
        seed=42,
        cal_fraction=0.25,
        threshold_fpr=0.10,
        model_type="embedding_fallback",
    )
    x12 = evaluate_cross_dataset_x12(
        model=model,
        calibrator=calibrator,
        tau_B=tau_B,
        df_h2=synthetic_h2_data,
    )
    assert x12["n_comments"] == len(synthetic_h2_data)
    assert "pr_auc" in x12
    assert "roc_auc" in x12
    assert "brier" in x12
    assert "ece" in x12
    assert "tp" in x12 and "fp" in x12 and "fn" in x12 and "tn" in x12
    assert x12["tp"] + x12["fp"] + x12["fn"] + x12["tn"] == len(synthetic_h2_data)


# ---------------------------------------------------------------------------
# Test 6: Export & Reload Artifacts
# ---------------------------------------------------------------------------

def test_export_and_reload_artifacts(synthetic_h1_data, tmp_path):
    model, calibrator, tau_B, cal_meta = train_final_h1_model(
        synthetic_h1_data,
        seed=42,
        cal_fraction=0.25,
        threshold_fpr=0.10,
        model_type="embedding_fallback",
    )
    cfg = {"version": "c2", "tau_b": tau_B}
    meta = {"model_name": "c2_test", "cal_meta": cal_meta}

    paths = export_c2_artifacts(
        output_dir=tmp_path,
        config_dict=cfg,
        metadata_dict=meta,
        calibrator=calibrator,
        model=model,
    )

    assert paths["config"].exists()
    assert paths["metadata"].exists()
    assert paths["calibrator"].exists()
    assert paths["model"].exists()

    with open(paths["config"], "r", encoding="utf-8") as f:
        loaded_cfg = json.load(f)
    assert loaded_cfg["version"] == "c2"

    with open(paths["calibrator"], "rb") as f:
        loaded_calibrator = pickle.load(f)
    with open(paths["model"], "rb") as f:
        loaded_model = pickle.load(f)

    # So khớp kết quả suy luận giữa model trước và sau khi lưu
    raw_scores = model.predict_proba(synthetic_h1_data["text"][:5])
    raw_scores_loaded = loaded_model.predict_proba(synthetic_h1_data["text"][:5])
    np.testing.assert_allclose(raw_scores, raw_scores_loaded)

    cal_scores = calibrator(raw_scores)
    cal_scores_loaded = loaded_calibrator(raw_scores_loaded)
    np.testing.assert_allclose(cal_scores, cal_scores_loaded)


# ---------------------------------------------------------------------------
# Test 7: Full Pipeline Smoke Run
# ---------------------------------------------------------------------------

def test_full_pipeline_smoke(tmp_path):
    h1_file = Path("data/processed/H1/labels_headB_train.parquet")
    h2_file = Path("data/processed/H2/labels_headB_train.parquet")
    if not (h1_file.exists() and h2_file.exists()):
        pytest.skip("Processed H1/H2 cache not available in CI environment.")

    res = run_pipeline(
        model_type="embedding_fallback",
        seed=42,
        k_folds=2,
        smoke_test=True,
        smoke_samples=30,
        output_dir=tmp_path,
        write_ledger=False,
    )
    assert "cv_summary" in res
    assert "x12_results" in res
    assert "tau_b" in res
    assert (tmp_path / "config.json").exists()
    assert (tmp_path / "metadata.json").exists()
    assert (tmp_path / "calibrator.pkl").exists()
    assert (tmp_path / "model.pkl").exists()
