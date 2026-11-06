"""Benchmark C1 cho MiniLM/BGE-M3 tren tin Anh/Viet thuoc train.

Mac dinh doc E1/E2 cho tieng Anh, H1/H2 cho tieng Viet. Script khong doc
``data/locked`` va chi ghi ket qua vao ``reports/`` (thu muc da gitignore).
"""
from __future__ import annotations

import argparse
import copy
import csv
import gc
import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np
import pandas as pd

from src.m3_health.embeddings import ROOT, load_embedding_config, make_encoder


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _text_sha256(texts: list[str]) -> str:
    """Hash có phân ranh giới để cùng một chuỗi mẫu luôn cho cùng fingerprint."""
    digest = hashlib.sha256()
    for text in texts:
        encoded = text.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, "big"))
        digest.update(encoded)
    return digest.hexdigest()


def _sample(paths: list[Path], n_each: int, seed: int) -> tuple[list[str], list[dict]]:
    rng = np.random.default_rng(seed)
    texts: list[str] = []
    sources: list[dict] = []
    for path in paths:
        frame = pd.read_parquet(path, columns=["text"])
        values = frame["text"].fillna("").astype(str).to_numpy()
        count = min(n_each, len(values))
        selected: list[str] = []
        if count:
            selected = values[rng.choice(len(values), count, replace=False)].tolist()
            texts.extend(selected)
        try:
            relative_path = path.resolve().relative_to(ROOT.resolve()).as_posix()
        except ValueError:
            relative_path = str(path.resolve())
        sources.append({
            "path": relative_path,
            "sha256": _sha256_file(path),
            "file_bytes": path.stat().st_size,
            "rows": int(len(values)),
            "sampled_rows": int(count),
            "sampled_text_sha256": _text_sha256(selected),
        })
    return texts, sources


def _gpu_memory() -> float | None:
    try:
        import torch
        if torch.cuda.is_available():
            return float(torch.cuda.max_memory_allocated() / (1024**2))
    except ImportError:
        pass
    return None


def _process_rss_mib() -> float | None:
    try:
        import psutil
        return float(psutil.Process().memory_info().rss / (1024**2))
    except ImportError:
        return None


def _hardware() -> dict:
    info = {"platform": platform.platform(), "processor": platform.processor()}
    try:
        import torch
        info["torch_version"] = torch.__version__
        info["cuda_runtime"] = torch.version.cuda
        if torch.cuda.is_available():
            properties = torch.cuda.get_device_properties(0)
            info["gpu_name"] = torch.cuda.get_device_name(0)
            info["gpu_total_mib"] = int(properties.total_memory / (1024**2))
    except ImportError:
        pass
    return info


def benchmark_one(encoder, texts: list[str], language: str, warmup: int, repeats: int,
                  batch_size: int, config: dict) -> dict:
    backend_device = str(getattr(encoder._backend(), "device", "cpu"))
    uses_cuda = backend_device.startswith("cuda")
    probe = texts[: min(len(texts), max(batch_size, 8))]
    for _ in range(warmup):
        encoder.encode(probe, batch_size=batch_size)
    torch = None
    if uses_cuda:
        try:
            import torch as torch_module
            torch = torch_module
            torch.cuda.reset_peak_memory_stats()
            torch.cuda.synchronize()
        except ImportError:
            pass
    elapsed = []
    for _ in range(repeats):
        start = time.perf_counter()
        encoder.encode(texts, batch_size=batch_size)
        if torch is not None:
            torch.cuda.synchronize()
        elapsed.append(time.perf_counter() - start)
    lengths = encoder.token_lengths(texts)
    max_length = int(encoder.model["max_length"])
    seconds = np.asarray(elapsed)
    return {
        **encoder.metadata(config), "language": language, "batch_size": batch_size,
        "n_texts": len(texts), "latency_s_median": float(np.median(seconds)),
        "latency_s_p95": float(np.percentile(seconds, 95)),
        "texts_per_s": float(len(texts) / np.median(seconds)),
        "token_p95": float(np.percentile(lengths, 95)) if len(lengths) else 0.0,
        "truncated_fraction": float(np.mean(lengths > max_length)) if len(lengths) else 0.0,
        "gpu_peak_mib": _gpu_memory() if uses_cuda else None,
        "process_rss_mib": _process_rss_mib(),
        "estimated_cache_mib": float(len(texts) * encoder.dimension * 4 / (1024**2)),
    }


