package vn.ptit.fwp.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.CompatibilityEngine
import vn.ptit.fwp.core.ai.ConversationAssistant
import vn.ptit.fwp.core.ai.HealthEngine
import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.CompatibilityReport
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.HealthReport
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.Profile

data class ChatUiState(
    val loading: Boolean = true,
    val match: FwpMatch? = null,
    val profile: Profile? = null,
    val me: Profile? = null,
    val messages: List<ChatMessage> = emptyList(),
    val compatibility: CompatibilityReport? = null,
    val health: HealthReport? = null,
    val aiEnabled: Boolean = true,
    val typing: Boolean = false,
    val openingMoves: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val aiConfigured: Boolean = false,
    val analyzing: Boolean = false
) {
    val revealAvailable: Boolean
        get() = (compatibility?.revealEligible == true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
    private val matchId: String
) : ViewModel() {
    private val repository = container.repository
    private val _typing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _finished = MutableStateFlow(false)
    private val _analyzing = MutableStateFlow(false)
    private val _remoteCompatibility = MutableStateFlow<Pair<Int, CompatibilityReport>?>(null)
    private val _remoteHealth = MutableStateFlow<Pair<Int, HealthReport>?>(null)

    val message: StateFlow<String?> = _message.asStateFlow()
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private data class Snapshot(
        val match: FwpMatch?,
        val messages: List<ChatMessage>,
        val profile: Profile?,
        val me: Profile?
    )

    private val conversation = repository.match(matchId).flatMapLatest { match ->
        if (match == null) {
            flowOf(Snapshot(null, emptyList(), null, null))
        } else {
            combine(
                repository.messages(matchId),
                repository.profile(match.profileId),
                repository.myProfile
            ) { messages, profile, me ->
                Snapshot(match, messages, profile, me)
            }
        }
    }

    private data class BaseChat(
        val snapshot: Snapshot,
        val settings: vn.ptit.fwp.core.data.AppSettings,
        val typing: Boolean,
        val analyzing: Boolean
    )

    private val baseChat = combine(
        conversation,
        container.settingsStore.settings,
        _typing,
        _analyzing
    ) { snapshot, settings, typing, analyzing ->
        BaseChat(snapshot, settings, typing, analyzing)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        baseChat,
        _remoteCompatibility,
        _remoteHealth
    ) { base, remoteCompatibility, remoteHealth ->
        val snapshot = base.snapshot
        val settings = base.settings
        val typing = base.typing
        val analyzing = base.analyzing
        val match = snapshot.match
        val profile = snapshot.profile
        if (match == null || profile == null) {
            return@combine ChatUiState(loading = false, match = match, aiEnabled = settings.aiAnalysisEnabled)
        }
        val chatMessages = snapshot.messages
        val localCompatibility = if (settings.aiAnalysisEnabled) {
            CompatibilityEngine.analyze(snapshot.me, profile, match, chatMessages)
        } else {
            CompatibilityReport(
                score = CompatibilityEngine.previewScore(snapshot.me, profile),
                blurLevel = 0.92f,
                personality = 0,
                emotion = 0,
                interest = CompatibilityEngine.previewScore(snapshot.me, profile),
                messageCount = chatMessages.count { it.type != MessageType.SYSTEM },
                revealEligible = false,
                revealed = false,
                source = "Chưa bật phân tích hội thoại"
            )
        }
        val compatibility = remoteCompatibility
            ?.takeIf { it.first == localCompatibility.messageCount }
            ?.second ?: localCompatibility
        val health = if (settings.aiAnalysisEnabled) {
            remoteHealth
                ?.takeIf { it.first == localCompatibility.messageCount }
                ?.second
                ?: HealthEngine.analyze(chatMessages, snapshot.me, profile)
        } else null
        ChatUiState(
            loading = false,
            match = match,
            profile = profile,
            me = snapshot.me,
            messages = chatMessages,
            compatibility = compatibility,
            health = health,
            aiEnabled = settings.aiAnalysisEnabled,
            typing = typing,
            openingMoves = if (chatMessages.none { it.isMine }) ConversationAssistant.openingMoves(snapshot.me, profile) else emptyList(),
            suggestions = health?.suggestions ?: listOf("Hỏi một câu mở về tuần của họ", "Chia sẻ một chuyện nhỏ trong ngày"),
            aiConfigured = container.remoteAi.isConfigured,
            analyzing = analyzing
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.match != null && state.match.unreadCount > 0) {
                    repository.markRead(matchId)
                }
                val score = state.compatibility?.score
                if (score != null && score != state.match?.compatibility && state.aiEnabled) {
                    repository.updateCompatibility(matchId, score)
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val state = uiState.value
        if (state.match == null || state.profile == null) return
        viewModelScope.launch {
            repository.sendMessage(matchId, "me", trimmed)
            scheduleReply()
        }
    }

    fun sendImage(uri: String) {
        if (uri.isBlank()) return
        val state = uiState.value
        if (state.match == null || state.profile == null) return
        viewModelScope.launch {
            repository.sendMessage(matchId, "me", uri, MessageType.IMAGE)
            scheduleReply()
        }
    }

    private fun scheduleReply() {
        viewModelScope.launch {
            delay(900)
            _typing.value = true
            delay(1500)
            _typing.value = false
            val state = uiState.value
            val profile = state.profile ?: return@launch
            val health = state.health ?: HealthEngine.analyze(state.messages, state.me, profile)
            val reply = ConversationAssistant.reply(state.me, profile, state.messages, health)
            repository.sendMessage(matchId, profile.id, reply)
            repository.markRead(matchId)
        }
    }

    fun setRevealOptIn(enabled: Boolean) {
        val state = uiState.value
        viewModelScope.launch {
            repository.setRevealOptIn(matchId, enabled)
            val compatibility = state.compatibility
            _message.value = when {
                !enabled -> "Đã tắt chia sẻ ảnh. Ảnh sẽ tiếp tục được làm mờ."
                compatibility == null -> "Đã bật chia sẻ ảnh."
                compatibility.revealEligible -> "Cả hai đã đồng ý. Ảnh đã được mở!"
                compatibility.messageCount < CompatibilityEngine.MIN_MESSAGES_TO_REVEAL ->
                    "Đã bật. Cần thêm ${CompatibilityEngine.MIN_MESSAGES_TO_REVEAL - compatibility.messageCount} tin nhắn và điểm tương thích từ ${CompatibilityEngine.REVEAL_SCORE}."
                else -> "Đã bật. Cần điểm tương thích từ ${CompatibilityEngine.REVEAL_SCORE}; hiện tại là ${compatibility.score}."
            }
        }
    }

    fun analyzeAgain() {
        val state = uiState.value
        val me = state.me
        val profile = state.profile
        val match = state.match
        if (me == null || profile == null || match == null) return
        if (!container.remoteAi.isConfigured) {
            _message.value = "AI Service chưa được cấu hình. Đang dùng Quy tắc FwP (fallback) ngay trên thiết bị."
            return
        }
        _analyzing.value = true
        viewModelScope.launch {
            val localCompatibility = CompatibilityEngine.analyze(me, profile, match, state.messages)
            val localHealth = HealthEngine.analyze(state.messages, me, profile)
            val compatibility = container.remoteAi.compatibility(me, profile, match, state.messages, localCompatibility)
            val health = container.remoteAi.health(me, profile, state.messages, localHealth)
            _remoteCompatibility.value = localCompatibility.messageCount to compatibility
            _remoteHealth.value = localCompatibility.messageCount to health
            _message.value = "Đã phân tích lại. Nguồn: ${compatibility.source}."
            _analyzing.value = false
        }
    }

    fun unmatch() {
        viewModelScope.launch {
            repository.unmatch(matchId)
            _message.value = "Đã hủy match."
            _finished.value = true
        }
    }

    fun block() {
        val profile = uiState.value.profile ?: return
        viewModelScope.launch {
            repository.blockProfile(profile.id)
            _message.value = "Đã chặn ${profile.nickname}."
            _finished.value = true
        }
    }

    fun report(reason: String) {
        val profile = uiState.value.profile ?: return
        viewModelScope.launch {
            repository.reportProfile(profile.id, reason)
            _message.value = "Đã gửi báo cáo về ${profile.nickname}."
            _finished.value = true
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
