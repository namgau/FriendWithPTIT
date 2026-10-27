from __future__ import annotations

import json
import time

from fastapi.testclient import TestClient

from serving.app import app
from serving.model_registry import ModelRegistry

client = TestClient(app)


def profile(profile_id: str, **overrides) -> dict:
    base = {
        "id": profile_id,
        "age": 20,
        "gender": "female",
        "school": "Học viện Công nghệ Bưu chính Viễn thông",
        "campus": "Hà Đông",
        "faculty": "Công nghệ thông tin",
        "gpa_tier": "GOOD",
        "interests": ["Cà phê", "Âm nhạc", "Du lịch", "Lập trình"],
        "mode": "FRIENDS",
        "distance_km": 2.0,
        "alumni": False,
    }
    base.update(overrides)
    return base


def messages(count: int = 10, *, now: int | None = None) -> list[dict]:
    now = now if now is not None else int(time.time() * 1000)
    texts = [
        "Cậu có nghe bài hát mới không? Mình thấy rất hay và thú vị.",
        "Mình vừa đi uống cà phê ở Hà Đông, không gian khá chill.",
        "Cuối tuần này cậu có muốn đi du lịch đâu đó không?",
        "Mình đang học lập trình, có chỗ nào chưa hiểu thì hỏi cậu nhé?",
        "Hôm nay trời đẹp quá, mình thấy vui và hào hứng.",
    ]
    result = []
    for index in range(count):
        result.append(
            {
                "sender": "viewer" if index % 2 == 0 else "partner",
                "text": texts[index % len(texts)],
                "created_at": now - (count - index) * 60_000,
            }
        )
    return result


def test_healthz() -> None:
    response = client.get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "fwp-ml-serving"


def test_rank_orders_shared_interests_first() -> None:
    viewer = profile("viewer")
    close = profile("close", distance_km=1.0, interests=["Cà phê", "Lập trình"])
    far = profile(
        "far",
        age=35,
        school="Trường khác",
        campus="Khác",
        faculty="Khác",
        gpa_tier="AVERAGE",
        interests=["Bóng đá"],
        mode="DATING",
        distance_km=40.0,
    )

    response = client.post("/m1/rank", json={"viewer": viewer, "candidates": [far, close]})
    assert response.status_code == 200
    body = response.json()
    assert body["model_source"] == "fallback_rules"
    assert [item["profile_id"] for item in body["items"]] == ["close", "far"]
    assert body["items"][0]["score"] > body["items"][1]["score"]
    assert any("sở thích chung" in reason for reason in body["items"][0]["reasons"])


def test_rank_top_k() -> None:
    viewer = profile("viewer")
    candidates = [profile("a"), profile("b"), profile("c")]
    response = client.post("/m1/rank", json={"viewer": viewer, "candidates": candidates, "top_k": 2})
    assert response.status_code == 200
    assert len(response.json()["items"]) == 2


def test_rank_validation_rejects_underage_profile() -> None:
    response = client.post(
        "/m1/rank",
        json={"viewer": profile("viewer"), "candidates": [profile("kid", age=15)]},
    )
    assert response.status_code == 422


def test_compatibility_reveal_requires_opt_in_and_messages() -> None:
    payload = {
        "viewer_interests": ["Cà phê", "Âm nhạc", "Du lịch", "Lập trình"],
        "partner_interests": ["cà phê", "âm nhạc", "du lịch", "lập trình"],
        "messages": messages(10),
        "viewer_opted_in": True,
        "partner_opted_in": True,
    }
    response = client.post("/m2/compat", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["reveal_eligible"] is True
    assert body["blur_level"] == 0.0
    assert body["score"] >= 72
    assert body["message_count"] == 10
    assert 0 <= body["interest"] <= 100

    blocked = client.post("/compat/update", json={**payload, "partner_opted_in": False})
    assert blocked.status_code == 200
    assert blocked.json()["reveal_eligible"] is False
    assert blocked.json()["blur_level"] > 0


def test_health_healthy_and_at_risk_levels() -> None:
    healthy = client.post(
        "/m3/health",
        json={
            "messages": messages(10),
            "viewer_interests": ["Cà phê", "Âm nhạc"],
            "partner_interests": ["Cà phê", "Du lịch"],
        },
    )
    assert healthy.status_code == 200
    assert healthy.json()["level"] == "HEALTHY"
    assert healthy.json()["suggestions"]

    stale = messages(10, now=int(time.time() * 1000) - 72 * 3_600_000)
    for message in stale:
        message["text"] = "ừ"
    risky = client.post(
        "/m3/health",
        json={
            "messages": stale,
            "viewer_interests": [],
            "partner_interests": [],
            "now": int(time.time() * 1000),
        },
    )
    assert risky.status_code == 200
    body = risky.json()
    assert body["level"] == "AT_RISK"
    assert body["score"] >= 64
    assert any("ngày" in reason for reason in body["reasons"])


def test_model_registry_activation(tmp_path) -> None:
    registry = ModelRegistry(tmp_path)
    version_dir = tmp_path / "m1" / "1.2.0"
    version_dir.mkdir(parents=True)
    (version_dir / "metadata.json").write_text(
        json.dumps({"seed": 42, "metric": {"ndcg_at_10": 0.71}}),
        encoding="utf-8",
    )

    versions = registry.versions("m1")
    assert len(versions) == 1
    assert registry.active("m1").version == "1.2.0"

    artifact = registry.activate("m1", "1.2.0")
    assert artifact.metadata["seed"] == 42
    assert registry.versions("m1")[0].active is True
