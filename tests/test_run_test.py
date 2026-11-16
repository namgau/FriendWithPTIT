import datetime as dt
import json

import numpy as np
import pytest

import src.m3_health.run_test as rt


def test_guard_blocks_until_flag_date_and_single_open(tmp_path, monkeypatch):
    monkeypatch.setattr(rt, "MARKER", tmp_path / "test_opened.json")
    with pytest.raises(SystemExit, match="--open"):
        rt.guard(False, False, dt.date(2026, 11, 20))
    with pytest.raises(SystemExit, match="Chưa tới"):
        rt.guard(True, False, dt.date(2026, 11, 19))
    assert rt.guard(True, False, dt.date(2026, 11, 20)) == []
    (tmp_path / "test_opened.json").write_text(json.dumps([{"opened_at": "2026-11-20T09:00:00"}]))
    with pytest.raises(SystemExit, match="--again"):
        rt.guard(True, False, dt.date(2026, 11, 21))
    assert len(rt.guard(True, True, dt.date(2026, 11, 21))) == 1


def test_evaluate_reports_ci_threshold_and_paired_delta():
    rng = np.random.default_rng(0)
    groups = np.repeat(np.arange(200), 3)
    y = rng.random(600) < 0.2
    good = np.clip(y * 0.5 + rng.random(600) * 0.5, 0, 1)
    noise = rng.random(600)
    cfg = {"bootstrap": {"B": 200, "ci": 0.95}}
    rows = rt.evaluate("X", y.astype(int), {"noise": noise, "good": good}, groups, "good", {"good": 0.5}, cfg)
    by = {r["model"]: r for r in rows}
    assert by["good"]["pr_auc_lo"] <= by["good"]["pr_auc"] <= by["good"]["pr_auc_hi"]
    assert {"precision", "recall", "fpr"} <= by["good"].keys() and "delta_vs_chosen" not in by["good"]
    assert by["noise"]["delta_lo"] > 0  # chốt tốt hơn nhiễu, CI không chứa 0
