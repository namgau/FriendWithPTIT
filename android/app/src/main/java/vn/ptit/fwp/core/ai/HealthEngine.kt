package vn.ptit.fwp.core.ai

import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.HealthLevel
import vn.ptit.fwp.core.model.HealthReport
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.Profile
import kotlin.math.roundToInt

object HealthEngine {

    fun analyze(
        messages: List<ChatMessage>,
        me: Profile?,
        other: Profile,
        now: Long = System.currentTimeMillis()
    ): HealthReport {
        val chat = messages.filter { it.type != MessageType.SYSTEM }
        if (chat.size < MIN_MESSAGES) {
            return HealthReport(
                level = HealthLevel.FADING,
                score = 38,
                reasons = listOf("Chưa đủ tin nhắn để phân tích sâu", "Hãy thử một câu hỏi mở"),
                suggestions = suggestions(me, other, chat, lowActivity = true, longGap = false),
                messageCount = chat.size,
                source = "Quy tắc FwP (fallback)"
            )
        }
        val window = chat.takeLast(10)
        val myCount = window.count { it.senderId == "me" }
        val otherCount = window.size - myCount
        val balance = 1f - kotlin.math.abs(myCount - otherCount).toFloat() / window.size
        val avgLength = window.sumOf { it.text.length }.toFloat() / window.size
        val questionRatio = window.count { it.text.contains("?") }.toFloat() / window.size
        val shortRatio = window.count { it.text.trim().length <= 4 }.toFloat() / window.size
        val last = chat.last()
        val gapHours = ((now - last.createdAt) / 3_600_000f).coerceAtLeast(0f)
        val negative = window.count {
            val t = it.text.lowercase()
            listOf("chán", "mệt", "buồn", "ừ", "ừm", "ok", "không có gì").any { w -> t.trim() == w }
        }.toFloat() / window.size
        var risk = 10f
        if (gapHours >= 48f) risk += 34f else if (gapHours >= 24f) risk += 22f else if (gapHours >= 8f) risk += 10f
        if (shortRatio > 0.45f) risk += 20f
        if (questionRatio < 0.12f) risk += 14f
        if (balance < 0.35f) risk += 16f
        if (avgLength < 13f) risk += 12f
        if (negative > 0.4f) risk += 12f
        if (window.size >= 12 && questionRatio > 0.2f && balance > 0.55f) risk -= 10f
        val score = risk.coerceIn(0f, 100f).roundToInt()
        val level = when {
            score >= 64 -> HealthLevel.AT_RISK
            score >= 36 -> HealthLevel.FADING
            else -> HealthLevel.HEALTHY
        }
        val reasons = buildList {
            if (gapHours >= 24f) add("Tin nhắn cuối đã ${formatGap(gapHours)}")
            if (shortRatio > 0.45f) add("Nhiều tin nhắn quá ngắn, dễ tạo cảm giác hời hợt")
            if (questionRatio < 0.12f) add("Ít câu hỏi mở, cuộc trò chuyện chưa có điểm bật")
            if (balance < 0.35f) add("Một bên đang nhắn nhiều hơn hẳn")
            if (avgLength < 13f) add("Độ dài tin nhắn đang giảm")
            if (negative > 0.4f) add("Có nhiều câu trả lời cụt hoặc thiếu năng lượng")
            if (isEmpty()) add("Hai bạn đang giữ nhịp trò chuyện khá tốt")
        }
        return HealthReport(
            level = level,
            score = score,
            reasons = reasons,
            suggestions = suggestions(me, other, window, lowActivity = score >= 36, longGap = gapHours >= 24f),
            messageCount = chat.size,
            source = "Quy tắc FwP (fallback)"
        )
    }

    private fun suggestions(
        me: Profile?,
        other: Profile,
        messages: List<ChatMessage>,
        lowActivity: Boolean,
        longGap: Boolean
    ): List<String> {
        val shared = RankingEngine.sharedInterests(me, other)
        val result = mutableListOf<String>()
        if (longGap) {
            result += "Nhắn nhẹ một câu: \"Dạo này cậu thế nào? Mình vừa thấy một thứ chắc cậu thích.\""
        }
        if (shared.isNotEmpty()) {
            result += "Khai thác sở thích chung: ${shared.take(2).joinToString(", ")}"
        }
        val askedQuestion = messages.lastOrNull()?.text?.contains("?") == true
        if (!askedQuestion) {
            result += "Hỏi một câu mở: \"Điều gì làm cậu thấy hào hứng nhất tuần này?\""
        }
        result += "Chia sẻ một chuyện nhỏ vừa xảy ra trong ngày thay vì chỉ trả lời"
        result += "Gợi ý một hoạt động cụ thể: cà phê, học nhóm hoặc một buổi đi bộ"
        return result.distinct().take(3)
    }

    private fun formatGap(hours: Float): String = when {
        hours >= 48 -> "${(hours / 24).toInt()} ngày"
        hours >= 24 -> "1 ngày"
        else -> "${hours.toInt()} giờ"
    }

    private const val MIN_MESSAGES = 4
}
