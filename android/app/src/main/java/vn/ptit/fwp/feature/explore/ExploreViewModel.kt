package vn.ptit.fwp.feature.explore

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

data class ExploreFilters(
    val query: String = "",
    val mode: ConnectionMode? = null,
    val campus: String? = null,
    val faculty: String? = null,
    val sortByDistance: Boolean = false
)

data class ExploreUiState(
    val loading: Boolean = true,
    val me: Profile? = null,
    val items: List<RankedProfile> = emptyList(),
    val filters: ExploreFilters = ExploreFilters(),
    val campuses: List<String> = emptyList(),
    val faculties: List<String> = emptyList(),
    val incomingLikes: Int = 0,
    val totalProfiles: Int = 0,
    val likedIds: Set<String> = emptySet(),
    val passedIds: Set<String> = emptySet()
)

class ExploreViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _filters = MutableStateFlow(ExploreFilters())

    val filters: StateFlow<ExploreFilters> = _filters

    val uiState: StateFlow<ExploreUiState> = combine(
        repository.profiles,
        repository.myProfile,
        repository.swipes,
        _filters
    ) { profiles, me, swipes, filters ->
        val swipeMap = swipes.associate { it.targetId to it.action }
        val likedIds = swipeMap.filterValues { it != vn.ptit.fwp.core.model.SwipeAction.PASS }.keys
        val passedIds = swipeMap.filterValues { it == vn.ptit.fwp.core.model.SwipeAction.PASS }.keys
        val query = filters.query.trim().lowercase()
        val filtered = profiles.filter { profile ->
            val queryOk = query.isBlank() ||
                profile.nickname.lowercase().contains(query) ||
                profile.faculty.lowercase().contains(query) ||
                profile.major.lowercase().contains(query) ||
                profile.interests.any { it.lowercase().contains(query) }
            val modeOk = filters.mode == null || profile.mode == filters.mode
            val campusOk = filters.campus == null || profile.campus == filters.campus
            val facultyOk = filters.faculty == null || profile.faculty == filters.faculty
            queryOk && modeOk && campusOk && facultyOk
        }.map { RankingEngine.score(me, it) }
        val sorted = if (filters.sortByDistance) {
            filtered.sortedBy { it.profile.distanceKm }
        } else {
            filtered.sortedByDescending { it.score }
        }
        val incoming = profiles.count { it.likedYou && !swipeMap.containsKey(it.id) }
        ExploreUiState(
            loading = false,
            me = me,
            items = sorted,
            filters = filters,
            campuses = profiles.map { it.campus }.distinct().sorted(),
            faculties = profiles.map { it.faculty }.distinct().sorted(),
            incomingLikes = incoming,
            totalProfiles = profiles.size,
            likedIds = likedIds,
            passedIds = passedIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    fun setQuery(query: String) = _filters.value.let { _filters.value = it.copy(query = query) }

    fun setMode(mode: ConnectionMode?) = _filters.value.let { _filters.value = it.copy(mode = mode) }

    fun setCampus(campus: String?) = _filters.value.let { _filters.value = it.copy(campus = campus) }

    fun setFaculty(faculty: String?) = _filters.value.let { _filters.value = it.copy(faculty = faculty) }

    fun toggleSort() = _filters.value.let { _filters.value = it.copy(sortByDistance = !it.sortByDistance) }

    fun reset() {
        _filters.value = ExploreFilters()
    }
}
