"""Test imbalance, calibration, threshold, report (src/eval)."""
import numpy as np
import pandas as pd
import pytest
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from src.eval import calibration as C
from src.eval import imbalance as I
from src.eval import report as R
from src.eval import threshold as T


@pytest.fixture
def imbalanced():
    rng = np.random.default_rng(0)
    n = 4000
    y = (rng.random(n) < 0.1).astype(int)
    X = rng.normal(0, 1, (n, 3)) + y[:, None] * 1.0
    return X, y


@pytest.mark.parametrize("strategy", I.STRATEGIES)
def test_pipelines_fit_and_resample_only_in_fit(imbalanced, strategy):
    X, y = imbalanced
    pipe = I.make_pipeline(LogisticRegression(), strategy, preprocess=[("scale", StandardScaler())])
    pipe.fit(X, y)
    p = pipe.predict_proba(X)[:, 1]
    assert p.shape == (len(y),)  # predict trên đủ mẫu: sampler không chạy lúc dự đoán
    if strategy == "none":
        assert abs(p.mean() - y.mean()) < 0.02
    else:
        assert p.mean() > 2 * y.mean()  # học trên 50/50 → xác suất bị đẩy lên


def test_prior_shift_restores_prevalence(imbalanced):
    X, y = imbalanced
    p = I.make_pipeline(LogisticRegression(), "undersample").fit(X, y).predict_proba(X)[:, 1]
    q = I.prior_shift(p, I.train_prevalence_after("undersample", y.mean()), y.mean())
    assert abs(q.mean() - y.mean()) < 0.02
    assert np.allclose(I.prior_shift(p, 0.3, 0.3), p)


def test_class_weight_rejected_when_unsupported():
    from sklearn.neighbors import KNeighborsClassifier
    with pytest.raises(ValueError):
        I.make_pipeline(KNeighborsClassifier(), "class_weight")


@pytest.mark.parametrize("method", ["platt", "isotonic"])
def test_calibration_reduces_ece(method):
    rng = np.random.default_rng(1)
    true_p = rng.uniform(0.05, 0.6, 20000)
    y = (rng.random(len(true_p)) < true_p).astype(int)
    raw = true_p ** 0.4  # quá tự tin
    cal = C.fit_calibrator(method, raw[:10000], y[:10000])
    assert C.ece(y[10000:], cal(raw[10000:])) < 0.3 * C.ece(y[10000:], raw[10000:])


def test_thresholds():
    rng = np.random.default_rng(2)
    y = (rng.random(5000) < 0.2).astype(int)
    p = np.clip(0.2 + 0.4 * (y - 0.2) + rng.normal(0, 0.15, len(y)), 0, 1)
    r = T.threshold_by_fpr(y, p, 0.05)
    assert r["fpr"] <= 0.05
    assert T.threshold_by_recall(y, p, 0.8)["recall"] >= 0.8
    cheap_fn = T.threshold_by_cost(y, p, c_fn=10, c_fp=1)["threshold"]
    cheap_fp = T.threshold_by_cost(y, p, c_fn=1, c_fp=10)["threshold"]
    assert cheap_fn < cheap_fp  # FN đắt → hạ ngưỡng
    t1, t2 = T.choose_tau1_tau2(y, p)
    assert t1 <= t2
    assert T.levels(np.array([0.0, 1.0]), t1, t2).tolist() == [0, 2]
    assert T.levels(np.array([0.1, 0.5, 0.9]), 0.3, 0.6).tolist() == [0, 1, 2]


def test_report_cv_summary_and_ledger(tmp_path):
    res = pd.DataFrame({"model": ["a"] * 3 + ["b"] * 3, "auc": [0.7, 0.72, 0.74, 0.6, 0.6, 0.6]})
    s = R.cv_summary(res, ["model"], ["auc"]).set_index("model")
    assert s.loc["a", "auc_mean"] == pytest.approx(0.72) and s.loc["a", "auc_std"] == pytest.approx(0.02)
    assert s.loc["b", "n_folds"] == 3
    assert R.mean_sd([0.7, 0.72, 0.74]) == "0.720 ± 0.020"
    ledger = tmp_path / "ledger.csv"
    R.append_ledger("m3", "A", {"x": 1}, [], [42, 43], "lr", "pr_auc", [0.5, 0.6], ledger=ledger)
    R.append_ledger("m3", "A", {"x": 1}, [], [42], "lr", "pr_auc", [0.5], ledger=ledger)
    df = pd.read_csv(ledger)
    assert list(df.columns) == R.LEDGER_COLUMNS and len(df) == 2
    assert df["config_hash"].nunique() == 1
