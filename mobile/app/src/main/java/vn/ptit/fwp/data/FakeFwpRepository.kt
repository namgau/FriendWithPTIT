package vn.ptit.fwp.data

import kotlinx.coroutines.delay
import vn.ptit.fwp.data.model.BlurState
import vn.ptit.fwp.data.model.CandidateCard
import vn.ptit.fwp.data.model.HealthScore
import vn.ptit.fwp.data.model.MatchState
import vn.ptit.fwp.data.model.MatchSummary
import vn.ptit.fwp.data.model.ME_ID
import vn.ptit.fwp.data.model.Message
import vn.ptit.fwp.data.model.PublicProfile
import vn.ptit.fwp.data.model.SafetyScore
import vn.ptit.fwp.data.model.SwipeResponse

/**
 * Dữ liệu giả trong máy để làm giao diện khi chưa bật server.
 * Logic ở đây chỉ mô phỏng thô; logic thật nằm ở server (serving/).
 */
class FakeFwpRepository : FwpRepository {

    private val me = PublicProfile(
        ME_ID, "Bạn", "Học viện Công nghệ Bưu chính Viễn thông", "Công nghệ thông tin",
        2, "student", 21, listOf("âm nhạc", "phim", "du lịch", "lập trình"), "Thích code và nghe nhạc indie.",
    )

    private val seed = listOf(
        card("f1", "Mây", "Học viện Công nghệ Bưu chính Viễn thông", "An toàn thông tin", 2, "student", 20,
            listOf("âm nhạc", "phim", "mèo"), "Cuối tuần hay đi xem phim một mình.", 0.4),
        card("f2", "Gió", "Đại học Bách khoa Hà Nội", "Điện tử viễn thông", 3, "student", 21,
            listOf("lập trình", "du lịch", "leo núi"), "Đang tập leo núi trong nhà.", 6.2),
        card("f3", "Sương", "Đại học Kinh tế Quốc dân", "Marketing", 1, "alumni", 24,
            listOf("cà phê", "nhiếp ảnh"), "Làm marketing, nghiện cà phê.", 6.0),
    )

    private val seen = mutableSetOf<String>()
    private val matches = linkedMapOf<String, Pair<PublicProfile, MutableList<Message>>>()
    private val botReplies = listOf("Chào bạn, rất vui được match nha!", "Mình cũng thích vậy đó, còn bạn?", "Haha thật hả?", "ừ", "ok")

    override suspend fun me() = me

    override suspend fun candidates(): List<CandidateCard> {
        delay(200)
        val matchedIds = matches.values.map { it.first.userId }.toSet()
        return seed.filter { it.userId !in seen && it.userId !in matchedIds }.sortedByDescending { it.score }
    }

    override suspend fun swipe(targetId: String, like: Boolean): SwipeResponse {
        seen += targetId
        val c = seed.first { it.userId == targetId }
        if (!like || c.commonTags.isEmpty()) return SwipeResponse(false, null)
        val id = "fm${matches.size + 1}"
        matches[id] = c.toPublic() to mutableListOf()
        return SwipeResponse(true, id)
    }

    override suspend fun matches() = matches.map { (id, v) ->
        MatchSummary(id, v.first, v.second.lastOrNull(), blur(v.second.size))
    }

    override suspend fun messages(matchId: String) = matches.getValue(matchId).second.toList()

    override suspend fun send(matchId: String, text: String): List<Message> {
        val (other, msgs) = matches.getValue(matchId)
        val now = System.currentTimeMillis()
        msgs += Message(ME_ID, now, text)
        val nBot = msgs.count { it.senderId != ME_ID }
        msgs += Message(other.userId, now + 1000, botReplies[minOf(nBot, botReplies.lastIndex)])
        return msgs.toList()
    }

    override suspend fun state(matchId: String): MatchState {
        val msgs = matches.getValue(matchId).second
        val shortReplies = msgs.takeLast(6).count { (it.text?.split(" ")?.size ?: 0) <= 1 }
        val health = if (shortReplies >= 2) {
            HealthScore(0.5, "shallow", listOf("short_replies"),
                listOf("Cuộc trò chuyện đang ngắn dần, thử một câu hỏi mở về sở thích chung."), "fake")
        } else {
            HealthScore(0.1, "healthy", emptyList(), emptyList(), "fake")
        }
        return MatchState(blur(msgs.size), health, minOf(1.0, 0.2 + 0.02 * msgs.size))
    }

    override suspend fun safety(matchId: String, context: List<Message>, draft: String): SafetyScore {
        val toxic = listOf("ngu", "đần", "cút", "stupid", "idiot").any { w ->
            draft.lowercase().split(Regex("\\W+")).contains(w)
        }
        return SafetyScore(if (toxic) 0.9 else 0.0, if (toxic) 0.5 else 0.0, toxic, "fake")
    }

    override suspend fun reset() {
        seen.clear()
        matches.clear()
    }

    private fun blur(nMessages: Int): BlurState {
        val level = maxOf(0.05, 1.0 - 0.6 * minOf(nMessages / 60.0, 1.0))
        return BlurState(level, level * 25, false, "fake")
    }

    private fun card(
        id: String, nick: String, school: String, faculty: String, gpa: Int, grad: String, age: Int,
        tags: List<String>, bio: String, dist: Double,
    ): CandidateCard {
        val common = tags.filter { it in me.interestTags }
        return CandidateCard(id, nick, school, faculty, gpa, grad, age, tags, bio, dist, common, common.size.toDouble())
    }

    private fun CandidateCard.toPublic() =
        PublicProfile(userId, nickname, schoolName, facultyName, gpaTier, graduationStatus, age, interestTags, bio)
}
