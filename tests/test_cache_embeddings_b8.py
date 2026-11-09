import json
import sys
from types import SimpleNamespace

import numpy as np
import pandas as pd
import pytest

from src.m3_health.cache_embeddings_b8 import run_dataset
from src.m3_health import cache_embeddings_b8
from src.m3_health.embedding_cache import has_cache, load_cache
from src.m3_health.embeddings import make_encoder


CFG = {"selected_model": "fake", "normalize": True, "empty_policy": "zero",
       "cache_dtype": "float16", "models": {"fake": {
           "model_id": "fake", "revision": "1", "dimension": 3,
           "max_length": 256, "batch_size": 2}}}


class Backend:
    dimension = 3

    def __init__(self, fail_on=None):
        self.calls = []
        self.batch_sizes = []
        self.fail_on = fail_on

    def encode(self, texts, batch_size):
        self.calls.append(list(texts))
        self.batch_sizes.append(batch_size)
        if len(self.calls) == self.fail_on:
            raise RuntimeError("simulated interruption")
        return np.array([[len(t), ord(t[0]), 1] for t in texts], dtype=np.float32)


def prepare(tmp_path, dataset="G1"):
    base = tmp_path / dataset
    base.mkdir()
    messages = pd.DataFrame({"conversation_id": ["b", "a", "a", "b", "c"],
                             "turn_idx": [0, 0, 1, 1, 0],
                             "text": ["bb", "aa", "", "bbb", "c"]})
    messages.to_parquet(base / "messages.parquet", index=False)
    (base / "embed_conversation_ids.txt").write_text("a\nb\nc\n", encoding="utf-8")
    return base, messages


def run(root, backend, *, resume=False, dataset="G1", chunk=2, config=CFG):
    return run_dataset(dataset, root, make_encoder(config, backend=backend), config,
                       chunk_size=chunk, batch_size=2, resume=resume, device_info="fake CPU")


def test_interruption_resume_preserves_alignment_and_reader_contract(tmp_path):
    base, messages = prepare(tmp_path)
    backend = Backend(fail_on=2)
    with pytest.raises(RuntimeError, match="interruption"):
        run(tmp_path, backend)
    state_path = base / "emb_bge-m3.checkpoint.json"
    assert json.loads(state_path.read_text())["completed"] == 2
    assert not has_cache("G1", tmp_path)
    backend = Backend()
    state = run(tmp_path, backend, resume=True, chunk=3)
    assert state["completed"] == 5 and state["status"] == "complete"
    assert backend.calls == [["bbb", "c"]]
    vectors, index = load_cache("G1", tmp_path)
    assert vectors.dtype == np.float16 and vectors.shape == (5, 3)
    pd.testing.assert_frame_equal(index, messages[["conversation_id", "turn_idx"]])
    expected = make_encoder(CFG, backend=Backend()).encode(messages.text.tolist())
    np.testing.assert_allclose(vectors, expected, atol=5e-4)
    assert np.array_equal(vectors[2], np.zeros(3))
    assert (base / "emb_bge-m3.metadata.json").exists()
    assert run(tmp_path, Backend(), resume=True)["completed"] == 5
    with pytest.raises(FileExistsError):
        run(tmp_path, Backend())


@pytest.mark.parametrize("change", ["messages", "ids", "config", "index"])
def test_resume_rejects_changed_inputs(tmp_path, change):
    base, messages = prepare(tmp_path)
    with pytest.raises(RuntimeError):
        run(tmp_path, Backend(fail_on=2))
    config = CFG
    if change == "messages":
        messages.loc[0, "text"] = "different"
        messages.to_parquet(base / "messages.parquet", index=False)
    elif change == "ids":
        (base / "embed_conversation_ids.txt").write_text("a\nb\n", encoding="utf-8")
    elif change == "config":
        config = {**CFG, "normalize": False}
    else:
        (base / "emb_bge-m3_index.partial.parquet").write_bytes(b"broken")
    with pytest.raises(ValueError):
        run(tmp_path, Backend(), resume=True, config=config)
    assert not has_cache("G1", tmp_path)


