package vn.ptit.fwp.ui.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import vn.ptit.fwp.data.model.CandidateCard
import vn.ptit.fwp.data.model.gpaTierLabel
import vn.ptit.fwp.data.model.graduationLabel
import vn.ptit.fwp.ui.components.BlurredAvatar
import vn.ptit.fwp.ui.components.CenteredMessage
import vn.ptit.fwp.ui.components.TagRow
import vn.ptit.fwp.ui.theme.Spacing
import kotlin.math.abs

data class SwipeUiState(
    val loading: Boolean = true,
    val cards: List<CandidateCard> = emptyList(),
    val error: String? = null,
    val newMatch: Pair<CandidateCard, String>? = null, // (người vừa match, matchId)
)

class SwipeViewModel(private val repo: FwpRepository) : ViewModel() {
    private val _state = MutableStateFlow(SwipeUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.candidates() }
            .onSuccess { cards -> _state.update { it.copy(loading = false, cards = cards) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Lỗi kết nối") } }
    }

    fun swipe(card: CandidateCard, like: Boolean) = viewModelScope.launch {
        _state.update { it.copy(cards = it.cards.drop(1)) }
        runCatching { repo.swipe(card.userId, like) }
            .onSuccess { r -> if (r.matched && r.matchId != null) _state.update { it.copy(newMatch = card to r.matchId) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun dismissMatch() = _state.update { it.copy(newMatch = null) }
}

@Composable
fun SwipeScreen(onOpenChat: (String) -> Unit) {
    val vm: SwipeViewModel = viewModel(factory = viewModelFactory { initializer { SwipeViewModel(ServiceLocator.repository()) } })
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(Spacing.lg)) {
        val top = state.cards.firstOrNull()
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && top == null -> CenteredMessage("Không tải được", state.error) {
                Button(onClick = { vm.load() }) { Text("Thử lại") }
            }
            top == null -> CenteredMessage("Hết người phù hợp", "Thử nới bộ lọc hoặc quay lại sau nhé.") {
                Button(onClick = { vm.load() }) { Text("Tải lại") }
            }
            else -> SwipeDeck(top, onSwipe = { like -> vm.swipe(top, like) })
        }
    }

    state.newMatch?.let { (card, matchId) ->
        AlertDialog(
            onDismissRequest = vm::dismissMatch,
            title = { Text("Hai bạn đã match!") },
            text = { Text("Bạn và ${card.nickname} cùng thích nhau. Ảnh sẽ rõ dần khi hai bạn trò chuyện.") },
            confirmButton = { Button(onClick = { vm.dismissMatch(); onOpenChat(matchId) }) { Text("Nhắn tin") } },
            dismissButton = { TextButton(onClick = vm::dismissMatch) { Text("Tiếp tục") } },
        )
    }
}

@Composable
private fun SwipeDeck(card: CandidateCard, onSwipe: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(card.userId) { Animatable(0f) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / 40f
                }
                .pointerInput(card.userId) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) > size.width / 3f) {
                                    val like = offsetX.value > 0
                                    offsetX.animateTo(if (like) size.width * 1.5f else -size.width * 1.5f)
                                    onSwipe(like)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                    }
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            CandidateContent(card)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedIconButton(onClick = { onSwipe(false) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Bỏ qua")
            }
            FilledIconButton(
                onClick = { onSwipe(true) },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = "Thích")
            }
        }
    }
}

@Composable
private fun CandidateContent(card: CandidateCard) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Ảnh luôn mờ ở màn Swipe: quyết định dựa trên thông tin, không dựa trên ngoại hình
        BlurredAvatar(card.userId, card.nickname, blurLevel = 1f, size = 220.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("${card.nickname}, ${card.age}", style = MaterialTheme.typography.headlineSmall)
        Text("${card.schoolName} · ${card.facultyName}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "${graduationLabel(card.graduationStatus)} · ${gpaTierLabel(card.gpaTier)} · cách ${"%.1f".format(card.distanceKm)} km",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(card.bio, style = MaterialTheme.typography.bodyLarge)
        if (card.commonTags.isNotEmpty()) {
            Text("${card.commonTags.size} sở thích chung", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        TagRow(card.interestTags, highlighted = card.commonTags.toSet())
    }
}
