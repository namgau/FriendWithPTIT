"""Unit tests for C6 Silent Conversation Checker (serving/silent_checker.py).

Kiểm thử:
1. Phân loại 4 mức silence tier (ACTIVE, SILENT_12H, SILENT_24H, SILENT_48H).
2. Trừ thời gian ngủ 00:00 - 07:00 UTC+7 (adjust_sleep_latency) tránh báo nhầm E3.
3. Đề xuất gợi ý hành động mở lời (recommended_action) theo ngữ cảnh và mức im lặng.
4. Quét và xếp hạng cấp thiết (scan_silent_conversations).
5. API endpoint POST /silent-checker/scan.
6. SilentCheckerJob chạy cron / background alert.
"""

from datetime import datetime, timedelta, timezone
import pytest
from fastapi.testclient import TestClient

from serving.app import app
from serving.schemas import Message
from serving.silent_checker import (
    ConversationScanItem,
    SilenceTier,
    SilentCheckerJob,
    SilentScanRequest,
    adjust_sleep_latency,
    check_conversation,
    classify_silence_tier,
    scan_silent_conversations,
)

H = 3_600_000  # 1 giờ (ms)
TZ_VN = timezone(timedelta(hours=7))  # UTC+7


def _vn_ms(year: int, month: int, day: int, hour: int, minute: int = 0) -> int:
    """Tạo timestamp Unix mili giây theo giờ Việt Nam (UTC+7)."""
    dt = datetime(year, month, day, hour, minute, tzinfo=TZ_VN)
    return int(dt.timestamp() * 1000)


# =====================================================================
# 1. Test phân loại 4 mức silence tier
# =====================================================================

def test_silence_tier_enum_values():
    assert SilenceTier.ACTIVE == "ACTIVE"
    assert SilenceTier.SILENT_12H == "SILENT_12H"
    assert SilenceTier.SILENT_24H == "SILENT_24H"
    assert SilenceTier.SILENT_48H == "SILENT_48H"

    # Case-insensitive equality
    assert SilenceTier.ACTIVE == "active"
    assert SilenceTier.SILENT_12H == "silent_12h"
    assert SilenceTier("active") == SilenceTier.ACTIVE
    assert SilenceTier("SILENT_24H") == SilenceTier.SILENT_24H


def test_classify_silence_tier_boundaries():
    # < 12h -> ACTIVE
    assert classify_silence_tier(hours=0.0) == SilenceTier.ACTIVE
    assert classify_silence_tier(hours=6.5) == SilenceTier.ACTIVE
    assert classify_silence_tier(hours=11.99) == SilenceTier.ACTIVE
    assert classify_silence_tier(latency_ms=11 * H) == SilenceTier.ACTIVE

    # 12h - 24h -> SILENT_12H
    assert classify_silence_tier(hours=12.0) == SilenceTier.SILENT_12H
    assert classify_silence_tier(hours=18.5) == SilenceTier.SILENT_12H
    assert classify_silence_tier(hours=23.99) == SilenceTier.SILENT_12H
    assert classify_silence_tier(latency_ms=12 * H) == SilenceTier.SILENT_12H

    # 24h - 48h -> SILENT_24H
    assert classify_silence_tier(hours=24.0) == SilenceTier.SILENT_24H
    assert classify_silence_tier(hours=36.0) == SilenceTier.SILENT_24H
    assert classify_silence_tier(hours=47.99) == SilenceTier.SILENT_24H
    assert classify_silence_tier(latency_ms=30 * H) == SilenceTier.SILENT_24H

    # >= 48h -> SILENT_48H
    assert classify_silence_tier(hours=48.0) == SilenceTier.SILENT_48H
    assert classify_silence_tier(hours=72.0) == SilenceTier.SILENT_48H
    assert classify_silence_tier(hours=120.0) == SilenceTier.SILENT_48H
    assert classify_silence_tier(latency_ms=50 * H) == SilenceTier.SILENT_48H


def test_classify_silence_tier_invalid_args():
    with pytest.raises(ValueError):
        classify_silence_tier()


# =====================================================================
# 2. Test trừ thời gian ngủ (00:00 - 07:00 UTC+7)
# =====================================================================

def test_adjust_sleep_latency_daytime():
    # Nhắn lúc 09:00, xem lúc 17:00 cùng ngày (thời gian ban ngày, không giao 00:00-07:00)
    t1 = _vn_ms(2026, 11, 14, 9, 0)
    t2 = _vn_ms(2026, 11, 14, 17, 0)
    raw = t2 - t1
    adj = adjust_sleep_latency(t1, t2)
    assert raw == 8 * H
    assert adj == 8 * H  # Không trừ gì vì hoàn toàn trong giờ thức


