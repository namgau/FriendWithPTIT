package vn.ptit.fwp.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.CompatibilityEngine
import vn.ptit.fwp.core.ai.RankingEngine
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.SwipeAction
import vn.ptit.fwp.feature.discover.CelebrationData

data class ProfileDetailUiState(
    val loading: Boolean = true,
    val me: Profile? = null,
    val profile: Profile? = null,
    val score: Int = 0,
    val sharedInterests: List<String> = emptyList(),
    val isLiked: Boolean = false,
    val isPassed: Boolean = false,
    val matchId: String? = null,
    val busy: Boolean = false
)

class ProfileDetailViewModel(
    private val container: AppContainer,
    private val profileId: String
) : ViewModel() {
    private val repository = container.repository
    private val _busy = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _celebration = MutableStateFlow<CelebrationData?>(null)
    private val _finished = MutableStateFlow(false)

    val message: StateFlow<String?> = _message.asStateFlow()
    val celebration: StateFlow<CelebrationData?> = _celebration.asStateFlow()
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val uiState: StateFlow<ProfileDetailUiState> = combine(
        repository.profile(profileId),
        repository.myProfile,
        repository.swipes,
        repository.matches,
        _busy
    ) { profile, me, swipes, matches, busy ->
        val swipe = swipes.firstOrNull { it.targetId == profileId }
        ProfileDetailUiState(
            loading = false,
            me = me,
            profile = profile,
            score = CompatibilityEngine.previewScore(me, profile ?: return@combine ProfileDetailUiState(loading = false)),
            sharedInterests = RankingEngine.sharedInterests(me, profile ?: return@combine ProfileDetailUiState(loading = false)),
            isLiked = swipe?.action == SwipeAction.LIKE || swipe?.action == SwipeAction.SUPER_LIKE,
            isPassed = swipe?.action == SwipeAction.PASS,
            matchId = matches.firstOrNull { it.profileId == profileId }?.id,
            busy = busy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDetailUiState())

    fun like(superLike: Boolean = false) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val outcome = repository.swipe(profileId, if (superLike) SwipeAction.SUPER_LIKE else SwipeAction.LIKE)
            if (outcome?.matched == true && outcome.matchId != null) {
                _celebration.value = CelebrationData(outcome.profile, outcome.matchId)
            } else {
                _message.value = if (superLike) {
                    "Đã gửi Super Like. Hồ sơ của bạn sẽ được ưu tiên hiển thị."
                } else {
                    "Đã gửi lời mời kết nối. FwP sẽ báo bạn khi có hồi đáp."
                }
            }
            _busy.value = false
        }
    }

    fun pass() {
        viewModelScope.launch {
            repository.swipe(profileId, SwipeAction.PASS)
            _message.value = "Đã bỏ qua hồ sơ này."
        }
    }

    fun report(reason: String) {
        viewModelScope.launch {
            repository.reportProfile(profileId, reason)
            _message.value = "Đã gửi báo cáo và ẩn hồ sơ này khỏi FwP của bạn."
            _finished.value = true
        }
    }

    fun block() {
        viewModelScope.launch {
            repository.blockProfile(profileId)
            _message.value = "Đã chặn người dùng này."
            _finished.value = true
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun dismissCelebration() {
        _celebration.value = null
    }
}
