"""Fit the frozen C2 PhoBERT on every H1 train row for the 20/11 test run.

The calibration model uses an H1 group holdout. Its Platt parameters and FPR
threshold are frozen before the final classifier is refit on all H1 train rows.
This script never reads data/locked.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np

from src.m3_health.c2_toxicity_data import batch_segment_underthesea, load_h1_train_data
from src.m3_health.labels import ROOT
from src.m3_health.train_c2_transformer import PhoBERTClassifier, train_final_h1_model

DEFAULT_DIR = ROOT / "models" / "m3" / "c2" / "final"
TRAIN_FILE = ROOT / "data" / "processed" / "H1" / "labels_headB_train.parquet"
BASE_REVISION = "86cd7fd4c148980922ac11a2cf5e257f2ba639e1"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def fit_final(output_dir: Path = DEFAULT_DIR, epochs: int = 2, batch_size: int = 32,
              seed: int = 42, device: str | None = None) -> dict:
    import torch
    import transformers
    import underthesea
    from huggingface_hub import model_info

    if not TRAIN_FILE.is_file():
        raise FileNotFoundError(TRAIN_FILE)
    if device is None:
        device = "cuda" if torch.cuda.is_available() else "cpu"
    if device != "cuda":
        raise RuntimeError("Final PhoBERT training requires a GPU; CPU is for test inference.")
    if model_info("vinai/phobert-base-v2").sha != BASE_REVISION:
        raise RuntimeError("PhoBERT upstream revision changed; freeze the requested revision first")

    data = load_h1_train_data()
    if len(data) != 26719:
        raise ValueError(f"Unexpected H1 train size: {len(data)}")
    print(f"Segmenting {len(data)} H1 train texts...", flush=True)
    segmented = batch_segment_underthesea(data["text"].tolist())
    data = data.copy()
    data["text"] = segmented
    start = time.monotonic()

    print("Fitting disjoint 80/20 group calibration model...", flush=True)
    cal_model, calibrator, tau_b, cal_meta = train_final_h1_model(
        data, seed=seed, cal_fraction=0.2, threshold_fpr=0.05,
        model_type="phobert", epochs=epochs, batch_size=batch_size,
        max_length=128, lr=2e-5,
    )
    if calibrator.lr is None:
        raise RuntimeError("Platt calibration did not fit both classes")
    del cal_model
    gc.collect()
    torch.cuda.empty_cache()

    print("Fitting final PhoBERT on ALL H1 train rows...", flush=True)
    model = PhoBERTClassifier(
        epochs=epochs, batch_size=batch_size, max_length=128, lr=2e-5,
        seed=seed, device=device, word_segment=False, imbalance="weighted",
    )
    model.fit(segmented, data["label"].to_numpy(int))
    output_dir = Path(output_dir)
    weights_dir = output_dir / "model"
    weights_dir.mkdir(parents=True, exist_ok=True)
    model.model.save_pretrained(weights_dir, safe_serialization=True)
    model.tokenizer.save_pretrained(weights_dir)

    coefficients = calibrator.lr.coef_.ravel().tolist()
    intercept = float(calibrator.lr.intercept_[0])
    manifest = {
        "schema_version": 1,
        "model": "vinai/phobert-base-v2",
        "base_revision": BASE_REVISION,
        "train_dataset": "H1 labels_headB_train only",
        "train_rows": len(data),
        "train_groups": int(data["group_id"].nunique()),
        "train_file_sha256": sha256(TRAIN_FILE),
        "fit": {"epochs": epochs, "batch_size": batch_size, "max_length": 128,
                "learning_rate": 2e-5, "imbalance": "weighted", "seed": seed,
                "word_segmentation": "underthesea 9.5.0"},
        "calibration": {"method": "Platt on raw-probability logit",
                        "source": "20% H1 group holdout from separate 80% fit model",
                        "coefficient": coefficients, "intercept": intercept,
                        "n_fit": cal_meta["n_fit"], "n_cal": cal_meta["n_cal"],
                        "tau_b": tau_b, "max_calibration_fpr": 0.05,
                        "observed_calibration_fpr": cal_meta["cal_fpr_at_tau"]},
        "note": "Calibration and tau transfer from the held-out model to the full-data refit; evaluate once on locked test.",
        "environment": {"python": platform.python_version(), "torch": torch.__version__,
                        "transformers": transformers.__version__, "underthesea": getattr(underthesea, "__version__", "unknown"),
                        "gpu": torch.cuda.get_device_name(0), "elapsed_seconds": round(time.monotonic() - start, 1)},
    }
    weight_files = sorted(weights_dir.glob("*.safetensors"))
    if not weight_files:
        raise RuntimeError("No safetensors weights were written")
    manifest["weights"] = [{"file": p.name, "bytes": p.stat().st_size, "sha256": sha256(p)} for p in weight_files]
    (output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output_dir), "rows": len(data), "tau_b": tau_b,
                      "weights": manifest["weights"]}, indent=2), flush=True)
    return manifest


def predict_cpu(texts: list[str], output_dir: Path = DEFAULT_DIR, batch_size: int = 16) -> tuple[np.ndarray, float]:
    """Load frozen final weights and Platt coefficients; safe for CPU test inference."""
    import torch

    manifest = json.loads((Path(output_dir) / "manifest.json").read_text(encoding="utf-8"))
    weights_dir = Path(output_dir) / "model"
    for entry in manifest["weights"]:
        if sha256(weights_dir / entry["file"]) != entry["sha256"]:
            raise ValueError("PhoBERT weight checksum mismatch")
    model = PhoBERTClassifier(model_name=str(weights_dir), device="cpu", word_segment=False)
    segmented = batch_segment_underthesea(texts)
    raw = model.predict_proba(segmented, batch_size=batch_size)
    cal = manifest["calibration"]
    logit = np.log(np.clip(raw, 1e-6, 1 - 1e-6) / np.clip(1 - raw, 1e-6, 1))
    p = torch.sigmoid(torch.as_tensor(cal["coefficient"][0] * logit + cal["intercept"])).numpy()
    return p.astype(float), float(cal["tau_b"])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_DIR)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()
    fit_final(args.output_dir, args.epochs, args.batch_size)


if __name__ == "__main__":
    main()