def test_adjust_sleep_latency_overnight():
    # Nhắn lúc 23:00 tối, xem lúc 08:00 sáng hôm sau (khoảng cách 9h)
    # Khung giờ ngủ 00:00 - 07:00 (7h) nằm trọn giữa 2 mốc
    t1 = _vn_ms(2026, 11, 13, 23, 0)
    t2 = _vn_ms(2026, 11, 14, 8, 0)
    raw = t2 - t1
    adj = adjust_sleep_latency(t1, t2)
    assert raw == 9 * H
    assert adj == 2 * H  # 9h - 7h ngủ = 2h thức


def test_adjust_sleep_latency_entirely_in_sleep():
    # Cả hai mốc đều trong giờ ngủ: 01:00 đến 05:00 sáng
    t1 = _vn_ms(2026, 11, 14, 1, 0)
    t2 = _vn_ms(2026, 11, 14, 5, 0)
    adj = adjust_sleep_latency(t1, t2)
    assert adj == 0  # Toàn bộ 4h là giờ ngủ, thời gian thức = 0


def test_adjust_sleep_latency_partial_sleep():
    # Nhắn lúc 04:00 sáng (trong giờ ngủ), xem lúc 10:00 sáng
    # Ngủ: 04:00 - 07:00 = 3h. Thức: 07:00 - 10:00 = 3h. Tổng raw = 6h.
    t1 = _vn_ms(2026, 11, 14, 4, 0)
    t2 = _vn_ms(2026, 11, 14, 10, 0)
    raw = t2 - t1
    adj = adjust_sleep_latency(t1, t2)
    assert raw == 6 * H
    assert adj == 3 * H


def test_adjust_sleep_latency_multiday():
    # 48h từ 12:00 ngày 14 đến 12:00 ngày 16
    # Giao với 2 đêm ngủ (đêm ngày 14->15 và ngày 15->16): 2 x 7h = 14h ngủ
    t1 = _vn_ms(2026, 11, 14, 12, 0)
    t2 = _vn_ms(2026, 11, 16, 12, 0)
    raw = t2 - t1
    adj = adjust_sleep_latency(t1, t2)
    assert raw == 48 * H
    assert adj == 34 * H  # 48h - 14h = 34h


def test_adjust_sleep_latency_edge_cases():
    t = _vn_ms(2026, 11, 14, 10, 0)
    assert adjust_sleep_latency(t, t) == 0
    assert adjust_sleep_latency(t + 1000, t) == 0


def test_adjust_sleep_prevents_e3_false_alarm():
    # Phân tích lỗi E3: Tin nhắn gửi lúc 23:30, đến 11:45 trưa hôm sau kiểm tra.
    # Raw latency: 12.25 giờ -> nếu không trừ ngủ sẽ nhảy sang SILENT_12H (báo động giả).
    # Có trừ ngủ: 12.25h - 7h (00:00-07:00) = 5.25h -> vẫn là ACTIVE.
    t_last = _vn_ms(2026, 11, 13, 23, 30)
    t_now = _vn_ms(2026, 11, 14, 11, 45)

    adj = adjust_sleep_latency(t_last, t_now)
    assert adj == int(5.25 * H)
    assert classify_silence_tier(latency_ms=adj) == SilenceTier.ACTIVE

    res = check_conversation(
        conv_id="conv_e3",
        messages=[Message(sender_id="u1", timestamp_ms=t_last, text="Mai gặp nhé")],
        viewer_id="u2",
        now_ms=t_now,
    )
    assert res.tier == SilenceTier.ACTIVE
    assert res.needs_nudge is False


# =====================================================================
# 3. Test gợi ý hành động khi hội thoại im lặng (recommended_action)
# =====================================================================

def test_check_conversation_empty_messages():
    res = check_conversation(conv_id="empty_conv", messages=[], viewer_id="u1")
    assert res.tier == SilenceTier.ACTIVE
    assert res.raw_silence_h == 0.0
    assert res.adjusted_silence_h == 0.0
    assert "chào làm quen" in (res.recommended_action or "").lower()
    assert res.needs_nudge is False


def test_check_conversation_active_healthy():
    t_start = _vn_ms(2026, 11, 14, 10, 0)
    t_now = _vn_ms(2026, 11, 14, 12, 0)  # 2h sau
    msgs = [
        {"sender_id": "u1", "timestamp_ms": t_start, "text": "Hôm nay bạn rảnh không?"},
        {"sender_id": "u2", "timestamp_ms": t_start + int(0.5 * H), "text": "Mình rảnh nè, có gì không bạn?"},
    ]
    res = check_conversation(conv_id="c_active", messages=msgs, viewer_id="u1", now_ms=t_now)
    assert res.tier == SilenceTier.ACTIVE
    assert res.adjusted_silence_h == 1.5
    assert res.needs_nudge is False
    assert res.recommended_action is None


