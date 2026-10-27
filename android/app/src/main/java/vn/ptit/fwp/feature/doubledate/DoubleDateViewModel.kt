package vn.ptit.fwp.feature.doubledate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.RankingEngine
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.Profile

data class DoubleDateCandidate(
    val match: FwpMatch,
    val profile: Profile,
    val sharedInterests: List<String>
)

data class DoubleDateUiState(
    val loading: Boolean = true,
    val candidates: List<DoubleDateCandidate> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val creating: Boolean = false,
    val createdMatchId: String? = null,
    val error: String? = null
)

class DoubleDateViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    private val _creating = MutableStateFlow(false)
    private val _createdMatchId = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    private data class BaseCandidates(
        val candidates: List<DoubleDateCandidate>,
        val selectedIds: Set<String>
    )

    private val baseCandidates = combine(
        repository.matches,
        repository.profiles,
        repository.myProfile,
        _selected
    ) { matches, profiles, me, selected ->
        val byId = profiles.associateBy { it.id }
        val candidates = matches
            .filter { !it.isGroup && !it.blocked }
            .mapNotNull { match ->
                val profile = byId[match.profileId] ?: return@mapNotNull null
                DoubleDateCandidate(match, profile, RankingEngine.sharedInterests(me, profile))
            }
        BaseCandidates(candidates, selected)
    }

    val uiState: StateFlow<DoubleDateUiState> = combine(
        baseCandidates,
        _creating,
        _createdMatchId,
        _error
    ) { base, creating, createdMatchId, error ->
        DoubleDateUiState(
            loading = false,
            candidates = base.candidates,
            selectedIds = base.selectedIds,
            creating = creating,
            createdMatchId = createdMatchId,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoubleDateUiState())

    fun toggle(matchId: String) {
        _selected.update { current ->
            when {
                current.contains(matchId) -> current - matchId
                current.size >= 2 -> current
                else -> current + matchId
            }
        }
        _error.value = null
    }

    fun create() {
        val selected = _selected.value.toList()
        if (selected.size < 2) {
            _error.value = "Hãy chọn đúng hai match để mở phòng Double Date."
            return
        }
        _creating.value = true
        viewModelScope.launch {
            val group = repository.createDoubleDate(selected[0], selected[1])
            if (group == null) {
                _error.value = "Không thể tạo phòng. Hãy thử lại với hai match khác."
            } else {
                _createdMatchId.value = group.id
            }
            _creating.value = false
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
