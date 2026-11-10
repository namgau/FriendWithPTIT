import json
from pathlib import Path

import pytest
from serving.engines import compatibility, rank
from serving.schemas_mobile import CompatRequest, RankRequest


@pytest.mark.parametrize("case", json.loads((Path(__file__).parent / "fixtures/m2_blur_cases.json").read_text()))
def test_blur_cases(case):
    messages = []
    for sender in ("viewer", "partner"):
        for i in range(case[sender]):
            timestamp = 1_700_000_000_000 + (i % case["days"]) * 86_400_000 if case["days"] else 0
            messages.append({"sender": sender, "created_at": timestamp})
    payload = CompatRequest(messages=messages, viewer_opted_in=case["opt_in"], partner_opted_in=case["opt_in"])
    result = compatibility(payload)
    assert result.blur_level == pytest.approx(case["blur"], abs=0.0001)
    assert result.reveal_eligible == case["opt_in"]


def test_rank_filter_and_common_interest_order():
    viewer = {"id": "me", "age": 20, "interests": [" Coffee ", "MUSIC"], "school": "PTIT"}
    candidates = [
        {"id": "a", "age": 20, "interests": ["music"], "alumni": True, "distance_km": 1},
        {"id": "b", "age": 20, "interests": ["COFFEE", "music"], "alumni": True, "distance_km": 2},
        {"id": "c", "age": 20, "interests": ["coffee", "music"], "alumni": False, "distance_km": 2},
        {"id": "d", "age": 20, "interests": ["coffee"], "alumni": True, "distance_km": None},
    ]
    result = rank(RankRequest(viewer=viewer, candidates=candidates, filters={"graduation": "ALUMNI", "max_distance_km": 10}))
    assert [(x.profile_id, x.score) for x in result.items] == [("b", 100), ("a", 50)]


@pytest.mark.parametrize("case", json.loads((Path(__file__).parent / "fixtures/m1_rank_cases.json").read_text()))
def test_shared_rank_fixture(case):
    result = rank(RankRequest(**{k: case[k] for k in ("viewer", "candidates", "filters")}))
    assert [x.profile_id for x in result.items] == case["expected"]
    assert [x.score for x in result.items] == case["scores"]


def test_utc7_midnight_missing_timestamp_system_and_withdrawal():
    # 16:59 and 17:01 UTC straddle midnight in Vietnam.
    timestamps = [1_767_286_740_000, 1_767_286_860_000]
    messages = [{"sender": "viewer" if i % 2 == 0 else "partner", "created_at": timestamps[i % 2]} for i in range(30)]
    messages += [{"sender": "system", "created_at": timestamps[1] + 86_400_000}]
    two_days = compatibility(CompatRequest(messages=messages, viewer_opted_in=True, partner_opted_in=True))
    assert not two_days.reveal_eligible and two_days.blur_level == pytest.approx(0.2833, abs=0.0001)
    messages[0]["created_at"] = timestamps[1] + 86_400_000
    revealed = compatibility(CompatRequest(messages=messages, viewer_opted_in=True, partner_opted_in=True))
    assert revealed.reveal_eligible and revealed.blur_level == 0
    withdrawn = compatibility(CompatRequest(messages=messages, viewer_opted_in=True, partner_opted_in=False))
    assert not withdrawn.reveal_eligible and withdrawn.blur_level == pytest.approx(0.15)
    for message in messages: message["created_at"] = None
    assert compatibility(CompatRequest(messages=messages)).blur_level == pytest.approx(0.55)