def test_check_conversation_active_shallow_suggestions():
    # Hội thoại mới nhắn (active) nhưng cụt ngủn liên tục
    t_start = _vn_ms(2026, 11, 14, 10, 0)
    msgs = [
        {"sender_id": "u1", "timestamp_ms": t_start + int(0.1 * H), "text": "ừ"},
        {"sender_id": "u2", "timestamp_ms": t_start + int(0.2 * H), "text": "ok"},
        {"sender_id": "u1", "timestamp_ms": t_start + int(0.3 * H), "text": "k"},
    ]
    res = check_conversation(conv_id="c_shallow", messages=msgs, viewer_id="u1", now_ms=t_start + int(0.5 * H))
    assert res.tier == SilenceTier.ACTIVE
    assert "short_replies" in res.factors
    assert res.recommended_action is not None
    assert "sở thích" in res.recommended_action


def test_check_conversation_silent_12h():
    # 15h thức trôi qua (sau khi trừ ngủ)
    t_msg = _vn_ms(2026, 11, 13, 8, 0)
    t_now = _vn_ms(2026, 11, 14, 6, 0)  # 22h raw, trừ 6h ngủ = 16h thức
    # Partner (u2) gửi tin cuối -> viewer (u1) nợ tin trả lời
    msgs = [{"sender_id": "u2", "timestamp_ms": t_msg, "text": "Cuối tuần này cậu muốn đi đâu?"}]
    res = check_conversation(conv_id="c_12h", messages=msgs, viewer_id="u1", now_ms=t_now)

    assert res.tier == SilenceTier.SILENT_12H
    assert res.needs_nudge is True
    assert res.recommended_action is not None
    assert "đối phương đang chờ bạn" in res.recommended_action.lower()


def test_check_conversation_silent_24h():
    # Khoảng 30h thức trôi qua
    t_msg = _vn_ms(2026, 11, 12, 10, 0)
    t_now = _vn_ms(2026, 11, 14, 10, 0)  # 48h raw - 14h ngủ = 34h thức -> SILENT_24H
    msgs = [{"sender_id": "u1", "timestamp_ms": t_msg, "text": "Hôm nào rảnh cà phê nhé!"}]
    res = check_conversation(conv_id="c_24h", messages=msgs, viewer_id="u1", now_ms=t_now)

    assert res.tier == SilenceTier.SILENT_24H
    assert res.needs_nudge is True
    assert res.recommended_action is not None
    assert "24 giờ" in res.recommended_action or "phá băng" in res.recommended_action


def test_check_conversation_silent_48h():
    # Hơn 48h thức trôi qua -> SILENT_48H nguy cơ tắt
    t_msg = _vn_ms(2026, 11, 10, 8, 0)
    t_now = _vn_ms(2026, 11, 14, 8, 0)  # 96h raw - 28h ngủ = 68h thức -> SILENT_48H
    msgs = [{"sender_id": "u2", "timestamp_ms": t_msg, "text": "Cậu bận à?"}]
    res = check_conversation(conv_id="c_48h", messages=msgs, viewer_id="u1", now_ms=t_now)

    assert res.tier == SilenceTier.SILENT_48H
    assert res.needs_nudge is True
    assert res.recommended_action is not None
    assert "48 giờ" in res.recommended_action
    assert res.urgency_score > 6.0


# =====================================================================
# 4. Test quét danh sách & xếp hạng cấp thiết (scan_silent_conversations)
# =====================================================================

def test_scan_silent_conversations_urgency_ordering():
    t_now = _vn_ms(2026, 11, 15, 12, 0)

    # c_active: vừa nhắn 2h trước (10:00 cùng ngày)
    t_active = _vn_ms(2026, 11, 15, 10, 0)
    # c_12h: nhắn 20:00 hôm trước (raw 16h - 7h ngủ = 9h thức, cho thêm vài giờ để đạt ~15h)
    t_12h = _vn_ms(2026, 11, 14, 14, 0)  # raw 22h - 7h ngủ = 15h thức -> SILENT_12H
    # c_24h: nhắn 10:00 ngày 13 (raw 50h - 14h ngủ = 36h thức -> SILENT_24H)
    t_24h = _vn_ms(2026, 11, 13, 10, 0)
    # c_48h: nhắn 10:00 ngày 11 (raw 98h - 28h ngủ = 70h thức -> SILENT_48H)
    t_48h = _vn_ms(2026, 11, 11, 10, 0)

    conversations = [
        {"conversation_id": "c_active", "viewer_id": "u1", "messages": [{"sender_id": "u1", "timestamp_ms": t_active, "text": "hi"}]},
        {"conversation_id": "c_12h", "viewer_id": "u1", "messages": [{"sender_id": "u2", "timestamp_ms": t_12h, "text": "hello"}]},
        {"conversation_id": "c_24h", "viewer_id": "u1", "messages": [{"sender_id": "u2", "timestamp_ms": t_24h, "text": "are you there"}]},
        {"conversation_id": "c_48h", "viewer_id": "u1", "messages": [{"sender_id": "u2", "timestamp_ms": t_48h, "text": "bye"}]},
    ]

    results = scan_silent_conversations(conversations, now_ms=t_now)
    assert len(results) == 4

    # Kiểm tra thứ tự sắp xếp theo độ cấp thiết giảm dần
    ranked_ids = [r.conversation_id for r in results]
    assert ranked_ids == ["c_48h", "c_24h", "c_12h", "c_active"]

    # Kiểm tra điểm urgency_score giảm dần
    for i in range(len(results) - 1):
        assert results[i].urgency_score >= results[i + 1].urgency_score


