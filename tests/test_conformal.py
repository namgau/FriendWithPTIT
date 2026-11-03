import numpy as np
import pandas as pd

from src.m3_health.run_conformal_a import CQR, _conf_q, _weighted_q


def test_conf_q_finite_sample():
    s = np.arange(1, 10, dtype=float)          # n = 9 → k = ceil(10 · 0.9) = 9
    assert _conf_q(s, 0.1) == 9
    assert _conf_q(s[:8], 0.1) == np.inf       # n = 8 → k = 9 > n


def test_weighted_equals_unweighted_when_uniform():
    rng = np.random.default_rng(0)
    s = rng.normal(size=500)
    q = _weighted_q(s, np.ones(500), np.ones(3))
    assert np.allclose(q, _conf_q(s))


def test_cqr_coverage_on_synthetic():
    rng = np.random.default_rng(1)
    n = 6000
    X = pd.DataFrame({"a": rng.normal(size=n), "b": rng.normal(size=n)})
    y = 2 * X.a + rng.gumbel(size=n) * (1 + X.b.abs())
    known = np.ones(n, bool)
    m = CQR(X[:3000], y[:3000], 0).calibrate(X[3000:4500], y[3000:4500], known[3000:4500])
    L, U = m.bounds(X[4500:])
    yt = y[4500:]
    assert np.mean(yt >= L) >= 0.87 and np.mean(yt <= U) >= 0.87
