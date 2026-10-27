package vn.ptit.fwp.core.ai

import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.HealthLevel
import vn.ptit.fwp.core.model.HealthReport
import vn.ptit.fwp.core.model.Profile
import kotlin.math.absoluteValue

object ConversationAssistant {

    fun openingMoves(me: Profile?, other: Profile): List<String> {
        val shared = RankingEngine.sharedInterests(me, other)
        val moves = mutableListOf<String>()
        if (shared.isNotEmpty()) {
            moves += "Mình thấy cậu cũng thích ${shared.first().lowercase()}. Cậu bắt đầu với nó thế nào?"
        }
        moves += when (other.mode) {
            ConnectionMode.STUDY -> "Cậu đang học môn nào thấy thú vị nhất kỳ này? Mình đang tìm người học cùng."
            ConnectionMode.FRIENDS -> "Nếu cuối tuần này rảnh, cậu thường làm gì để nạp lại năng lượng?"
            ConnectionMode.DATING -> "Một buổi hẹn lý tưởng của cậu sẽ trông như thế nào?"
        }
        val campus = other.campus.substringBefore(" – ")
        moves += "Ở $campus có chỗ cà phê nào cậu thấy ổn không? Mình đang muốn thử chỗ mới."
        if (other.faculty.isNotBlank()) {
            moves += "Nghe nói khoa ${other.faculty} có nhiều chuyện hay. Cậu thấy môn nào đáng học nhất?"
        }
        moves += "Nếu được chọn một chủ đề để nói cả tối nay, cậu sẽ chọn gì?"
        return moves.distinct().take(4)
    }

    fun reply(
        me: Profile?,
        other: Profile,
        messages: List<ChatMessage>,
        health: HealthReport
    ): String {
        val last = messages.lastOrNull { it.isMine }?.text.orEmpty()
        val count = messages.count { !it.isMine }
        val interest = RankingEngine.sharedInterests(me, other).firstOrNull()
        val lower = last.lowercase()
        val seed = (other.id.hashCode() + count * 7).absoluteValue
        if (last.isBlank()) {
            return "Chào cậu, mình là ${other.nickname}. Rất vui được match với cậu!"
        }
        if (lower.contains("chào") || lower.contains("hello") || lower.contains("hi ")) {
            return "Chào cậu! Mình vừa thấy thông báo match xong. Cậu đang làm gì đó?"
        }
        if (lower.contains("?")) {
            val answers = listOf(
                "Câu này hay đấy. Mình nghĩ là ${if (interest != null) "mình sẽ chọn ${interest.lowercase()} vì nó làm mình thấy thoải mái" else "mình thích những buổi nhẹ nhàng, không cần kế hoạch quá nhiều"}.",
                "Mình chưa thử bao giờ, nhưng nghe cậu kể thì có vẻ thú vị. Cậu thử trước chưa?",
                "Mình thích ${if (interest != null) interest.lowercase() else "những cuộc trò chuyện dài"}. Còn cậu thì sao?"
            )
            return answers[seed % answers.size]
        }
        if (other.mode == ConnectionMode.STUDY && (lower.contains("học") || lower.contains("thi") || lower.contains("tài liệu"))) {
            return "Mình có vài tài liệu hay, hôm nào rảnh mình gửi cậu. Cậu đang cần môn nào?"
        }
        if (health.level == HealthLevel.AT_RISK && count > 2) {
            return "Xin lỗi cậu, mấy hôm nay mình hơi bận. Mình vẫn muốn nói chuyện tiếp, cậu kể mình nghe tuần này thế nào đi?"
        }
        val generic = listOf(
            "Nghe cũng hay đó. Mình thì đang thử tìm một quán cà phê mới, cậu có gợi ý không?",
            "Mình vừa xem lại ảnh cũ, tự nhiên thấy muốn đi đâu đó một chuyến. Cậu thích biển hay núi hơn?",
            "Cậu kể thêm đi, mình đang nghe đây. Hôm nay của cậu có gì vui không?",
            "Mình thích cách cậu kể chuyện. Nếu rảnh cuối tuần, cậu có muốn đi cà phê không?",
            if (interest != null) "À, mình cũng mê ${interest.lowercase()} lắm. Cậu mới bắt đầu từ khi nào?" else "Cậu có sở thích nào mà ít người biết không?",
            "Mình vừa làm xong một việc nhỏ mà vui cả buổi. Cậu có chuyện gì tương tự không?"
        )
        return generic[seed % generic.size]
    }
}
