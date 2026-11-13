"""Tests for M3 Serving Integration: Model Registry, Trained Predictors, and FastAPI endpoints.

Covers:
- ModelRegistry: load_head_a, load_head_b, get_thresholds, metadata reading.
- TrainedHealthPredictor: T1/T2 feature extraction, disengagement prediction, fallback mechanism.
- TrainedSafetyPredictor: text feature extraction, toxicity & derailment risk, fallback mechanism.
- FastAPI endpoints: POST /health/score, POST /safety/score, schema verification.
"""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from serving.app import app
from serving.model_registry import ModelRegistry
from serving.predictors import (
    RuleHealthPredictor,
    RuleSafetyPredictor,
    TrainedHealthPredictor,
    TrainedSafetyPredictor,
    extract_safety_features,
    extract_t1_t2_features,
    level_from_p,
)
from serving.schemas import HealthLevel, Message

client = TestClient(app)
H = 3_600_000  # 1 hour in ms


def make_chat(times_h: list[float], texts: list[str], start: int = 0) -> list[dict]:
    return [
        {
            "sender_id": "me" if i % 2 == 0 else "you",
            "timestamp_ms": start + int(t * H),
            "text": txt,
        }
        for i, (t, txt) in enumerate(zip(times_h, texts))
    ]


# -----------------------------------------------------------------------------
# 1. ModelRegistry Tests
# -----------------------------------------------------------------------------

def test_model_registry_load_heads():
    registry = ModelRegistry()
    
    # Thresholds reading
    thresholds = registry.get_thresholds("v1")
    assert "tau1" in thresholds
    assert "tau2" in thresholds
    assert "tau_nudge" in thresholds
    assert 0.0 <= thresholds["tau1"] <= thresholds["tau2"] <= 1.0
    assert 0.0 <= thresholds["tau_nudge"] <= 1.0

    # Load real artifacts
    head_a = registry.load_head_a("v1")
    assert head_a is not None
    assert hasattr(head_a, "predict_proba") or hasattr(head_a, "predict")

    head_b = registry.load_head_b("v1")
    assert head_b is not None
    assert hasattr(head_b, "predict_proba") or hasattr(head_b, "predict")


def test_model_registry_load_c2_and_c4():
    registry = ModelRegistry()
    # If c2 directory exists, load_head_b("c2") loads its artifact
    c2_model = registry.load_head_b("c2")
    if (registry.root / "m3" / "c2").is_dir():
        assert c2_model is not None

    # If c4 directory exists, load_head_b("c4") loads its artifact
    c4_model = registry.load_head_b("c4")
    if (registry.root / "m3" / "c4").is_dir():
        assert c4_model is not None


def test_model_registry_nonexistent_version():
    registry = ModelRegistry()
    assert registry.load_head_a("non_existent_version_xyz") is None
    assert registry.load_head_b("non_existent_version_xyz") is None
    
    # Thresholds should return defaults
    thresholds = registry.get_thresholds("non_existent_version_xyz")
    assert thresholds == {"tau1": 0.3, "tau2": 0.6, "tau_nudge": 0.5}


# -----------------------------------------------------------------------------
# 2. Predictor Unit Tests
# -----------------------------------------------------------------------------

def test_extract_t1_t2_features():
    msgs = [
        Message(sender_id="u1", timestamp_ms=0, text="Chào bạn"),
        Message(sender_id="u2", timestamp_ms=60_000, text="Chào cậu, hôm nay thế nào?"),
        Message(sender_id="u1", timestamp_ms=120_000, text="Tớ ổn, cảm ơn cậu"),
    ]
    df = extract_t1_t2_features(msgs, now_ms=180_000)
    assert len(df) == 1
    assert df["n_msgs"].iloc[0] == 3.0
    assert "gap_last" in df.columns
    assert "words_mean" in df.columns
    assert "short_share" in df.columns


def test_extract_safety_features():
    context = [
        Message(sender_id="u1", timestamp_ms=0, text="Chào bạn"),
    ]
    draft = Message(sender_id="u2", timestamp_ms=60_000, text="Hôm nay thế nào?")
    df = extract_safety_features(context, draft)
    assert len(df) == 1
    assert df["n_messages"].iloc[0] == 2.0
    assert "Chào bạn" in df["text"].iloc[0]
    assert df["last_text"].iloc[0] == "Hôm nay thế nào?"