# =====================================================================
# 5. Test API endpoint POST /silent-checker/scan
# =====================================================================

def test_api_silent_checker_scan():
    client = TestClient(app)
    t_now = _vn_ms(2026, 11, 15, 12, 0)
    t_active = _vn_ms(2026, 11, 15, 10, 0)
    t_silent = _vn_ms(2026, 11, 11, 10, 0)

    payload = {
        "now_ms": t_now,
        "conversations": [
            {
                "conversation_id": "c1",
                "viewer_id": "u1",
                "messages": [{"sender_id": "u1", "timestamp_ms": t_active, "text": "Vừa ăn trưa xong nè"}],
            },
            {
                "conversation_id": "c2",
                "viewer_id": "u1",
                "messages": [{"sender_id": "u2", "timestamp_ms": t_silent, "text": "Cuối tuần này gặp nhé"}],
            },
        ],
    }

    res = client.post("/silent-checker/scan", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["scanned_count"] == 2
    assert data["silent_count"] == 1
    assert len(data["results"]) == 2

    # Phần tử đầu tiên là c2 vì cấp thiết hơn
    first = data["results"][0]
    assert first["conversation_id"] == "c2"
    assert first["tier"] == "SILENT_48H"
    assert first["needs_nudge"] is True
    assert first["recommended_action"] is not None


def test_api_silent_checker_scan_min_tier_filter():
    client = TestClient(app)
    t_now = _vn_ms(2026, 11, 15, 12, 0)
    t_active = _vn_ms(2026, 11, 15, 10, 0)
    t_48h = _vn_ms(2026, 11, 11, 10, 0)

    payload = {
        "now_ms": t_now,
        "min_tier": "SILENT_24H",
        "conversations": [
            {
                "conversation_id": "c1",
                "viewer_id": "u1",
                "messages": [{"sender_id": "u1", "timestamp_ms": t_active, "text": "Vừa ăn trưa xong nè"}],
            },
            {
                "conversation_id": "c2",
                "viewer_id": "u1",
                "messages": [{"sender_id": "u2", "timestamp_ms": t_48h, "text": "Cuối tuần này gặp nhé"}],
            },
        ],
    }

    res = client.post("/silent-checker/scan", json=payload)
    assert res.status_code == 200
    data = res.json()
    # Chỉ trả về c2 vì c1 là ACTIVE < SILENT_24H
    assert len(data["results"]) == 1
    assert data["results"][0]["conversation_id"] == "c2"


# =====================================================================
# 6. Test SilentCheckerJob cron / background alert
# =====================================================================

def test_silent_checker_job_run_once_callback():
    t_now = _vn_ms(2026, 11, 15, 12, 0)
    t_active = _vn_ms(2026, 11, 15, 10, 0)
    t_silent = _vn_ms(2026, 11, 11, 10, 0)

    convs = [
        ConversationScanItem(
            conversation_id="conv_ok",
            viewer_id="me",
            messages=[Message(sender_id="me", timestamp_ms=t_active, text="Chào bạn")],
        ),
        ConversationScanItem(
            conversation_id="conv_alert",
            viewer_id="me",
            messages=[Message(sender_id="partner", timestamp_ms=t_silent, text="Cậu đi đâu rồi?")],
        ),
    ]

    alerted_convs = []

    def on_alert(alerts):
        alerted_convs.extend(alerts)

    job = SilentCheckerJob(
        interval_s=60.0,
        data_fetcher=lambda: convs,
        on_alert=on_alert,
    )

    results = job.run_once(now_ms=t_now)
    assert len(results) == 2
    # Chỉ conv_alert cần nudge và được gửi tới callback
    assert len(alerted_convs) == 1
    assert alerted_convs[0].conversation_id == "conv_alert"
    assert alerted_convs[0].tier == SilenceTier.SILENT_48H
