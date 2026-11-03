import numpy as np
import pandas as pd

from src.m3_health.run_error_analysis import VI_DIACRITIC, TEENCODE, lead_time


def test_lead_time_counts_back_along_flagged_run():
    lab = pd.DataFrame({"conversation_id": [1, 1, 1, 2, 2], "turn_idx": [5, 7, 9, 5, 7]})
    X = pd.DataFrame({"conv_age_log": np.log1p([0, 3600, 7200, 0, 100])})
    y = np.array([0, 0, 1, 0, 1])
    flag = np.array([True, True, True, False, True])
    lt = lead_time(lab, X, y, flag)
    assert lt["n_tp"] == 2 and lt["lead_0"] == 0.5
    assert abs(lt["lead_median_h_if_pos"] - 2.0) < 1e-9


def test_lead_time_run_does_not_cross_conversations():
    lab = pd.DataFrame({"conversation_id": [1, 2], "turn_idx": [5, 5]})
    X = pd.DataFrame({"conv_age_log": np.log1p([3600, 7200])})
    lt = lead_time(lab, X, np.array([0, 1]), np.array([True, True]))
    assert lt["lead_0"] == 1.0


def test_vietnamese_slices():
    assert TEENCODE.search("ko bik nữa") and not TEENCODE.search("không biết")
    assert VI_DIACRITIC.search("không") and not VI_DIACRITIC.search("khong biet")