def test_selection_e1_and_all_messages_required_for_g1(tmp_path):
    base, messages = prepare(tmp_path, "E1")
    (base / "embed_conversation_ids.txt").write_text("a\nc\n", encoding="utf-8")
    run(tmp_path, Backend(), dataset="E1")
    vectors, index = load_cache("E1", tmp_path)
    expected = messages[messages.conversation_id.isin(["a", "c"])].reset_index(drop=True)
    pd.testing.assert_frame_equal(index, expected[["conversation_id", "turn_idx"]])
    assert len(vectors) == 3
    base, _ = prepare(tmp_path, "G1")
    (base / "embed_conversation_ids.txt").write_text("a\nc\n", encoding="utf-8")
    with pytest.raises(ValueError, match="tat ca"):
        run(tmp_path, Backend())
    assert not has_cache("G1", tmp_path)


def test_duplicate_keys_and_missing_ids_are_rejected(tmp_path):
    base, messages = prepare(tmp_path)
    messages.loc[1, ["conversation_id", "turn_idx"]] = ["b", 0]
    messages.to_parquet(base / "messages.parquet", index=False)
    with pytest.raises(ValueError, match="Trung khoa"):
        run(tmp_path, Backend())
    (base / "embed_conversation_ids.txt").write_text("a\nb\nc\nmissing\n", encoding="utf-8")
    with pytest.raises(ValueError, match="khong co"):
        run(tmp_path, Backend())


def test_resume_interrupted_publishing(tmp_path, monkeypatch):
    base, _ = prepare(tmp_path)
    original = cache_embeddings_b8.os.replace

    def interrupted(source, target):
        if str(source).endswith("emb_bge-m3.partial.npy"):
            raise OSError("interrupted publishing")
        return original(source, target)

    monkeypatch.setattr(cache_embeddings_b8.os, "replace", interrupted)
    with pytest.raises(OSError, match="publishing"):
        run(tmp_path, Backend())
    assert (base / "emb_bge-m3_index.parquet").exists()
    assert not has_cache("G1", tmp_path)
    monkeypatch.setattr(cache_embeddings_b8.os, "replace", original)
    backend = Backend()
    run(tmp_path, backend, resume=True)
    assert not backend.calls
    assert has_cache("G1", tmp_path)


def test_c1_memmap_called_per_chunk_and_batch_size_forwarded(tmp_path, monkeypatch):
    base, messages = prepare(tmp_path)
    calls = []
    real_encode_to_memmap = cache_embeddings_b8.encode_to_memmap

    def spy(texts, output, encoder, shard_size):
        calls.append((list(texts), output, shard_size, encoder.batch_size))
        assert output == base / "emb_bge-m3.chunk.npy"
        assert output != base / "emb_bge-m3.partial.npy"
        assert output != base / "emb_bge-m3.npy"
        return real_encode_to_memmap(texts, output, encoder, shard_size=shard_size)

    monkeypatch.setattr(cache_embeddings_b8, "encode_to_memmap", spy)
    backend = Backend()
    encoder = make_encoder(CFG, backend=backend)
    run_dataset("G1", tmp_path, encoder, CFG, chunk_size=2, batch_size=1)
    assert [call[0] for call in calls] == [messages.text.tolist()[:2], ["", "bbb"], ["c"]]
    assert all(call[2:] == (2, 1) for call in calls)
    assert backend.batch_sizes == [1, 1, 1]
    assert encoder.batch_size == 2
    assert not (base / "emb_bge-m3.chunk.npy").exists()


def test_resume_rejects_corruption_of_checkpointed_vectors(tmp_path):
    base, _ = prepare(tmp_path)
    with pytest.raises(RuntimeError):
        run(tmp_path, Backend(fail_on=2))
    cache = np.load(base / "emb_bge-m3.partial.npy", mmap_mode="r+")
    cache[0, 0] = 123
    cache.flush()
    del cache
    with pytest.raises(ValueError, match="embedding da checkpoint"):
        run(tmp_path, Backend(), resume=True)
    assert not has_cache("G1", tmp_path)


