package vn.ptit.fwp.core.ai

import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.CompatibilityReport
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.Profile
import kotlin.math.roundToInt

object CompatibilityEngine {

    private val positiveWords = setOf(
        "vui", "thích", "hay", "đẹp", "cười", "haha", "hehe", "cute", "ngon",
        "thú vị", "tuyệt", "ổn", "chill", "yêu", "giỏi", "tốt", "hạnh phúc"
    )
    private val negativeWords = setOf(
        "buồn", "chán", "mệt", "khó", "ghét", "bực", "căng", "lo", "không thích", "chẳng"
    )

    fun analyze(
        me: Profile?,
        other: Profile,
        match: FwpMatch,
        messages: List<ChatMessage>
    ): CompatibilityReport {
        val chat = messages.filter { it.type != MessageType.SYSTEM }
        val count = chat.size
        val shared = RankingEngine.sharedInterests(me, other)
        val union = (me?.interests.orEmpty() + other.interests).toSet()
        val interestOverlap = if (union.isEmpty()) 0f else shared.size.toFloat() / union.size
        val text = chat.joinToString(" ") { it.text.lowercase() }
        val sharedMentions = shared.count { tag -> text.contains(tag.lowercase()) }
        val positive = positiveWords.count { text.contains(it) }
        val negative = negativeWords.count { text.contains(it) }
        val questions = chat.count { it.text.contains("?") }
        val myCount = chat.count { it.senderId == "me" }
        val theirCount = count - myCount
        val balance = if (count == 0) 0f else 1f - kotlin.math.abs(myCount - theirCount).toFloat() / count
        val avgLength = if (count == 0) 0 else chat.sumOf { it.text.length } / count
        val engagement = (
            balance * 32f +
                (questions.toFloat() / count.coerceAtLeast(1) * 26f).coerceAtMost(26f) +
                (avgLength / 60f * 20f).coerceAtMost(20f) +
                (count / 24f * 22f).coerceAtMost(22f)
            ).coerceIn(0f, 100f)
        val interestAxis = ((interestOverlap * 58f) + (sharedMentions * 8f) + 32f).coerceIn(15f, 99f)
        val emotionAxis = (52f + positive * 7f - negative * 9f + engagement * 0.2f).coerceIn(15f, 99f)
        val personalityAxis = (56f + interestOverlap * 26f + engagement * 0.18f).coerceIn(15f, 99f)
        val raw = interestAxis * 0.34f + emotionAxis * 0.3f + personalityAxis * 0.26f + engagement * 0.1f
        val dataPenalty = when {
            count < 3 -> 24f
            count < 6 -> 14f
            count < 9 -> 5f
            else -> 0f
        }
        val score = (raw - dataPenalty).coerceIn(8f, 99f).roundToInt()
        val bothOptIn = match.revealOptInMe && match.revealOptInThem
        val revealEligible = bothOptIn && count >= MIN_MESSAGES_TO_REVEAL && score >= REVEAL_SCORE
        val blur = when {
            revealEligible -> 0f
            bothOptIn -> (1f - (score - 20) / 82f).coerceIn(0.12f, 0.92f)
            else -> (1f - score / 135f).coerceIn(0.55f, 0.96f)
        }
        return CompatibilityReport(
            score = score,
            blurLevel = blur,
            personality = personalityAxis.roundToInt(),
            emotion = emotionAxis.roundToInt(),
            interest = interestAxis.roundToInt(),
            messageCount = count,
            revealEligible = revealEligible,
            revealed = revealEligible,
            source = "Quy tắc FwP (fallback)"
        )
    }

    fun previewScore(me: Profile?, other: Profile): Int {
        val shared = RankingEngine.sharedInterests(me, other)
        val union = (me?.interests.orEmpty() + other.interests).toSet()
        val overlap = if (union.isEmpty()) 0f else shared.size.toFloat() / union.size
        val tierDiff = kotlin.math.abs((me?.gpaTier?.ordinal ?: 1) - other.gpaTier.ordinal)
        val gpa = when (tierDiff) {
            0 -> 1f
            1 -> 0.72f
            else -> 0.36f
        }
        val sameCampus = if (me?.campus == other.campus) 1f else 0.5f
        return ((overlap * 46f) + gpa * 30f + sameCampus * 16f + 8f).coerceIn(18f, 96f).roundToInt()
    }

    const val MIN_MESSAGES_TO_REVEAL = 8
    const val REVEAL_SCORE = 72
}
