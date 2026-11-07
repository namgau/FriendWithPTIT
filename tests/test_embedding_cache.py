import numpy as np
import pandas as pd
import pytest

from src.m3_health.embedding_cache import cache_paths, has_cache, load_cache, t4_for_samples
from src.m3_health.features_t4 import extract_t4_features


def _msgs():
    rows = [{"conversation_id": c, "turn_idx": t, "sender_id": f"u{t % 2}", "text": f"{c} tin {t}"}
            for c in ("a", "b", "c") for t in range(8)]
    return pd.DataFrame(rows)


def _write_cache(tmp_path, msgs, dim=6):
    rng = np.random.default_rng(0)
    shuffled = msgs.sample(frac=1, random_state=1)[["conversation_id", "turn_idx"]].reset_index(drop=True)
    emb = rng.normal(size=(len(shuffled), dim)).astype(np.float16)
    emb_path, idx_path = cache_paths("X", tmp_path)
    emb_path.parent.mkdir(parents=True)
    np.save(emb_path, emb)
    shuffled.to_parquet(idx_path, index=False)
    return emb, shuffled


def test_chunked_t4_matches_direct_extraction(tmp_path):
    msgs = _msgs()
    emb, index = _write_cache(tmp_path, msgs)
    assert has_cache("X", tmp_path)
    mm, idx = load_cache("X", tmp_path)
    samples = pd.DataFrame({"conversation_id": ["c", "a", "b", "a"], "turn_idx": [5, 7, 6, 5]}, index=[9, 3, 7, 1])
    got = t4_for_samples(msgs, samples, mm, idx, chunk_conversations=1)
    lookup = {k: emb[i].astype(np.float32) for i, k in enumerate(index.itertuples(index=False, name=None))}
    want = extract_t4_features(msgs, samples, lookup)
    assert got.index.tolist() == [9, 3, 7, 1]
    pd.testing.assert_frame_equal(got, want, atol=1e-5)


def test_missing_message_in_cache_raises(tmp_path):
    msgs = _msgs()
    _write_cache(tmp_path, msgs[msgs.conversation_id != "b"])
    mm, idx = load_cache("X", tmp_path)
    samples = pd.DataFrame({"conversation_id": ["b"], "turn_idx": [6]})
    with pytest.raises(KeyError, match="B8"):
        t4_for_samples(msgs, samples, mm, idx)
