package vn.ptit.fwp.feature.chat

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import vn.ptit.fwp.core.ai.*
import vn.ptit.fwp.core.data.AppSettings
import vn.ptit.fwp.core.data.FwpChatRepository
import vn.ptit.fwp.core.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSafetyTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------
    // 1. Kiểm tra SafetyEngine offline
    // -------------------------------------------------------------
    @Test
    fun testToxicTermsOfflineDetection() {
        // Từ đơn độc hại
        assertTrue(SafetyEngine.isToxicOffline("ngu"))
        assertTrue(SafetyEngine.isToxicOffline("đần"))
        assertTrue(SafetyEngine.isToxicOffline("đm"))
        assertTrue(SafetyEngine.isToxicOffline("dm"))
        assertTrue(SafetyEngine.isToxicOffline("vcl"))
        assertTrue(SafetyEngine.isToxicOffline("vl"))
        assertTrue(SafetyEngine.isToxicOffline("cút"))
        assertTrue(SafetyEngine.isToxicOffline("stupid"))
        assertTrue(SafetyEngine.isToxicOffline("idiot"))
        assertTrue(SafetyEngine.isToxicOffline("fuck"))
        assertTrue(SafetyEngine.isToxicOffline("bitch"))

        // Cụm từ độc hại / xúc phạm
        assertTrue(SafetyEngine.isToxicOffline("óc chó"))
        assertTrue(SafetyEngine.isToxicOffline("câm mồm"))
        assertTrue(SafetyEngine.isToxicOffline("câm đi"))
        assertTrue(SafetyEngine.isToxicOffline("mất dạy"))
        assertTrue(SafetyEngine.isToxicOffline("khốn nạn"))
        assertTrue(SafetyEngine.isToxicOffline("đồ chó"))
        assertTrue(SafetyEngine.isToxicOffline("con điên"))
        assertTrue(SafetyEngine.isToxicOffline("thằng điên"))
        assertTrue(SafetyEngine.isToxicOffline("shut up"))

        // Trong câu có dấu câu, chữ hoa
        assertTrue(SafetyEngine.isToxicOffline("  Mày NGU thế?! "))
        assertTrue(SafetyEngine.isToxicOffline("ĐỒ ÓC CHÓ!!"))
        assertTrue(SafetyEngine.isToxicOffline("Cút đi cho khuất mắt!"))
        assertTrue(SafetyEngine.isToxicOffline("Ăn nói mất dạy thật sự."))
    }

    @Test
    fun testSafeMessagesOffline() {
        assertFalse(SafetyEngine.isToxicOffline("Chào cậu nhé!"))
        assertFalse(SafetyEngine.isToxicOffline("Hôm nay đi học có gì vui không?"))
        assertFalse(SafetyEngine.isToxicOffline("Chiều nay 2h gặp ở thư viện nhé"))
        assertFalse(SafetyEngine.isToxicOffline("Cảm ơn bạn rất nhiều!"))
        assertFalse(SafetyEngine.isToxicOffline("Mình cùng học môn Nhập môn AI nhé"))
        assertFalse(SafetyEngine.isToxicOffline(""))
        assertFalse(SafetyEngine.isToxicOffline("   "))
    }

    // -------------------------------------------------------------
    // 2. Kiểm tra Retrofit DTO & RemoteAiClient
    // -------------------------------------------------------------
    @Test
    fun testSafetyDtoSerialization() {
        val gson = Gson()
        val request = SafetyScoreRequest(
            conversationId = "conv-123",
            context = listOf(
                SafetyMessageDto(senderId = "viewer", timestampMs = 1000L, text = "Hello")
            ),
            draft = SafetyMessageDto(senderId = "viewer", timestampMs = 2000L, text = "Draft content")
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"conversation_id\":\"conv-123\""))
        assertTrue(json.contains("\"draft\""))
        assertTrue(json.contains("\"timestamp_ms\":2000"))

        val responseJson = """{"toxicity":0.85,"derail_risk":0.62,"should_nudge":true,"model_version":"head-b-v1"}"""
        val response = gson.fromJson(responseJson, SafetyScoreResponse::class.java)
        assertEquals(0.85f, response.toxicity, 0.001f)
        assertEquals(0.62f, response.derailRisk, 0.001f)
        assertTrue(response.shouldNudge)
        assertEquals("head-b-v1", response.modelVersion)
    }

    @Test
    fun testRemoteAiClientSafetyScore() = runBlocking {
        val fakeApi = object : FwpAiApi {
            override suspend fun rank(request: RankRequest): RankResponse = RankResponse(emptyList())
            override suspend fun compatibility(request: CompatRequest): CompatResponse = CompatResponse(0, 0f, 0, 0, 0, 0, false)
            override suspend fun health(request: HealthRequest): HealthResponse = HealthResponse("HEALTHY", 10, emptyList(), emptyList(), 0)
            override suspend fun safetyScore(request: SafetyScoreRequest): SafetyScoreResponse {
                return SafetyScoreResponse(
                    toxicity = 0.9f,
                    derailRisk = 0.8f,
                    shouldNudge = true,
                    modelVersion = "test-model"
                )
            }
        }

        val client = RemoteAiClient(customApi = fakeApi)
        val result = client.safetyScore("conv-1", emptyList(), "Tin nhắn nhạy cảm")
        assertNotNull(result)
        assertEquals(0.9f, result!!.toxicity, 0.001f)
        assertTrue(result.shouldNudge)
    }

    @Test
    fun testRemoteAiClientErrorGracefulFallback() = runBlocking {
        val failingApi = object : FwpAiApi {
            override suspend fun rank(request: RankRequest): RankResponse = RankResponse(emptyList())
            override suspend fun compatibility(request: CompatRequest): CompatResponse = CompatResponse(0, 0f, 0, 0, 0, 0, false)
            override suspend fun health(request: HealthRequest): HealthResponse = HealthResponse("HEALTHY", 10, emptyList(), emptyList(), 0)
            override suspend fun safetyScore(request: SafetyScoreRequest): SafetyScoreResponse {
                throw RuntimeException("Network timeout")
            }
        }

        val client = RemoteAiClient(customApi = failingApi)
        val result = client.safetyScore("conv-1", emptyList(), "hello")
        assertNull(result)
    }

    // -------------------------------------------------------------
    // 3. Kiểm tra ChatViewModel logic nhắc nhở / dialog
    // -------------------------------------------------------------
    private class FakeChatRepository : FwpChatRepository {
        val sentMessages = mutableListOf<String>()
        val matchFlow = MutableStateFlow<FwpMatch?>(
            FwpMatch("match1", "partner1", ConnectionMode.FRIENDS, 80)
        )
        val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
        val profileFlow = MutableStateFlow<Profile?>(
            Profile(
                id = "partner1", nickname = "Partner", fullName = "Partner Name", age = 21,
                gender = Gender.OTHER, school = "PTIT", campus = "Hà Nội", faculty = "CNTT",
                major = "CNTT", academicYear = "D21", alumni = false, gpa = 3.5, gpaTier = GpaTier.GOOD,
                bio = "", interests = listOf("CNTT"), avatarSeed = 1, distanceKm = 1.0, verified = true,
                isDemo = false, showGpa = true, showLocation = true, mode = ConnectionMode.FRIENDS
            )
        )
        val meFlow = MutableStateFlow<Profile?>(
            Profile(
                id = "me", nickname = "Me", fullName = "Me Name", age = 21,
                gender = Gender.OTHER, school = "PTIT", campus = "Hà Nội", faculty = "CNTT",
                major = "CNTT", academicYear = "D21", alumni = false, gpa = 3.5, gpaTier = GpaTier.GOOD,
                bio = "", interests = listOf("CNTT"), avatarSeed = 1, distanceKm = 1.0, verified = true,
                isDemo = false, showGpa = true, showLocation = true, mode = ConnectionMode.FRIENDS
            )
        )

        override val myProfile: Flow<Profile?> = meFlow
        override fun messages(matchId: String): Flow<List<ChatMessage>> = messagesFlow
        override fun match(matchId: String): Flow<FwpMatch?> = matchFlow
        override fun profile(id: String): Flow<Profile?> = if (id == "me") meFlow else profileFlow

        override suspend fun sendMessage(matchId: String, senderId: String, text: String, type: MessageType) {
            sentMessages.add(text)
        }
        override suspend fun markRead(matchId: String) {}
        override suspend fun updateCompatibility(matchId: String, score: Int) {}
        override suspend fun setRevealOptIn(matchId: String, enabled: Boolean) {}
        override suspend fun unmatch(matchId: String) {}
        override suspend fun blockProfile(profileId: String, reason: String?) {}
        override suspend fun reportProfile(profileId: String, reason: String) {}
    }

    @Test
    fun testSendSafeMessageImmediatelySends() = runBlocking {
        val repo = FakeChatRepository()
        val settings = MutableStateFlow(AppSettings())
        val remoteAi = RemoteAiClient(enabled = false)
        val vm = ChatViewModel("match1", repo, settings, remoteAi)

        vm.send("Chào cậu, chiều nay rảnh không?")

        assertEquals(listOf("Chào cậu, chiều nay rảnh không?"), repo.sentMessages)
        assertNull(vm.pendingDraft.value)
    }

    @Test
    fun testSendToxicMessageOfflineHoldsPendingDraft() = runBlocking {
        val repo = FakeChatRepository()
        val settings = MutableStateFlow(AppSettings())
        val remoteAi = RemoteAiClient(enabled = false) // offline mode
        val vm = ChatViewModel("match1", repo, settings, remoteAi)

        vm.send("Mày ngu thế")

        // Tin nhắn độc hại bị dừng lại, chưa gửi vào repo
        assertTrue(repo.sentMessages.isEmpty())
        // Giữ trong pendingDraft để hiển thị Dialog xin ý kiến
        assertEquals("Mày ngu thế", vm.pendingDraft.value)
    }

    @Test
    fun testConfirmSendSendsDraftWithoutPenalty() = runBlocking {
        val repo = FakeChatRepository()
        val settings = MutableStateFlow(AppSettings())
        val remoteAi = RemoteAiClient(enabled = false)
        val vm = ChatViewModel("match1", repo, settings, remoteAi)

        vm.send("Đồ óc chó")
        assertEquals("Đồ óc chó", vm.pendingDraft.value)
        assertTrue(repo.sentMessages.isEmpty())

        // Người dùng chọn "Vẫn gửi"
        vm.confirmSend()

        // Tin nhắn được gửi mà không bị phạt
        assertEquals(listOf("Đồ óc chó"), repo.sentMessages)
        // pendingDraft được giải phóng
        assertNull(vm.pendingDraft.value)
    }

    @Test
    fun testCancelSendClearsDraftWithoutSending() = runBlocking {
        val repo = FakeChatRepository()
        val settings = MutableStateFlow(AppSettings())
        val remoteAi = RemoteAiClient(enabled = false)
        val vm = ChatViewModel("match1", repo, settings, remoteAi)

        vm.send("Cút đi")
        assertEquals("Cút đi", vm.pendingDraft.value)
        assertTrue(repo.sentMessages.isEmpty())

        // Người dùng chọn "Sửa lại"
        vm.cancelSend()

        // Không gửi tin nào
        assertTrue(repo.sentMessages.isEmpty())
        // pendingDraft đã xóa
        assertNull(vm.pendingDraft.value)
    }

    @Test
    fun testRemoteAiNudgeTriggersPendingDraft() = runBlocking {
        val fakeApi = object : FwpAiApi {
            override suspend fun rank(request: RankRequest): RankResponse = RankResponse(emptyList())
            override suspend fun compatibility(request: CompatRequest): CompatResponse = CompatResponse(0, 0f, 0, 0, 0, 0, false)
            override suspend fun health(request: HealthRequest): HealthResponse = HealthResponse("HEALTHY", 10, emptyList(), emptyList(), 0)
            override suspend fun safetyScore(request: SafetyScoreRequest): SafetyScoreResponse {
                // Server dự báo nguy cơ cao (Head B nudge)
                return SafetyScoreResponse(
                    toxicity = 0.6f,
                    derailRisk = 0.7f,
                    shouldNudge = true,
                    modelVersion = "head-b-craft-gru"
                )
            }
        }

        val repo = FakeChatRepository()
        val settings = MutableStateFlow(AppSettings())
        val remoteAi = RemoteAiClient(customApi = fakeApi)
        val vm = ChatViewModel("match1", repo, settings, remoteAi)

        vm.send("Câu nói có sắc thái căng thẳng")

        assertTrue(repo.sentMessages.isEmpty())
        assertEquals("Câu nói có sắc thái căng thẳng", vm.pendingDraft.value)

        vm.confirmSend()
        assertEquals(listOf("Câu nói có sắc thái căng thẳng"), repo.sentMessages)
        assertNull(vm.pendingDraft.value)
    }
}