def test_resume_after_chunk_copy_before_checkpoint(tmp_path, monkeypatch):
    base, messages = prepare(tmp_path)
    original = cache_embeddings_b8.write_json

    def interrupted(path, state):
        if path.name.endswith("checkpoint.json") and state["completed"] == 4:
            raise RuntimeError("checkpoint interrupted")
        original(path, state)

    monkeypatch.setattr(cache_embeddings_b8, "write_json", interrupted)
    with pytest.raises(RuntimeError, match="checkpoint interrupted"):
        run(tmp_path, Backend())
    assert json.loads((base / "emb_bge-m3.checkpoint.json").read_text())["completed"] == 2
    monkeypatch.setattr(cache_embeddings_b8, "write_json", original)
    backend = Backend()
    run(tmp_path, backend, resume=True, chunk=3)
    assert backend.calls == [["bbb", "c"]]
    vectors, index = load_cache("G1", tmp_path)
    pd.testing.assert_frame_equal(index, messages[["conversation_id", "turn_idx"]])
    expected = make_encoder(CFG, backend=Backend()).encode(messages.text.tolist())
    np.testing.assert_allclose(vectors, expected, atol=5e-4)


def test_contract_1024_dimensions(tmp_path):
    class Backend1024:
        dimension = 1024

        def encode(self, texts, batch_size):
            return np.array([[len(text)] * self.dimension for text in texts], dtype=np.float32)

    _, messages = prepare(tmp_path)
    config = {**CFG, "models": {"fake": {**CFG["models"]["fake"], "dimension": 1024}}}
    run(tmp_path, Backend1024(), config=config)
    vectors, index = load_cache("G1", tmp_path)
    assert vectors.shape == (5, 1024) and vectors.dtype == np.float16
    pd.testing.assert_frame_equal(index, messages[["conversation_id", "turn_idx"]])
    np.testing.assert_allclose(np.linalg.norm(vectors[[0, 1, 3, 4]].astype(np.float32), axis=1), 1, atol=5e-4)
    assert np.all(vectors[2] == 0)


def test_refuses_publication_if_source_changes_during_encoding(tmp_path):
    base, _ = prepare(tmp_path)

    class ChangingBackend(Backend):
        def encode(self, texts, batch_size):
            result = super().encode(texts, batch_size)
            if len(self.calls) == 1:
                with (base / "messages.parquet").open("ab") as stream:
                    stream.write(b"changed")
            return result

    with pytest.raises((ValueError, OSError)):
        run(tmp_path, ChangingBackend())
    assert not has_cache("G1", tmp_path)


def test_cli_constructs_encoder_using_c1_factory(monkeypatch, tmp_path):
    cfg = {"selected_model": "bge_m3", "normalize": True, "cache_dtype": "float16",
           "models": {"bge_m3": {"model_id": "BAAI/bge-m3", "dimension": 1024,
                                 "max_length": 256, "compute_dtype": "float16"}}}
    encoder = object()
    created = []
    executed = []
    monkeypatch.setattr(cache_embeddings_b8, "load_embedding_config", lambda _: cfg)
    monkeypatch.setattr(cache_embeddings_b8, "make_encoder", lambda config: created.append(config) or encoder)
    monkeypatch.setattr(cache_embeddings_b8, "run_dataset", lambda *args, **kwargs: executed.append((args, kwargs)))
    monkeypatch.setitem(sys.modules, "torch", SimpleNamespace(cuda=SimpleNamespace(
        is_available=lambda: True, get_device_name=lambda _: "test GPU")))
    monkeypatch.setattr(sys, "argv", ["cache_embeddings_b8", "G1", "--root", str(tmp_path), "--batch-size", "16"])
    cache_embeddings_b8.main()
    assert created == [cfg]
    assert executed[0][0] == ("G1", tmp_path, encoder, cfg)
    assert executed[0][1]["batch_size"] == 16
    assert executed[0][1]["device_info"] == "test GPU"
