from fastapi.testclient import TestClient

from serving.app import app

client = TestClient(app)


def setup_function():
    client.post("/app/reset")


def test_me_and_candidates_are_ranked_and_filtered():
    assert client.get("/app/me").json()["profile"]["user_id"] == "u_me"
    cards = client.get("/app/candidates").json()
    ids = [c["user_id"] for c in cards]
    assert "u_me" not in ids
    assert "u7" not in ids          # u7 chỉ tìm nữ → không hợp giới tính
    assert len(ids) == 7
    scores = [c["score"] for c in cards]
    assert scores == sorted(scores, reverse=True)


def test_swipe_match_chat_flow():
    first = client.get("/app/candidates").json()[0]
    r = client.post("/app/swipe", json={"target_id": first["user_id"], "like": True}).json()
    assert r["matched"]
    mid = r["match_id"]
    assert first["user_id"] not in [c["user_id"] for c in client.get("/app/candidates").json()]

    msgs = client.post(f"/app/matches/{mid}/messages", json={"text": "chào bạn"}).json()
    assert len(msgs) == 2 and msgs[0]["sender_id"] == "u_me"

    st = client.get(f"/app/matches/{mid}/state").json()
    assert 0 < st["blur"]["blur_level"] <= 1 and not st["blur"]["revealed"]
    assert st["health"]["level"] in {"healthy", "shallow", "at_risk"}
    assert 0 <= st["compat"] <= 1

    summary = client.get("/app/matches").json()
    assert summary[0]["match_id"] == mid and summary[0]["last_message"]["sender_id"] == first["user_id"]


def test_pass_does_not_match():
    first = client.get("/app/candidates").json()[0]
    r = client.post("/app/swipe", json={"target_id": first["user_id"], "like": False}).json()
    assert r == {"matched": False, "match_id": None}


def test_unknown_match_404():
    assert client.get("/app/matches/nope/state").status_code == 404
