package vn.ptit.fwp.data

import vn.ptit.fwp.data.model.CandidateCard
import vn.ptit.fwp.data.model.MatchState
import vn.ptit.fwp.data.model.MatchSummary
import vn.ptit.fwp.data.model.ME_ID
import vn.ptit.fwp.data.model.Message
import vn.ptit.fwp.data.model.PublicProfile
import vn.ptit.fwp.data.model.SafetyRequest
import vn.ptit.fwp.data.model.SafetyScore
import vn.ptit.fwp.data.model.SendRequest
import vn.ptit.fwp.data.model.SwipeRequest
import vn.ptit.fwp.data.model.SwipeResponse
import vn.ptit.fwp.data.remote.FwpApi

/**
 * Mọi màn hình chỉ nói chuyện với interface này.
 * - [RemoteFwpRepository]: gọi server FastAPI (luật hiện tại, mô hình thật về sau).
 * - [FakeFwpRepository]: dữ liệu giả trong máy, chạy được khi không có server.
 */
interface FwpRepository {
    suspend fun me(): PublicProfile
    suspend fun candidates(): List<CandidateCard>
    suspend fun swipe(targetId: String, like: Boolean): SwipeResponse
    suspend fun matches(): List<MatchSummary>
    suspend fun messages(matchId: String): List<Message>
    suspend fun send(matchId: String, text: String): List<Message>
    suspend fun state(matchId: String): MatchState
    suspend fun safety(matchId: String, context: List<Message>, draft: String): SafetyScore
    suspend fun reset()
}

class RemoteFwpRepository(private val api: FwpApi) : FwpRepository {
    override suspend fun me() = api.me().publicProfile
    override suspend fun candidates() = api.candidates()
    override suspend fun swipe(targetId: String, like: Boolean) = api.swipe(SwipeRequest(targetId, like))
    override suspend fun matches() = api.matches()
    override suspend fun messages(matchId: String) = api.messages(matchId)
    override suspend fun send(matchId: String, text: String) = api.send(matchId, SendRequest(text))
    override suspend fun state(matchId: String) = api.state(matchId)
    override suspend fun safety(matchId: String, context: List<Message>, draft: String) =
        api.safety(SafetyRequest(matchId, context.takeLast(10), Message(ME_ID, System.currentTimeMillis(), draft)))

    override suspend fun reset() {
        api.reset()
    }
}
