package vn.ptit.fwp.ui.matches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import vn.ptit.fwp.data.model.ME_ID
import vn.ptit.fwp.data.model.MatchSummary
import vn.ptit.fwp.ui.components.BlurredAvatar
import vn.ptit.fwp.ui.components.CenteredMessage

data class MatchesUiState(val loading: Boolean = true, val items: List<MatchSummary> = emptyList(), val error: String? = null)

class MatchesViewModel(private val repo: FwpRepository) : ViewModel() {
    private val _state = MutableStateFlow(MatchesUiState())
    val state = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        runCatching { repo.matches() }
            .onSuccess { items -> _state.update { it.copy(loading = false, items = items) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Lỗi kết nối") } }
    }
}

@Composable
fun MatchesScreen(onOpenChat: (String) -> Unit) {
    val vm: MatchesViewModel = viewModel(key = "matches-${ServiceLocator.settings.useServer}", factory = viewModelFactory { initializer { MatchesViewModel(ServiceLocator.repository()) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() } // tải lại mỗi lần quay về màn này

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> CenteredMessage("Không tải được", state.error) { Button(onClick = { vm.load() }) { Text("Thử lại") } }
            state.items.isEmpty() -> CenteredMessage("Chưa có match nào", "Quẹt phải ở tab Khám phá để bắt đầu.")
            else -> LazyColumn {
                items(state.items, key = { it.matchId }) { m ->
                    val last = m.lastMessage
                    ListItem(
                        modifier = Modifier.clickable { onOpenChat(m.matchId) },
                        leadingContent = { BlurredAvatar(m.other.userId, m.other.nickname, m.blur.blurLevel.toFloat(), 52.dp) },
                        headlineContent = { Text(m.other.nickname) },
                        supportingContent = {
                            Text(
                                when {
                                    last == null -> "Hãy gửi lời chào đầu tiên"
                                    last.senderId == ME_ID -> "Bạn: ${last.text.orEmpty()}"
                                    else -> last.text.orEmpty()
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = { Text("rõ ${((1 - m.blur.blurLevel) * 100).toInt()}%") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