def test_trained_health_predictor_scoring():
    predictor = TrainedHealthPredictor()
    
    lively_msgs = [
        Message(sender_id="me" if i % 2 == 0 else "you", timestamp_ms=int(t * H), text=txt)
        for i, (t, txt) in enumerate(zip(
            [0, 0.1, 0.2, 0.3, 0.4, 0.5],
            ["hôm nay đi học thế nào", "vui lắm, còn cậu thì sao?", "tớ vừa thi xong môn học máy",
             "chúc mừng nha, khó không?", "khá khó nhưng ổn, cuối tuần cậu rảnh không?", "rảnh nè, đi cà phê nhé"]
        ))
    ]
    
    dying_msgs = [
        Message(sender_id="me" if i % 2 == 0 else "you", timestamp_ms=int(t * H), text=txt)
        for i, (t, txt) in enumerate(zip(
            [0, 2, 3, 9, 10, 20],
            ["hôm nay cậu làm gì vui không?", "ừ", "cuối tuần đi đâu không?", "k", "sao vậy?", "ok"]
        ))
    ]

    p1, factors1 = predictor.score(lively_msgs, now_ms=int(0.6 * H), viewer_id="me")
    p2, factors2 = predictor.score(dying_msgs, now_ms=int(70 * H), viewer_id="me")

    assert p1 < predictor.tau1
    assert p2 >= predictor.tau2
    assert p2 > p1
    assert "long_silence" in factors2 or "short_replies" in factors2


def test_trained_health_predictor_empty_and_fallback():
    predictor = TrainedHealthPredictor()
    p, factors = predictor.score([], now_ms=1000, viewer_id="me")
    assert p == 0.5
    assert factors == ["no_messages"]

    # Test corrupted model fallback
    predictor.model = "corrupted_non_model"
    msgs = [Message(sender_id="u1", timestamp_ms=0, text="test")]
    p_fallback, factors_fallback = predictor.score(msgs, now_ms=1000, viewer_id="me")
    assert 0.0 <= p_fallback <= 1.0


def test_trained_safety_predictor_scoring():
    predictor = TrainedSafetyPredictor()
    
    clean_draft = Message(sender_id="me", timestamp_ms=1000, text="mai cậu có rảnh không?")
    toxic_draft = Message(sender_id="me", timestamp_ms=1000, text="mày ngu thế, cút đi")

    tox_clean, derail_clean = predictor.score([], clean_draft)
    tox_toxic, derail_toxic = predictor.score([], toxic_draft)

    assert tox_clean == 0.0
    assert tox_toxic > 0.5
    assert derail_toxic > 0.5


def test_trained_safety_predictor_fallback():
    predictor = TrainedSafetyPredictor()
    predictor.model = None  # Force fallback
    draft = Message(sender_id="me", timestamp_ms=1000, text="mày ngu thế")
    tox, derail = predictor.score([], draft)
    assert tox > 0.5


# -----------------------------------------------------------------------------
# 3. FastAPI Route Integration Tests
# -----------------------------------------------------------------------------

def test_api_health_score_endpoint():
    msgs = make_chat(
        [0, 0.1, 0.2, 0.3],
        ["cậu có khỏe không?", "tớ khỏe, cảm ơn nhé", "chiều nay đi thư viện không?", "có chứ!"]
    )
    payload = {
        "conversation_id": "test_conv_1",
        "viewer_id": "me",
        "messages": msgs,
        "now_ms": int(0.5 * H),
    }
    resp = client.post("/health/score", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert "p_disengage" in data
    assert "level" in data
    assert "top_factors" in data
    assert "suggestions" in data
    assert "model_version" in data
    assert 0.0 <= data["p_disengage"] <= 1.0
    assert data["level"] == "healthy"
    assert data["suggestions"] == []
    assert "health-trained" in data["model_version"] or "health-rule" in data["model_version"]


def test_api_health_score_at_risk():
    msgs = make_chat(
        [0, 5, 10, 15, 20],
        ["alo?", "ừ", "sao thế?", "k có gì", "ok"]
    )
    payload = {
        "conversation_id": "test_conv_dying",
        "viewer_id": "me",
        "messages": msgs,
        "now_ms": int(80 * H),  # 60h silence
    }
    resp = client.post("/health/score", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert data["level"] == "at_risk"
    assert len(data["suggestions"]) > 0
    assert any(f in data["top_factors"] for f in ("long_silence", "short_replies"))


def test_api_safety_score_endpoint_clean():
    payload = {
        "conversation_id": "test_safety_1",
        "context": [
            {"sender_id": "other", "timestamp_ms": 1000, "text": "Hôm nay thời tiết đẹp nhỉ"},
        ],
        "draft": {"sender_id": "me", "timestamp_ms": 2000, "text": "Đúng rồi, mát mẻ ghê"},
    }
    resp = client.post("/safety/score", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert "toxicity" in data
    assert "derail_risk" in data
    assert "should_nudge" in data
    assert "model_version" in data
    assert data["should_nudge"] is False
    assert data["toxicity"] == 0.0


def test_api_safety_score_endpoint_toxic():
    payload = {
        "conversation_id": "test_safety_2",
        "context": [
            {"sender_id": "other", "timestamp_ms": 1000, "text": "cậu làm bài sai rồi"},
        ],
        "draft": {"sender_id": "me", "timestamp_ms": 2000, "text": "đm mày ngu thế hả"},
    }
    resp = client.post("/safety/score", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert data["should_nudge"] is True
    assert data["toxicity"] > 0.5