def _write(rows: list[dict], output: Path, cfg: dict, provenance: dict) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix(".json").write_text(
        json.dumps({"config": cfg, "hardware": _hardware(),
                    "data_provenance": provenance, "results": rows},
                   ensure_ascii=False, indent=2), encoding="utf-8"
    )
    fields = sorted({key for row in rows for key in row})
    with output.with_suffix(".csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def _enrich_existing(output: Path, provenance: dict) -> None:
    """Bổ sung provenance mà không chạy lại benchmark hay thay đổi các kết quả đo."""
    report = output.with_suffix(".json")
    if not report.exists():
        raise FileNotFoundError(f"khong co benchmark de bo sung provenance: {report}")
    original_mtime = report.stat().st_mtime
    payload = json.loads(report.read_text(encoding="utf-8"))
    results = payload.get("results", [])
    expected = {
        language: sum(source["sampled_rows"] for source in detail["sources"])
        for language, detail in provenance["languages"].items()
    }
    for row in results:
        language = row.get("language")
        if language in expected and int(row.get("n_texts", -1)) != expected[language]:
            raise ValueError(f"n_texts cua {language} khong khop provenance")
    source_paths = [
        ROOT / source["path"]
        for detail in provenance["languages"].values()
        for source in detail["sources"]
        if not Path(source["path"]).is_absolute()
    ]
    provenance = copy.deepcopy(provenance)
    provenance["capture_mode"] = "retrospective_without_rerunning_benchmark"
    provenance["all_source_mtimes_precede_original_report"] = all(
        path.stat().st_mtime <= original_mtime for path in source_paths
    )
    payload.pop("platform", None)
    payload["hardware"] = _hardware()
    payload["data_provenance"] = provenance
    temporary = report.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(report)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=ROOT / "configs" / "embedding.yaml")
    parser.add_argument("--models", nargs="+", default=["bge_m3", "minilm"])
    parser.add_argument("--devices", nargs="+", default=["cpu", "cuda"])
    parser.add_argument("--english", nargs="*", type=Path,
                        default=[ROOT / "data/processed/E1/messages.parquet",
                                 ROOT / "data/processed/E2/messages.parquet"])
    parser.add_argument("--vietnamese", nargs="*", type=Path,
                        default=[ROOT / "data/processed/H1/messages.parquet",
                                 ROOT / "data/processed/H2/messages.parquet"])
    parser.add_argument("--output", type=Path, default=ROOT / "reports/c1_embedding_benchmark")
    parser.add_argument("--provenance-only", action="store_true",
                        help="Bo sung hash/path/hardware vao JSON hien co, khong benchmark lai")
    args = parser.parse_args()
    cfg = load_embedding_config(args.config)
    bench = cfg["benchmark"]
    missing = [p for p in args.english + args.vietnamese if not p.exists()]
    if missing:
        parser.error("thieu messages.parquet: " + ", ".join(map(str, missing)))
    english, english_sources = _sample(
        args.english, int(bench["sample_per_source"]), int(cfg["seed"])
    )
    vietnamese, vietnamese_sources = _sample(
        args.vietnamese, int(bench["sample_per_source"]), int(cfg["seed"])
    )
    samples = {"en": english, "vi": vietnamese}
    provenance = {
        "seed": int(cfg["seed"]),
        "sample_per_source": int(bench["sample_per_source"]),
        "languages": {
            "en": {"sources": english_sources, "combined_sample_sha256": _text_sha256(english)},
            "vi": {"sources": vietnamese_sources, "combined_sample_sha256": _text_sha256(vietnamese)},
        },
    }
    if args.provenance_only:
        _enrich_existing(args.output, provenance)
        return
    rows = []
    for name in args.models:
        for requested_device in args.devices:
            if requested_device.startswith("cuda"):
                try:
                    import torch
                    if not torch.cuda.is_available():
                        print("skip cuda: CUDA khong san sang", flush=True)
                        continue
                except ImportError:
                    print("skip cuda: chua cai torch", flush=True)
                    continue
            runtime_cfg = copy.deepcopy(cfg)
            runtime_cfg["models"][name]["device"] = requested_device
            if requested_device == "cpu":
                runtime_cfg["models"][name]["compute_dtype"] = "float32"
            encoder = make_encoder(runtime_cfg, name)
            device = getattr(encoder._backend(), "device", requested_device)
            batches = bench["gpu_batches"] if str(device).startswith("cuda") else bench["cpu_batches"]
            for language, texts in samples.items():
                for batch in batches:
                    row = benchmark_one(encoder, texts, language, int(bench["warmup"]),
                                        int(bench["repeats"]), int(batch), runtime_cfg)
                    row["device"] = device
                    rows.append(row)
                    print(name, language, device, f"batch={batch}",
                          f"{row['texts_per_s']:.1f} text/s", flush=True)
            del encoder
            gc.collect()
            try:
                import torch
                if torch.cuda.is_available():
                    torch.cuda.empty_cache()
            except ImportError:
                pass
    _write(rows, args.output, cfg, provenance)


if __name__ == "__main__":
    main()
