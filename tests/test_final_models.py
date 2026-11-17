import joblib
import pytest
import numpy as np
import pandas as pd

from serving.model_registry import ModelRegistry
from serving.predictors import TrainedHealthPredictor, TrainedSafetyPredictor, extract_t1_t2_features
from serving.schemas import Message
from src.m3_health.final_models import fit_head_a, fit_head_b, frame_from_texts, head_a_taus, oof_head_a
from src.m3_health.features import extract_features


def _head_a_data(n_conv=120, turns=12, seed=0):
    rng = np.random.default_rng(seed)
    rows, labs = [], []
    for c in range(n_conv):
        t = 1_600_000_000 + c * 3600.0
        for i in range(turns):
            t += rng.exponential(60 if c % 3 else 3000)
            rows.append({"conversation_id": f"c{c}", "turn_idx": i, "timestamp_s": t,
                         "sender_id": f"u{i % 2}", "text": "ok" if c % 3 == 0 else "how are you doing today?"})
        for i in range(5, turns, 2):
            labs.append({"conversation_id": f"c{c}", "turn_idx": i, "group_id": f"c{c}",
                         "conv_start_s": 1_600_000_000 + c * 3600.0, "y": int(c % 3 == 0 and i == turns - 1 or rng.random() < 0.1)})
    msgs, lab = pd.DataFrame(rows), pd.DataFrame(labs)
    return msgs, lab, extract_features(msgs, lab)


def test_head_a_final_model_roundtrip_and_taus(tmp_path):
    msgs, lab, X = _head_a_data()
    cols = list(X.columns)
    p = oof_head_a(lab, X, cols, seed=42, k=3)
    assert np.isfinite(p).sum() > 0 and np.isnan(p).sum() > 0  # khối sớm nhất không có OOF
    tau1, tau2 = head_a_taus(lab["y"], p, {"alert_budget": 0.1, "shallow_recall": 0.8})
    assert 0 <= tau1 <= tau2 <= 1
    model = fit_head_a(lab, X, cols, seed=42)
    joblib.dump(model, tmp_path / "head_a.joblib")
    again = joblib.load(tmp_path / "head_a.joblib")
    np.testing.assert_allclose(again.predict_proba(X)[:, 1], model.predict_proba(X)[:, 1])
    assert ((model.predict_proba(X) >= 0) & (model.predict_proba(X) <= 1)).all()


def test_head_b_text_model_accepts_raw_texts():
    texts = ["mày ngu quá", "chào bạn nhé", "đồ ngu", "hôm nay đẹp trời", "ngu như bò", "cảm ơn bạn"] * 10
    frame = frame_from_texts(texts)
    frame["y"] = [int("ngu" in t) for t in texts]
    frame["group_id"] = np.arange(len(texts))
    cfg = {"head_b": {"min_df": 1, "max_features": 1000, "calibration_fraction": 0.2, "threshold_fpr": 0.05}}
    model, tau = fit_head_b(frame, cfg, seed=42)
    p = model.predict_proba(["mày ngu", "chào bạn"])[:, 1]
    assert p[0] > p[1] and 0 <= tau <= 1


def test_serving_loads_exported_models(tmp_path):
    msgs, lab, X = _head_a_data()
    head_a = fit_head_a(lab, X, list(X.columns), seed=42)
    texts = ["mày ngu quá", "chào bạn nhé"] * 20
    frame = frame_from_texts(texts)
    frame["y"], frame["group_id"] = [i % 2 == 0 for i in range(40)], np.arange(40)
    cfg = {"head_b": {"min_df": 1, "max_features": 1000, "calibration_fraction": 0.2, "threshold_fpr": 0.05}}
    head_b, tau = fit_head_b(frame.astype({"y": int}), cfg, seed=42)
    out = tmp_path / "m3" / "v1"
    out.mkdir(parents=True)
    joblib.dump(head_a, out / "head_a.joblib")
    joblib.dump(head_b, out / "head_b.joblib")
    (out / "metadata.json").write_text('{"head_a": {"model_file": "head_a.joblib", "tau1": 0.2, "tau2": 0.5}, '
                                       f'"head_b": {{"model_file": "head_b.joblib", "tau_nudge": {tau}}}}}', encoding="utf-8")
    (tmp_path / "m3" / ".active").write_text("v1", encoding="utf-8")
    registry = ModelRegistry(tmp_path)
    health = TrainedHealthPredictor(registry)
    assert health.version.startswith("health-trained")
    chat = [Message(sender_id=f"u{i % 2}", timestamp_ms=1_700_000_000_000 + i * 60_000, text="hi there?")
            for i in range(8)]
    p, _ = health.score(chat, chat[-1].timestamp_ms + 1000, "u0")
    assert p == pytest.approx(head_a.predict_proba(extract_t1_t2_features(chat))[0, 1])  # không lùi về luật
    safety = TrainedSafetyPredictor(registry)
    assert safety.version.startswith("safety-trained")
    tox_bad, _ = safety.score([], Message(sender_id="u0", timestamp_ms=1, text="mày ngu quá"))
    tox_ok, _ = safety.score([], Message(sender_id="u0", timestamp_ms=1, text="chào bạn nhé"))
    assert tox_bad > tox_ok
