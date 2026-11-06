import numpy as np

from src.m3_health.embeddings import config_hash, encode_to_memmap, make_encoder


CFG = {
    "selected_model": "fake", "normalize": True, "empty_policy": "zero",
    "models": {"fake": {"model_id": "fake/model", "revision": "abc123",
                           "dimension": 3, "max_length": 4, "batch_size": 2,
                           "device": "cpu", "compute_dtype": "float32"}},
}


class FakeBackend:
    dimension = 3

    def encode(self, texts, batch_size):
        return np.asarray([[len(text), ord(text[0]), batch_size] for text in texts], dtype=np.float32)

    def token_lengths(self, texts):
        return np.asarray([len(text.split()) + 2 for text in texts])


def test_encode_keeps_order_normalizes_and_zeros_empty():
    encoder = make_encoder(CFG, backend=FakeBackend())
    out = encoder.encode(["bb", "", "a", None])
    assert out.shape == (4, 3)
    assert np.allclose(np.linalg.norm(out[[0, 2]], axis=1), 1.0)
    assert np.array_equal(out[[1, 3]], np.zeros((2, 3), dtype=np.float32))
    assert out[0, 0] > out[2, 0]


def test_metadata_and_config_hash_are_stable():
    encoder = make_encoder(CFG, backend=FakeBackend())
    reordered = {"models": CFG["models"], "empty_policy": "zero", "normalize": True,
                 "selected_model": "fake"}
    assert config_hash(CFG) == config_hash(reordered)
    meta = encoder.metadata(CFG)
    assert meta["revision"] == "abc123"
    assert meta["config_hash"] == config_hash(CFG)


def test_encode_to_memmap_writes_shards(tmp_path):
    encoder = make_encoder(CFG, backend=FakeBackend())
    path = tmp_path / "cache.npy"
    cache = encode_to_memmap(["a", "bb", "ccc"], path, encoder, shard_size=2)
    expected = encoder.encode(["a", "bb", "ccc"])
    assert np.allclose(cache, expected)
    assert np.allclose(np.load(path, mmap_mode="r"), expected)


def test_token_lengths_use_backend():
    encoder = make_encoder(CFG, backend=FakeBackend())
    assert encoder.token_lengths(["mot hai", "ba"]).tolist() == [4, 3]
