package vn.ptit.fwp.feature.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.RankingEngine
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile

data class NearbyUiState(
    val loading: Boolean = true,
    val me: Profile? = null,
    val items: List<RankedProfile> = emptyList(),
    val radiusKm: Int = 10,
    val mode: ConnectionMode? = null,
    val totalWithinRadius: Int = 0
)

class NearbyViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _radiusKm = MutableStateFlow(10)
    private val _mode = MutableStateFlow<ConnectionMode?>(null)

    val uiState: StateFlow<NearbyUiState> = combine(
        repository.profiles,
        repository.myProfile,
        _radiusKm,
        _mode
    ) { profiles, me, radius, mode ->
        val within = profiles
            .filter { it.distanceKm <= radius }
            .filter { mode == null || it.mode == mode }
            .map { RankingEngine.score(me, it) }
            .sortedBy { it.profile.distanceKm }
        NearbyUiState(
            loading = false,
            me = me,
            items = within,
            radiusKm = radius,
            mode = mode,
            totalWithinRadius = profiles.count { it.distanceKm <= radius }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    fun setRadius(radiusKm: Int) {
        _radiusKm.value = radiusKm
    }

    fun setMode(mode: ConnectionMode?) {
        _mode.value = mode
    }
}
