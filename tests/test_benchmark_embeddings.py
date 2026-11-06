import numpy as np
import pandas as pd
import json

from src.m3_health import benchmark_embeddings as bench


class _Backend:
    device = "cpu"


class _Encoder:
    dimension = 4
    model = {"max_length": 8}

    def _backend(self):
        return _Backend()

    def encode(self, texts, batch_size):
        return np.zeros((len(texts), self.dimension), dtype=np.float32)

    def token_lengths(self, texts):
        return np.ones(len(texts), dtype=np.int32)

    def metadata(self, config):
        return {}


def test_cpu_benchmark_never_reports_stale_gpu_memory(monkeypatch):
    monkeypatch.setattr(bench, "_gpu_memory", lambda: 99.0)
    row = bench.benchmark_one(
        _Encoder(), ["xin chao"], "vi", warmup=0, repeats=1,
        batch_size=1, config={},
    )
    assert row["gpu_peak_mib"] is None


def test_sample_records_source_and_sample_fingerprints(tmp_path):
    source = tmp_path / "messages.parquet"
    pd.DataFrame({"text": ["a", "b", "c"]}).to_parquet(source, index=False)

    texts, provenance = bench._sample([source], n_each=2, seed=42)

    assert len(texts) == 2
    assert provenance[0]["rows"] == 3
    assert provenance[0]["sampled_rows"] == 2
    assert len(provenance[0]["sha256"]) == 64
    assert provenance[0]["sampled_text_sha256"] == bench._text_sha256(texts)


def test_enrich_existing_preserves_results(tmp_path, monkeypatch):
    output = tmp_path / "benchmark"
    report = output.with_suffix(".json")
    original_results = [{"language": "vi", "n_texts": 2, "texts_per_s": 12.5}]
    report.write_text(json.dumps({"platform": "old", "results": original_results}), encoding="utf-8")
    monkeypatch.setattr(bench, "_hardware", lambda: {"gpu_name": "test-gpu"})
    provenance = {
        "seed": 42,
        "sample_per_source": 2,
        "languages": {"vi": {"sources": [{
            "path": str(tmp_path / "external.parquet"), "sampled_rows": 2,
        }], "combined_sample_sha256": "abc"}},
    }

    bench._enrich_existing(output, provenance)

    enriched = json.loads(report.read_text(encoding="utf-8"))
    assert enriched["results"] == original_results
    assert enriched["hardware"]["gpu_name"] == "test-gpu"
    assert enriched["data_provenance"]["capture_mode"].startswith("retrospective")
