import numpy as np
from sklearn.metrics import average_precision_score, roc_auc_score

from src.eval.bootstrap import cluster_bootstrap, paired_cluster_bootstrap


def _clustered(n_groups=200, size=10, seed=0):
    """Nhãn và điểm tương quan mạnh trong cùng group (giống các mẫu (c, t) của một hội thoại)."""
    rng = np.random.default_rng(seed)
    groups = np.repeat(np.arange(n_groups), size)
    y = np.repeat(rng.random(n_groups) < 0.3, size).astype(int)
    score = y * 0.8 + np.repeat(rng.normal(0, 0.6, n_groups), size) + rng.normal(0, 0.1, len(y))
    return y, score, groups


def test_ci_contains_estimate_and_reproducible():
    y, s, g = _clustered()
    r1 = cluster_bootstrap(roc_auc_score, y, s, g, B=300, seed=1)
    r2 = cluster_bootstrap(roc_auc_score, y, s, g, B=300, seed=1)
    assert r1 == r2
    assert r1["lo"] <= r1["estimate"] <= r1["hi"]
    assert r1["n_groups"] == 200 and r1["B_valid"] == 300


def test_cluster_ci_wider_than_iid():
    y, s, g = _clustered()
    clus = cluster_bootstrap(average_precision_score, y, s, g, B=500, seed=2)
    iid = cluster_bootstrap(average_precision_score, y, s, np.arange(len(y)), B=500, seed=2)
    assert clus["hi"] - clus["lo"] > 1.5 * (iid["hi"] - iid["lo"])


def test_paired_identical_models():
    y, s, g = _clustered()
    r = paired_cluster_bootstrap(roc_auc_score, y, s, s, g, B=200)
    assert r["estimate"] == 0 and r["lo"] == 0 and r["hi"] == 0 and r["p_value"] == 1.0


def test_paired_detects_better_model():
    y, s, g = _clustered()
    noise = np.random.default_rng(3).normal(0, 2, len(y))
    r = paired_cluster_bootstrap(roc_auc_score, y, s, s + noise, g, B=500)
    assert r["estimate"] > 0 and r["lo"] > 0 and r["p_value"] < 0.05
