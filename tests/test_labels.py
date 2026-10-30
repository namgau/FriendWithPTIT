"""Test nhãn Module 3 trên dữ liệu nhỏ dựng tay (CI không có dữ liệu thật)."""
import numpy as np
import pandas as pd

from src.m3_health.labels import head_a_samples, head_b_g1, label_at

H = 3600.0


def _conv(cid, times_h, group=None):
    return pd.DataFrame({"conversation_id": cid, "group_id": group or cid,
                         "turn_idx": range(len(times_h)), "timestamp_s": np.array(times_h) * H})


def test_head_a_labels_and_censoring():
    # a: 8 tin, im 50h sau tin 6 rồi nhắn tiếp ; b: 7 tin, kết thúc sớm ; c: kết thúc sát cuối dữ liệu
    msgs = pd.concat([_conv("a", [0, 1, 2, 3, 4, 5, 55, 56]),
                      _conv("b", [0, 1, 2, 3, 4, 5, 6]),
                      _conv("c", [190, 191, 192, 193, 194, 195, 196])])
    s = head_a_samples(msgs, delta_s=48 * H, start_turn=5, every=2, data_end_s=200 * H).set_index(
        ["conversation_id", "turn_idx"])
    assert list(s.index) == [("a", 5), ("a", 7), ("b", 5), ("c", 5)]       # tin thứ 6, mỗi 2 tin
    assert s.loc[("a", 5), "y"] == 1 and s.loc[("a", 5), "event"] == 1     # im 50h > 48h nhưng có tin sau
    assert s.loc[("b", 5), "y"] == 0                                       # có tin sau 1h
    assert s.loc[("a", 7), "y"] == 1 and s.loc[("a", 7), "event"] == 0     # hết hội thoại, còn 144h dữ liệu
    assert s.loc[("c", 5), "y"] == 0 and not s.loc[("c", 5), "censored"]   # tin kế tiếp sau 1h → biết chắc
    late = head_a_samples(_conv("d", [190, 191, 192, 193, 194, 195]), 48 * H, data_end_s=200 * H)
    assert late["censored"].all() and late["y"].isna().all()               # chỉ còn 5h dữ liệu < Δ


def test_label_at_other_delta():
    s = head_a_samples(_conv("a", [0, 1, 2, 3, 4, 5, 8]), 48 * H, data_end_s=100 * H)
    assert label_at(s, 1 * H).iloc[0] == 1 and label_at(s, 24 * H).iloc[0] == 0


def test_head_b_prefix_stops_before_attack():
    msgs = pd.DataFrame({
        "conversation_id": ["bad"] * 5 + ["ok"] * 4, "group_id": "page_1",
        "turn_idx": [0, 1, 2, 3, 4, 0, 1, 2, 3],
        "is_attack": [False, False, False, True, False, False, False, False, False]})
    conv = pd.DataFrame({"conversation_id": ["bad", "ok"], "pair_id": ["ok", "bad"], "page_id": [1, 1],
                         "has_attack": [True, False], "orig_split": ["train", "train"]})
    out, info = head_b_g1(msgs, conv)
    out = out.set_index("conversation_id")
    assert out.loc["bad", "prefix_len"] == 3       # chỉ lượt 0–2, không thấy lượt công kích
    assert out.loc["ok", "prefix_len"] == 3        # lành: bỏ lượt cuối
    assert info["dropped_pairs_empty_prefix"] == 0


def test_head_b_drops_pair_when_prefix_empty():
    msgs = pd.DataFrame({"conversation_id": ["bad", "ok", "ok"], "group_id": "p", "turn_idx": [0, 0, 1],
                         "is_attack": [True, False, False]})
    conv = pd.DataFrame({"conversation_id": ["bad", "ok"], "pair_id": ["ok", "bad"], "page_id": [1, 1],
                         "has_attack": [True, False], "orig_split": ["train", "train"]})
    out, info = head_b_g1(msgs, conv)
    assert out.empty and info["dropped_pairs_empty_prefix"] == 1
