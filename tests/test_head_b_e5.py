import numpy as np
import pandas as pd

from src.m3_health.run_head_b_e5 import _suspect, swear_features


def test_swear_directed_vs_exclaim():
    f = swear_features(pd.Series(["Đm nhớ cái thời này vl", "mày ngu vl", "hôm nay đẹp trời", None]))
    assert f.swear_exclaim.tolist() == [1, 0, 0, 0]
    assert f.swear_directed.tolist() == [0, 1, 0, 0]


def test_suspect_flags_confident_disagreement_only():
    y = np.array([0, 0, 0, 1, 1, 1])
    p = np.array([0.1, 0.2, 0.95, 0.9, 0.8, 0.05])
    assert _suspect(y, p).tolist() == [False, False, True, False, False, True]
