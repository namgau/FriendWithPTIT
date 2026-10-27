package vn.ptit.fwp.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.CompatibilityEngine
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.HealthLevel
import vn.ptit.fwp.core.model.Profile

data class ChatListEntry(
    val match: FwpMatch,
    val profile: Profile?,
    val title: String,
    val compatibility: Int,
    val health: HealthLevel
)

data class ChatsUiState(
    val loading: Boolean = true,
    val entries: List<ChatListEntry> = emptyList(),
    val unreadTotal: Int = 0
)

class ChatsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<ChatsUiState> = combine(
        repository.matches,
        repository.profiles,
        repository.myProfile,
        _query
    ) { matches, profiles, me, query ->
        val byId = profiles.associateBy { it.id }
        val now = System.currentTimeMillis()
        val entries = matches.mapNotNull { match ->
            val profile = byId[match.profileId]
            if (profile == null && !match.isGroup) return@mapNotNull null
            val title = if (match.isGroup) match.groupTitle ?: "Double Date" else profile?.nickname ?: "FwP"
            if (query.isNotBlank() && !title.lowercase().contains(query.trim().lowercase())) return@mapNotNull null
            val gapHours = ((now - (match.lastMessageAt ?: match.matchedAt)) / 3_600_000f).coerceAtLeast(0f)
            val health = when {
                gapHours >= 48f -> HealthLevel.AT_RISK
                gapHours >= 24f -> HealthLevel.FADING
                else -> HealthLevel.HEALTHY
            }
            ChatListEntry(
                match = match,
                profile = profile,
                title = title,
                compatibility = match.compatibility.takeIf { it > 0 }
                    ?: profile?.let { CompatibilityEngine.previewScore(me, it) } ?: 0,
                health = health
            )
        }
        ChatsUiState(
            loading = false,
            entries = entries,
            unreadTotal = matches.sumOf { it.unreadCount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState())

    fun setQuery(value: String) {
        _query.value = value
    }
}
