"""Modal GPU runner for C2 PhoBERT Fine-Tuning on Vietnamese Toxicity Classification.

Hỗ trợ chạy trên Modal GPU (T4 hoặc A10G) như cách triển khai của Vĩ ở B8/B9.
Sử dụng underthesea tách từ tiếng Việt trước khi tokenization và fine-tune PhoBERT.

Cách chạy:
  python -m modal run src/m3_health/modal_c2_runner.py --epochs 2 --batch-size 32
"""
from __future__ import annotations

import os
from pathlib import Path

try:
    import modal
    _MODAL_AVAILABLE = True
except ImportError:
    modal = None
    _MODAL_AVAILABLE = False

if _MODAL_AVAILABLE and modal.is_local():
    ROOT = Path(__file__).resolve().parents[2]
    image = (
        modal.Image.debian_slim(python_version="3.11")
        .pip_install(
            "torch==2.5.1",
            "transformers==4.46.3",
            "underthesea==9.5.0",
            "underthesea-core>=3.3.0",
            "scikit-learn==1.6.1",
            "pandas==2.2.3",
            "numpy==1.26.4",
            "pyarrow==19.0.1",
            "PyYAML==6.0.2",
            "huggingface-hub==0.28.1",
        )
        .add_local_dir(ROOT / "src", "/root/project/src", copy=True)
        .add_local_dir(ROOT / "configs", "/root/project/configs", copy=True)
        .add_local_dir(ROOT / "data" / "processed", "/root/project/data/processed", copy=True)
    )
    app = modal.App("c2-phobert-head-b")
elif _MODAL_AVAILABLE:
    image = modal.Image.debian_slim(python_version="3.11")
    app = modal.App("c2-phobert-head-b")
else:
    image = None
    app = None


if _MODAL_AVAILABLE and app is not None:
    @app.function(image=image, gpu="T4", cpu=4, memory=16384, timeout=14400)
    def run_c2_modal(
        epochs: int = 2,
        batch_size: int = 32,
        max_length: int = 128,
        lr: float = 2e-5,
        seed: int = 42,
    ) -> dict:
        """Thực thi pipeline huấn luyện PhoBERT C2 trên GPU Modal."""
        import os
        import sys
        os.chdir("/root/project")
        sys.path.insert(0, "/root/project")

        from src.m3_health.train_c2_transformer import run_pipeline

        results = run_pipeline(
            model_type="phobert",
            seed=seed,
            k_folds=5,
            cal_fraction=0.2,
            threshold_fpr=0.05,
            output_dir="/root/project/models/m3/c2",
            smoke_test=False,
            write_ledger=True,
            word_segment=True,
            epochs=epochs,
            batch_size=batch_size,
            max_length=max_length,
            lr=lr,
            save_pickle=False,
        )
        return results

    @app.local_entrypoint()
    def main(
        epochs: int = 2,
        batch_size: int = 32,
        max_length: int = 128,
        lr: float = 2e-5,
        seed: int = 42,
    ):
        print(f"Bắt đầu chạy C2 PhoBERT Fine-tuning trên Modal GPU (T4)...")
        res = run_c2_modal.remote(
            epochs=epochs,
            batch_size=batch_size,
            max_length=max_length,
            lr=lr,
            seed=seed,
        )
        print("Hoàn tất huấn luyện C2 trên Modal:", res.get("cv_summary"))
