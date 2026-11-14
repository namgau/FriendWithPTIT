"""C6: Silent Conversation Checker Background Job (docs/06_MODULE_3_HEALTH_MONITOR.md §7).

Nhiệm vụ:
- Phân loại mức độ im lặng (SilenceTier): ACTIVE (<12h), SILENT_12H (12-24h), SILENT_24H (24-48h), SILENT_48H (>=48h).
- Trừ thời gian ban đêm 00:00 - 07:00 UTC+7 (adjust_sleep_latency) tránh báo nhầm khi người dùng đi ngủ (lỗi E3).
- Dự đoán nguy cơ rời đi (health_score) và đề xuất gợi ý mở lời (recommended_action).
- Quét và xếp hạng mức độ cấp thiết (scan_silent_conversations).
- Background job cho cron / FastAPI lifespan.
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
import time
from enum import Enum
from typing import Any, Callable

from pydantic import BaseModel, Field

from serving.predictors import _words, is_question, is_short_reply, level_from_p
from serving.schemas import HealthLevel, Message

logger = logging.getLogger(__name__)

# Hằng số thời gian UTC+7
UTC7_OFFSET_MS = 7 * 3_600 * 1000       # 25,200,000 ms
DAY_MS = 24 * 3_600 * 1000              # 86,400,000 ms
SLEEP_DURATION_MS = 7 * 3_600 * 1000   # 00:00 đến 07:00 = 7 giờ = 25,200,000 ms


class SilenceTier(str, Enum):
    """Bốn mức độ im lặng theo khoảng thời gian không tương tác (đã trừ giờ ngủ)."""
    ACTIVE = "ACTIVE"          # < 12h
    SILENT_12H = "SILENT_12H"  # 12 - 24h
    SILENT_24H = "SILENT_24H"  # 24 - 48h
    SILENT_48H = "SILENT_48H"  # >= 48h

    def __eq__(self, other: Any) -> bool:
        if isinstance(other, str):
            return self.value.lower() == other.lower()
        return super().__eq__(other)

    def __hash__(self) -> int:
        return hash(self.value.upper())

    @classmethod
    def _missing_(cls, value: object) -> Any:
        if isinstance(value, str):
            for member in cls:
                if member.value.lower() == value.lower():
                    return member
        return super()._missing_(value)


def adjust_sleep_latency(last_ms: int, now_ms: int) -> int:
    """Trừ thời gian ban đêm 00:00 - 07:00 UTC+7 khỏi khoảng cách tin nhắn (ms).

    Theo phân tích lỗi E3 trong docs/06: nhiều cảnh báo giả xuất hiện khi người dùng
    ngủ qua đêm. Hàm này tính chính xác số mili giây giao giữa [last_ms, now_ms]
    và các khung giờ 00:00 - 07:00 UTC+7 rồi trừ đi, trả về độ trễ thức thực tế.
    """
    if now_ms <= last_ms:
        return 0

    t1_local = int(last_ms) + UTC7_OFFSET_MS
    t2_local = int(now_ms) + UTC7_OFFSET_MS

    d1 = t1_local // DAY_MS
    d2 = t2_local // DAY_MS

    total_sleep_ms = 0
    for d in range(d1, d2 + 1):
        s_start = d * DAY_MS
        s_end = s_start + SLEEP_DURATION_MS
        overlap = max(0, min(t2_local, s_end) - max(t1_local, s_start))
        total_sleep_ms += overlap

    raw_latency_ms = now_ms - last_ms
    adjusted = max(0, raw_latency_ms - total_sleep_ms)
    return int(adjusted)


def classify_silence_tier(latency_ms: float | None = None, *, hours: float | None = None) -> SilenceTier:
    """Phân loại mức silence tier từ thời gian (ms hoặc giờ)."""
    if hours is not None:
        h = float(hours)
    elif latency_ms is not None:
        h = float(latency_ms) / 3_600_000.0
    else:
        raise ValueError("Cần cung cấp latency_ms hoặc hours")

    if h < 12.0:
        return SilenceTier.ACTIVE
    if h < 24.0:
        return SilenceTier.SILENT_12H
    if h < 48.0:
        return SilenceTier.SILENT_24H
    return SilenceTier.SILENT_48H


def generate_recommended_action(
    tier: SilenceTier,
    last_sender_id: str | None,
    viewer_id: str,
    factors: list[str],
) -> str | None:
    """Đề xuất gợi ý mở lời dựa trên mức độ im lặng và ngữ cảnh người gửi."""
    is_viewer_last = (last_sender_id == viewer_id) if (last_sender_id and viewer_id) else False

    if tier == SilenceTier.ACTIVE:
        if "short_replies" in factors:
            return "Cuộc trò chuyện đang ngắn dần, thử một câu hỏi mở về sở thích chung."
        if "shrinking_messages" in factors:
            return "Thử kể một chuyện nhỏ hôm nay của bạn để mở chủ đề mới."
        if "one_sided_questions" in factors:
            return "Hãy hỏi lại đối phương để cuộc trò chuyện hai chiều hơn."
        return None

    if tier == SilenceTier.SILENT_12H:
        if not is_viewer_last:
            return "Đối phương đang chờ bạn phản hồi (đã hơn 12 giờ), hãy tiếp nối câu chuyện nhé."
        return "Đã hơn 12 giờ chưa có tin mới, hãy gửi một câu hỏi nhẹ nhàng hoặc chia sẻ điều thú vị hôm nay."

    if tier == SilenceTier.SILENT_24H:
        if not is_viewer_last:
            return "Đối phương đã nhắn từ hơn 24 giờ trước. Hãy phản hồi để cuộc trò chuyện không bị ngắt quãng."
        return "Hội thoại đã im lặng hơn 24 giờ. Thử chia sẻ một hình ảnh vui hoặc hỏi về sở thích chung để phá băng."

    if tier == SilenceTier.SILENT_48H:
        if not is_viewer_last:
            return "Đã hơn 48 giờ chưa trả lời đối phương. Hãy chủ động nhắn lại và đề xuất một buổi hẹn trực tiếp (cà phê/học nhóm)."
        return "Hội thoại có nguy cơ tắt (im lặng trên 48 giờ). Hãy gửi lời chào nhẹ nhàng hoặc hẹn gặp mặt trực tiếp để hâm nóng kết nối."

    return None


def compute_urgency_score(
    tier: SilenceTier,
    adjusted_silence_h: float,
    p_disengage: float,
    last_sender_id: str | None,
    viewer_id: str,
) -> float:
    """Tính điểm cấp thiết để xếp hạng ưu tiên can thiệp trong background scan."""
    tier_base = {
        SilenceTier.ACTIVE: 0.0,
        SilenceTier.SILENT_12H: 2.0,
        SilenceTier.SILENT_24H: 4.0,
        SilenceTier.SILENT_48H: 6.0,
    }.get(tier, 0.0)

    # Ưu tiên nếu đối phương là người gửi tin cuối (người xem đang nợ tin trả lời)
    awaiting_reply_bonus = (
        1.0 if (last_sender_id and viewer_id and last_sender_id != viewer_id and tier != SilenceTier.ACTIVE) else 0.0
    )

    silence_norm = min(adjusted_silence_h / 48.0, 2.0)
    score = tier_base + awaiting_reply_bonus + (p_disengage * 2.0) + silence_norm
    return round(score, 3)


class SilentConversationResult(BaseModel):
    """Kết quả phân tích độ im lặng của 1 cuộc hội thoại."""
    conversation_id: str
    viewer_id: str = "viewer"
    tier: SilenceTier
    raw_silence_h: float = Field(..., description="Thời gian im lặng thô (giờ)")
    adjusted_silence_h: float = Field(..., description="Thời gian im lặng sau khi trừ giờ ngủ (giờ)")
    health_score: float = Field(..., ge=0.0, le=1.0, description="Xác suất rời đi p_disengage")
    p_disengage: float = Field(..., ge=0.0, le=1.0, description="Alias cho health_score")
    health_level: HealthLevel = Field(..., description="healthy, shallow, hoặc at_risk")
    urgency_score: float = Field(..., description="Điểm cấp thiết để sắp xếp ưu tiên")
    recommended_action: str | None = Field(None, description="Gợi ý hành động mở lời riêng tư")
    factors: list[str] = Field(default_factory=list, description="Yếu tố làm suy giảm nhiệt độ hội thoại")
    last_sender_id: str | None = Field(None, description="Người gửi tin nhắn cuối cùng")
    last_message_ms: int | None = Field(None, description="Thời điểm tin nhắn cuối cùng (ms)")
    needs_nudge: bool = Field(False, description="True nếu hội thoại im lặng cần can thiệp")


class ConversationScanItem(BaseModel):
    """Hội thoại đầu vào cho lượt quét."""
    conversation_id: str
    viewer_id: str = "viewer"
    messages: list[Message] = Field(default_factory=list)


class SilentScanRequest(BaseModel):
    """Yêu cầu quét hàng loạt hội thoại im lặng."""
    conversations: list[ConversationScanItem]
    now_ms: int | None = None
    min_tier: SilenceTier | None = None


class SilentScanResponse(BaseModel):
    """Kết quả quét hàng loạt hội thoại im lặng."""
    scanned_count: int
    silent_count: int
    results: list[SilentConversationResult]


def _normalize_messages(messages: list[Message | dict[str, Any]]) -> list[Message]:
    normalized: list[Message] = []
    for m in messages:
        if isinstance(m, Message):
            normalized.append(m)
        elif isinstance(m, dict):
            normalized.append(
                Message(
                    sender_id=str(m.get("sender_id", "")),
                    timestamp_ms=int(m.get("timestamp_ms", 0)),
                    text=m.get("text"),
                )
            )
    return normalized


def check_conversation(
    conv_id: str,
    messages: list[Message | dict[str, Any]],
    viewer_id: str = "viewer",
    now_ms: int | None = None,
) -> SilentConversationResult:
    """Kiểm tra một hội thoại: tính độ im lặng trừ giờ ngủ, điểm sức khỏe và đề xuất mở lời."""
    if now_ms is None:
        now_ms = int(time.time() * 1000)

    norm_msgs = _normalize_messages(messages)
    past = sorted(
        (m for m in norm_msgs if m.timestamp_ms <= now_ms),
        key=lambda m: m.timestamp_ms,
    )

    if not past:
        tier = SilenceTier.ACTIVE
        return SilentConversationResult(
            conversation_id=conv_id,
            viewer_id=viewer_id,
            tier=tier,
            raw_silence_h=0.0,
            adjusted_silence_h=0.0,
            health_score=0.5,
            p_disengage=0.5,
            health_level=HealthLevel.healthy,
            urgency_score=0.0,
            recommended_action="Chưa có tin nhắn trong cuộc trò chuyện. Hãy gửi lời chào làm quen nhé!",
            factors=["no_messages"],
            last_sender_id=None,
            last_message_ms=None,
            needs_nudge=False,
        )

    last_msg = past[-1]
    last_ms = last_msg.timestamp_ms
    last_sender_id = last_msg.sender_id
    raw_silence_ms = max(0, now_ms - last_ms)
    adjusted_silence_ms = adjust_sleep_latency(last_ms, now_ms)

    raw_silence_h = round(raw_silence_ms / 3_600_000.0, 3)
    adjusted_silence_h = round(adjusted_silence_ms / 3_600_000.0, 3)

    tier = classify_silence_tier(adjusted_silence_ms)

    # Tính health_score dùng độ trễ đã trừ giờ ngủ
    factors: list[str] = []
    p = 0.6 * min(adjusted_silence_h / 48.0, 1.0)
    if adjusted_silence_h >= 24.0:
        factors.append("long_silence")

    last_window = past[-6:]
    if sum(is_short_reply(m.text) for m in last_window) >= 3:
        p += 0.2
        factors.append("short_replies")

    lengths = [len(_words(m.text)) for m in last_window]
    if len(lengths) >= 4 and lengths[-1] < lengths[0] and lengths[-2] < lengths[1]:
        p += 0.1
        factors.append("shrinking_messages")

    askers = {m.sender_id for m in last_window if is_question(m.text)}
    if len(askers) == 1 and len({m.sender_id for m in last_window}) == 2:
        p += 0.1
        factors.append("one_sided_questions")

    p = min(round(p, 4), 1.0)
    level = level_from_p(p)

    recommended_action = generate_recommended_action(
        tier=tier,
        last_sender_id=last_sender_id,
        viewer_id=viewer_id,
        factors=factors,
    )

    urgency_score = compute_urgency_score(
        tier=tier,
        adjusted_silence_h=adjusted_silence_h,
        p_disengage=p,
        last_sender_id=last_sender_id,
        viewer_id=viewer_id,
    )

    needs_nudge = (tier != SilenceTier.ACTIVE) or (level == HealthLevel.at_risk)

    return SilentConversationResult(
        conversation_id=conv_id,
        viewer_id=viewer_id,
        tier=tier,
        raw_silence_h=raw_silence_h,
        adjusted_silence_h=adjusted_silence_h,
        health_score=p,
        p_disengage=p,
        health_level=level,
        urgency_score=urgency_score,
        recommended_action=recommended_action,
        factors=factors,
        last_sender_id=last_sender_id,
        last_message_ms=last_ms,
        needs_nudge=needs_nudge,
    )


def scan_silent_conversations(
    conversations: list[ConversationScanItem | dict[str, Any]],
    now_ms: int | None = None,
) -> list[SilentConversationResult]:
    """Quét danh sách các hội thoại và xếp hạng mức độ cấp thiết giảm dần."""
    if now_ms is None:
        now_ms = int(time.time() * 1000)

    results: list[SilentConversationResult] = []
    for item in conversations:
        if isinstance(item, dict):
            conv_id = item.get("conversation_id", "")
            viewer_id = item.get("viewer_id", "viewer")
            raw_msgs = item.get("messages", [])
        else:
            conv_id = item.conversation_id
            viewer_id = item.viewer_id
            raw_msgs = item.messages

        res = check_conversation(
            conv_id=conv_id,
            messages=raw_msgs,
            viewer_id=viewer_id,
            now_ms=now_ms,
        )
        results.append(res)

    results.sort(key=lambda r: r.urgency_score, reverse=True)
    return results


class SilentCheckerJob:
    """Background job định kỳ kiểm tra các hội thoại im lặng (cho FastAPI lifespan hoặc cron script)."""

    def __init__(
        self,
        interval_s: float = 3600.0,
        data_fetcher: Callable[[], list[ConversationScanItem]] | None = None,
        on_alert: Callable[[list[SilentConversationResult]], None] | None = None,
    ):
        self.interval_s = interval_s
        self.data_fetcher = data_fetcher
        self.on_alert = on_alert
        self._running = False

    def run_once(
        self,
        conversations: list[ConversationScanItem | dict[str, Any]] | None = None,
        now_ms: int | None = None,
    ) -> list[SilentConversationResult]:
        """Chạy một lượt quét (thích hợp cho script cron)."""
        if conversations is None:
            if self.data_fetcher is not None:
                conversations = self.data_fetcher()
            else:
                conversations = []
        results = scan_silent_conversations(conversations, now_ms=now_ms)
        alerts = [r for r in results if r.needs_nudge]
        if self.on_alert and alerts:
            self.on_alert(alerts)
        return results

    async def start(self):
        """Khởi động background loop."""
        self._running = True
        logger.info("SilentCheckerJob started (interval: %.1fs)", self.interval_s)
        while self._running:
            try:
                self.run_once()
            except Exception as exc:
                logger.error("Error in SilentCheckerJob execution: %s", exc)
            try:
                await asyncio.sleep(self.interval_s)
            except asyncio.CancelledError:
                break
        logger.info("SilentCheckerJob stopped")

    def stop(self):
        """Dừng background loop."""
        self._running = False


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="FwP Silent Conversation Checker Job")
    parser.add_argument("--once", action="store_true", help="Chạy quét một lần rồi thoát")
    parser.add_argument("--interval", type=float, default=3600.0, help="Chu kỳ quét (giây)")
    args = parser.parse_args()

    job = SilentCheckerJob(interval_s=args.interval)
    if args.once:
        print("Running one-shot scan...")
        results = job.run_once()
        print(f"Scanned {len(results)} conversations.")
    else:
        print(f"Starting loop with interval {args.interval}s...")
        try:
            asyncio.run(job.start())
        except KeyboardInterrupt:
            job.stop()
            print("Job stopped.")
