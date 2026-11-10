"""Run one B8 dataset on a pre-existing Modal Volume; no automatic uploads.

Set B8_MODAL_VOLUME before invoking ``modal run``. Input files must already be
present under /b8/data/processed/<dataset> on that Volume. Run one writer per
dataset and keep the existing B8 checkpoint files between interrupted runs.
"""
from __future__ import annotations

import os
from pathlib import Path

import modal

if modal.is_local():
    ROOT = Path(__file__).resolve().parents[2]
    volume_name = os.environ.get("B8_MODAL_VOLUME")
    if not volume_name:
        raise RuntimeError("Set B8_MODAL_VOLUME to an existing, dedicated Volume name")
    volume = modal.Volume.from_name(volume_name, create_if_missing=False)
    image = (
        modal.Image.debian_slim(python_version="3.11")
        .pip_install("numpy>=1.26,<3", "pandas>=2.1", "pyarrow>=15,<27", "PyYAML>=6",
                     "torch==2.5.1", "transformers==4.46.3", "sentence-transformers==3.4.1")
        .env({"B8_MODAL_VOLUME": volume_name})
        .add_local_file(ROOT / "src/m3_health/embeddings.py", "/root/project/src/m3_health/embeddings.py")
        .add_local_file(ROOT / "src/m3_health/cache_embeddings_b8.py", "/root/project/src/m3_health/cache_embeddings_b8.py")
        .add_local_file(ROOT / "configs/embedding.yaml", "/root/project/configs/embedding.yaml")
    )
else:
    volume_name = None
    volume = modal.Volume.from_name(os.environ["B8_MODAL_VOLUME"], create_if_missing=False) if "B8_MODAL_VOLUME" in os.environ else None
    image = modal.Image.debian_slim(python_version="3.11")
app = modal.App("b8-bge-m3-cache")


@app.function(image=image, gpu="T4", cpu=2, memory=8192, timeout=10800,
              volumes={"/b8": volume} if volume else {})
def cache_one(dataset: str, resume: bool, batch_size: int, chunk_size: int) -> None:
    import subprocess
    import sys

    if dataset not in ("E1", "E2", "G1", "H1", "H2"):
        raise ValueError("Invalid dataset")
    os.environ["HF_HOME"] = "/b8/model-cache"
    os.environ["PYTHONPATH"] = "/root/project"
    command = [sys.executable, "-m", "src.m3_health.cache_embeddings_b8", dataset,
               "--root", "/b8/data/processed", "--config", "/root/project/configs/embedding.yaml",
               "--batch-size", str(batch_size), "--chunk-size", str(chunk_size)]
    if resume:
        command.append("--resume")
    try:
        subprocess.run(command, cwd="/root/project", check=True)
    finally:
        # Make completed writes visible to a later container, including on a
        # normal subprocess error. Abrupt container loss still requires resume
        # integrity checks; Volume background commits are not transactions.
        modal.Volume.from_name(os.environ["B8_MODAL_VOLUME"]).commit()


@app.local_entrypoint()
def main(dataset: str, resume: bool = False, batch_size: int = 32, chunk_size: int = 2048):
    if dataset not in ("E1", "E2", "G1", "H1", "H2") or batch_size <= 0 or chunk_size <= 0:
        raise ValueError("Invalid dataset, batch size, or chunk size")
    print(f"Volume={volume_name}, dataset={dataset}, resume={resume}", flush=True)
    cache_one.remote(dataset, resume, batch_size, chunk_size)
