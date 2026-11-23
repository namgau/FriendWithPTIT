package vn.ptit.fwp.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.RankingEngine
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile
import vn.ptit.fwp.core.model.SwipeAction

data class DiscoverUiState(
    val loading: Boolean = true,
    val me: Profile? = null,
    val deck: List<RankedProfile> = emptyList(),
    val filters: DiscoveryFilters = DiscoveryFilters(),
    val source: String = "Quy tắc FwP (fallback)",
    val aiConfigured: Boolean = false,
    val canUndo: Boolean = false,
    val busy: Boolean = false,
    val refreshing: Boolean = false
)

data class CelebrationData(val profile: Profile, val matchId: String)

internal data class RemoteDeck(
    val viewer: Profile,
    val filters: DiscoveryFilters,
    val candidates: Map<String, Profile>,
    val ranks: List<RankedProfile>
)

internal fun orderedDeck(
    viewer: Profile?, filters: DiscoveryFilters, local: List<RankedProfile>, remote: RemoteDeck?
): List<RankedProfile> {
    val current = local.associateBy { it.profile.id }
    if (remote == null || remote.viewer != viewer || remote.filters != filters ||
        remote.candidates != current.mapValues { it.value.profile }) return local
    // The service order and filtering are authoritative only for the exact request.
    return remote.ranks.distinctBy { it.profile.id }.mapNotNull { ranked ->
        current[ranked.profile.id]?.let { ranked.copy(profile = it.profile) }
    }
}

class DiscoverViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _filters = MutableStateFlow(DiscoveryFilters())
    private val _remoteRanks = MutableStateFlow<RemoteDeck?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _celebration = MutableStateFlow<CelebrationData?>(null)
    private val _busy = MutableStateFlow(false)
    private val _refreshing = MutableStateFlow(false)

    val message: StateFlow<String?> = _message.asStateFlow()
    val celebration: StateFlow<CelebrationData?> = _celebration.asStateFlow()

    private data class BaseDeck(
        val me: Profile?,
        val candidates: List<Profile>,
        val hasSwipes: Boolean,
        val filters: DiscoveryFilters
    )

    private val baseDeck = combine(
        repository.myProfile,
        repository.profiles,
        repository.swipes,
        _filters
    ) { me, profiles, swipes, filters ->
        val swipedIds = swipes.map { it.targetId }.toSet()
        BaseDeck(
            me = me,
            candidates = profiles.filter { it.id !in swipedIds },
            hasSwipes = swipes.isNotEmpty(),
            filters = filters
        )
    }

    val uiState: StateFlow<DiscoverUiState> = combine(
        baseDeck,
        _remoteRanks,
        _busy,
        _refreshing
    ) { base, remote, busy, refreshing ->
        val localRanks = RankingEngine.rank(base.me, base.candidates, base.filters)
        val merged = orderedDeck(base.me, base.filters, localRanks, remote)
        DiscoverUiState(
            loading = false,
            me = base.me,
            deck = merged,
            filters = base.filters,
            source = merged.firstOrNull()?.source ?: "Quy tắc FwP (fallback)",
            aiConfigured = container.remoteAi.isConfigured,
            canUndo = base.hasSwipes,
            busy = busy,
            refreshing = refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    fun setFilters(filters: DiscoveryFilters) {
        _filters.value = filters
    }

    fun resetFilters() {
        _filters.value = DiscoveryFilters()
        _message.value = "Đã đặt lại bộ lọc."
    }

    fun swipe(action: SwipeAction) {
        if (_busy.value) return
        val state = uiState.value
        val top = state.deck.firstOrNull()?.profile ?: return
        _busy.value = true
        viewModelScope.launch {
            val outcome = repository.swipe(top.id, action)
            if (outcome?.matched == true && outcome.matchId != null) {
                _celebration.value = CelebrationData(outcome.profile, outcome.matchId)
            } else {
                _message.value = when (action) {
                    SwipeAction.LIKE -> "Đã thích ${top.nickname}. Nếu họ cũng thích bạn, FwP sẽ báo ngay."
                    SwipeAction.PASS -> "Đã bỏ qua ${top.nickname}."
                    SwipeAction.SUPER_LIKE -> "Đã gửi Super Like tới ${top.nickname}. Hồ sơ của bạn sẽ nổi bật hơn."
                }
            }
            delay(220)
            _busy.value = false
        }
    }

    fun undo() {
        viewModelScope.launch {
            val ok = repository.rewind()
            _message.value = if (ok) "Đã hoàn tác lượt gần nhất." else "Chưa có lượt nào để hoàn tác."
        }
    }

    fun resetDeck() {
        viewModelScope.launch {
            repository.resetSwipes()
            _remoteRanks.value = null
            _message.value = "Đã làm mới toàn bộ gợi ý. Bắt đầu lại từ hồ sơ đầu tiên nhé!"
        }
    }

    fun refreshRanking() {
        if (_refreshing.value) return
        val state = uiState.value
        val me = state.me
        if (me == null) {
            _message.value = "Hãy hoàn thiện hồ sơ trước khi làm mới gợi ý."
            return
        }
        if (!container.remoteAi.isConfigured) {
            _message.value = "AI Service chưa được cấu hình. FwP đang dùng Quy tắc FwP (fallback) hoàn toàn trên thiết bị."
            return
        }
        _refreshing.value = true
        viewModelScope.launch {
            val base = baseDeck.first()
            val viewer = base.me
            if (viewer == null) {
                _refreshing.value = false
                return@launch
            }
            val candidates = RankingEngine.rank(viewer, base.candidates, base.filters).map { it.profile }
            val remote = container.remoteAi.rank(viewer, candidates, base.filters)
            if (remote.isNullOrEmpty()) {
                _remoteRanks.value = null
                _message.value = "Không kết nối được AI Service. Giữ nguyên thứ hạng từ Quy tắc FwP (fallback)."
            } else {
                val current = baseDeck.first()
                val currentCandidates = RankingEngine.rank(current.me, current.candidates, current.filters).map { it.profile }
                if (current.me != viewer || current.filters != base.filters ||
                    currentCandidates.associateBy { it.id } != candidates.associateBy { it.id }) {
                    _remoteRanks.value = null
                    _message.value = "Danh sách đã thay đổi; đang dùng thứ hạng trên thiết bị."
                } else {
                    _remoteRanks.value = RemoteDeck(viewer, base.filters, candidates.associateBy { it.id }, remote)
                    _message.value = "Đã cập nhật thứ hạng từ AI Service. Nguồn: ${remote.first().source}."
                }
            }
            _refreshing.value = false
        }
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }
}
