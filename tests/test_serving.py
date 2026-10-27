from fastapi.testclient import TestClient

from serving.app import app

client = TestClient(app)
H = 3_600_000  # 1 giờ (ms)


def profile(uid, **kw):
    base = dict(user_id=uid, school_id="ptit", gpa_tier=2, graduation_status="student", age=21,
                gender="f", seeking_genders=["m"], interest_tags=["music"], lat=20.98, lon=105.79)
    base.update(kw)
    return base


VIEWER = profile("me", gender="m", seeking_genders=["f"], interest_tags=["music", "film", "travel"])


def test_healthz():
    r = client.get("/healthz")
    assert r.status_code == 200 and r.json()["status"] == "ok"


def test_rank_orders_by_common_tags_and_filters():
    cands = [
        profile("a", interest_tags=["music"]),
        profile("b", interest_tags=["music", "film", "travel"]),
        profile("far", lat=10.77, lon=106.70),                      # TP.HCM, quá 30 km
        profile("old", age=40),
        profile("wrong_gender", gender="m"),
    ]
    r = client.post("/rank", json={"viewer": VIEWER, "candidates": cands}).json()
    assert [c["user_id"] for c in r["ranked"]] == ["b", "a"]
    reasons = {f["user_id"]: f["reason"] for f in r["filtered_out"]}
    assert reasons == {"far": "distance", "old": "age", "wrong_gender": "gender_mismatch"}


def test_rank_optional_filters():
    cands = [profile("x", gpa_tier=0), profile("y", graduation_status="alumni", school_id="hust")]
    prefs = {"max_gpa_tier_gap": 1, "school_ids": ["ptit"]}
    r = client.post("/rank", json={"viewer": VIEWER, "preferences": prefs, "candidates": cands}).json()
    assert r["ranked"] == []
    assert {f["reason"] for f in r["filtered_out"]} == {"gpa_tier", "school"}


def test_blur_monotonic_and_reveal_rule():
    levels = [client.post("/blur-level", json={"n_messages": n, "days_since_match": d}).json()
              for n, d in [(0, 0), (10, 1), (30, 3), (60, 7)]]
    blur = [x["blur_level"] for x in levels]
    assert blur == sorted(blur, reverse=True) and blur[0] == 1.0
    assert levels[-1]["revealed"] and levels[-1]["blur_level"] == 0.0
    # Đủ thời gian nhưng chưa đủ tin nhắn tối thiểu thì không rõ hẳn
    few = client.post("/blur-level", json={"n_messages": 10, "days_since_match": 30}).json()
    assert not few["revealed"] and few["blur_level"] > 0


def chat(times_h, texts, start=0):
    return [{"sender_id": "me" if i % 2 == 0 else "you", "timestamp_ms": start + int(t * H), "text": txt}
            for i, (t, txt) in enumerate(zip(times_h, texts))]


def test_health_lively_vs_dying():
    lively = chat([0, 0.1, 0.2, 0.3, 0.4, 0.5],
                  ["hôm nay đi học thế nào", "vui lắm, còn cậu thì sao?", "tớ vừa thi xong môn học máy",
                   "chúc mừng nha, khó không?", "khá khó nhưng ổn, cuối tuần cậu rảnh không?", "rảnh nè, đi cà phê nhé"])
    dying = chat([0, 2, 3, 9, 10, 20],
                 ["hôm nay cậu làm gì vui không?", "ừ", "cuối tuần đi đâu không?", "k", "sao vậy?", "ok"])
    r1 = client.post("/health/score", json={"conversation_id": "c1", "viewer_id": "me",
                                            "messages": lively, "now_ms": int(0.6 * H)}).json()
    r2 = client.post("/health/score", json={"conversation_id": "c2", "viewer_id": "me",
                                            "messages": dying, "now_ms": int(70 * H)}).json()
    assert r1["level"] == "healthy" and r1["suggestions"] == []
    assert r2["level"] == "at_risk" and r2["suggestions"]
    assert r2["p_disengage"] > r1["p_disengage"]


def test_health_ignores_future_messages():
    msgs = chat([0, 1, 100], ["chào", "chào cậu", "tin ở tương lai"])
    r = client.post("/health/score", json={"conversation_id": "c", "viewer_id": "me",
                                           "messages": msgs, "now_ms": int(1.5 * H)}).json()
    assert "long_silence" not in r["top_factors"]


def test_safety_nudges_only_toxic_draft():
    ok = client.post("/safety/score", json={"conversation_id": "c",
                                            "draft": {"sender_id": "me", "timestamp_ms": 0, "text": "mai đi ăn không"}}).json()
    bad = client.post("/safety/score", json={"conversation_id": "c",
                                             "draft": {"sender_id": "me", "timestamp_ms": 0, "text": "mày ngu thế"}}).json()
    assert not ok["should_nudge"] and ok["toxicity"] == 0
    assert bad["should_nudge"] and bad["toxicity"] > 0.5
