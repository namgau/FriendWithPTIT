"""Tổng hợp CV (mean ± SD), băm config/dữ liệu và ghi experiments/ledger.csv.

Mỗi lần chạy thí nghiệm = 1 dòng ledger (docs/07 §2) để A kiểm tra tái lập.
"""
from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
import subprocess
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "experiments" / "ledger.csv"
LEDGER_COLUMNS = ["run_id", "date", "module", "owner", "config_hash", "data_hash", "git_commit", "seeds",
                  "model", "metric_name", "metric_mean", "metric_sd", "notes"]


def config_hash(cfg: dict) -> str:
    return hashlib.sha256(json.dumps(cfg, sort_keys=True, default=str).encode()).hexdigest()[:12]


def data_hash(*paths) -> str:
    """Băm nội dung các file dữ liệu đầu vào (đọc theo khối, dùng được cho file lớn)."""
    h = hashlib.sha256()
    for p in sorted(map(Path, paths)):
        with open(p, "rb") as f:
            for block in iter(lambda: f.read(1 << 20), b""):
                h.update(block)
    return h.hexdigest()[:12]


def git_commit() -> str:
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True,
                              text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def cv_summary(results: pd.DataFrame, by: list[str], metrics: list[str]) -> pd.DataFrame:
    """``results``: 1 dòng / (model, seed, fold). Trả mean, sd (ddof=1), n_folds cho từng metric."""
    g = results.groupby(by)[metrics]
    out = g.agg(["mean", "std"])
    out.columns = [f"{m}_{s}" for m, s in out.columns]
    out["n_folds"] = g.size()
    return out.reset_index()


def mean_sd(values, digits: int = 3) -> str:
    v = np.asarray(values, float)
    return f"{v.mean():.{digits}f} ± {v.std(ddof=1) if len(v) > 1 else 0:.{digits}f}"


def append_ledger(module: str, owner: str, cfg: dict, data_files, seeds, model: str,
                  metric_name: str, values, notes: str = "", ledger: Path = LEDGER) -> dict:
    v = np.asarray(values, float)
    row = {"run_id": dt.datetime.now().strftime("%Y%m%d-%H%M%S"), "date": dt.date.today().isoformat(),
           "module": module, "owner": owner, "config_hash": config_hash(cfg),
           "data_hash": data_hash(*data_files) if data_files else "", "git_commit": git_commit(),
           "seeds": " ".join(map(str, seeds)), "model": model, "metric_name": metric_name,
           "metric_mean": round(float(v.mean()), 5), "metric_sd": round(float(v.std(ddof=1)) if len(v) > 1 else 0.0, 5),
           "notes": notes}
    new = not ledger.exists() or ledger.stat().st_size == 0
    with open(ledger, "a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=LEDGER_COLUMNS)
        if new:
            w.writeheader()
        w.writerow(row)
    return row
