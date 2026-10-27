package vn.ptit.fwp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.ServiceLocator
import vn.ptit.fwp.data.FwpRepository
import vn.ptit.fwp.data.Settings
import vn.ptit.fwp.data.model.MatchState
import vn.ptit.fwp.data.model.ME_ID
import vn.ptit.fwp.data.model.Message
import vn.ptit.fwp.data.model.PublicProfile
import vn.ptit.fwp.ui.components.BlurredAvatar
import vn.ptit.fwp.ui.components.CompatOrb
import vn.ptit.fwp.ui.theme.Spacing

data class ChatUiState(
    val other: PublicProfile? = null,
    val messages: List<Message> = emptyList(),
    val match: MatchState? = null,
    val input: String = "",
    val sending: Boolean = false,
    val pendingNudge: String? = null, // tin bị Head B đánh dấu, chờ người dùng xác nhận
    val error: String? = null,
)

class ChatViewModel(
    private val matchId: String,
    private val repo: FwpRepository,
    private val settings: Settings,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val other = repo.matches().first { it.matchId == matchId }.other
                _state.update { it.copy(other = other, messages = repo.messages(matchId)) }
                refreshState()
            }.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text) }

    /** Gửi tin: hỏi Head B trước (nếu bật), tin có nguy cơ thì hiện hộp thoại xác nhận. */
    fun onSend() = viewModelScope.launch {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return@launch
        _state.update { it.copy(sending = true) }
        val nudge = settings.safetyNudge &&
            runCatching { repo.safety(matchId, _state.value.messages, text).shouldNudge }.getOrDefault(false)
        if (nudge) _state.update { it.copy(sending = false, pendingNudge = text) } else send(text)
    }

    fun confirmNudge(sendAnyway: Boolean) = viewModelScope.launch {
        val text = _state.value.pendingNudge ?: return@launch
        _state.update { it.copy(pendingNudge = null) }
        if (sendAnyway) send(text)
    }

    private suspend fun send(text: String) {
        _state.update { it.copy(sending = true) }
        runCatching { repo.send(matchId, text) }
            .onSuccess { msgs -> _state.update { it.copy(messages = msgs, input = "", sending = false) } }
            .onFailure { e -> _state.update { it.copy(sending = false, error = e.message) } }
        refreshState()
    }

    private suspend fun refreshState() {
        runCatching { repo.state(matchId) }.onSuccess { s -> _state.update { it.copy(match = s) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(matchId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = viewModel(
        key = matchId,
        factory = viewModelFactory { initializer { ChatViewModel(matchId, ServiceLocator.repository(), ServiceLocator.settings) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }

    val blur = state.match?.blur?.blurLevel?.toFloat() ?: 1f
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Scaffold ngoài (FwpApp) đã xử lý insets
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        state.other?.let { BlurredAvatar(it.userId, it.nickname, blur, 40.dp) }
                        Column {
                            Text(state.other?.nickname ?: "…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.match?.blur?.revealed == true) "Ảnh đã hiện rõ" else "Ảnh rõ ${((1 - blur) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = { state.match?.let { CompatOrb(it.compat.toFloat(), 44.dp, Modifier.padding(end = Spacing.sm)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val suggestion = state.match?.health?.suggestions?.firstOrNull()
            if (ServiceLocator.settings.aiSuggestions && suggestion != null) {
                HealthBanner(state.match!!.health.level, suggestion)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.messages, key = { "${it.timestampMs}-${it.senderId}" }) { MessageBubble(it) }
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhắn tin…") },
                    maxLines = 4,
                )
                IconButton(onClick = { vm.onSend() }, enabled = state.input.isNotBlank() && !state.sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Gửi")
                }
            }
        }
    }

    state.pendingNudge?.let {
        AlertDialog(
            onDismissRequest = { vm.confirmNudge(false) },
            title = { Text("Bạn có chắc muốn gửi?") },
            text = { Text("Tin này có thể khiến người kia thấy khó chịu. Chỉ bạn nhìn thấy lời nhắc này.") },
            confirmButton = { TextButton(onClick = { vm.confirmNudge(true) }) { Text("Vẫn gửi") } },
            dismissButton = { TextButton(onClick = { vm.confirmNudge(false) }) { Text("Sửa lại") } },
        )
    }
}

@Composable
private fun HealthBanner(level: String, suggestion: String) {
    val atRisk = level == "at_risk"
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = if (atRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(if (atRisk) "Cuộc trò chuyện đang chững lại" else "Gợi ý nhỏ", style = MaterialTheme.typography.labelLarge)
            Text(suggestion, style = MaterialTheme.typography.bodyMedium)
            Text("Chỉ bạn nhìn thấy gợi ý này", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MessageBubble(m: Message) {
    val mine = m.senderId == ME_ID
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text.orEmpty(),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
