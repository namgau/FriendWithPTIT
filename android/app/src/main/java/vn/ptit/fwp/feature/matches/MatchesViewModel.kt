package vn.ptit.fwp.feature.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.ai.CompatibilityEngine
import vn.ptit.fwp.core.ai.RankingEngine
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.Profile

data class MatchCardData(
    val match: FwpMatch,
    val profile: Profile?,
    val sharedInterests: List<String>
)

data class IncomingLikeData(
    val profile: Profile,
    val score: Int
)

data class MatchesUiState(
    val loading: Boolean = true,
    val matches: List<MatchCardData> = emptyList(),
    val incomingLikes: List<IncomingLikeData> = emptyList(),
    val unreadMessages: Int = 0,
    val groupCount: Int = 0
)

class MatchesViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val uiState: StateFlow<MatchesUiState> = combine(
        repository.matches,
        repository.profiles,
        repository.myProfile,
        repository.swipes
    ) { matches, profiles, me, swipes ->
        val byId = profiles.associateBy { it.id }
        val swipedIds = swipes.map { it.targetId }.toSet()
        val matchCards = matches.mapNotNull { match ->
            val profile = byId[match.profileId]
            if (match.isGroup) {
                MatchCardData(match, profile, emptyList())
            } else if (profile != null) {
                MatchCardData(
                    match = match,
                    profile = profile,
                    sharedInterests = RankingEngine.sharedInterests(me, profile)
                )
            } else null
        }
        val incoming = profiles.filter { it.likedYou && it.id !in swipedIds }
            .map { IncomingLikeData(it, CompatibilityEngine.previewScore(me, it)) }
            .sortedByDescending { it.score }
        MatchesUiState(
            loading = false,
            matches = matchCards,
            incomingLikes = incoming,
            unreadMessages = matches.sumOf { it.unreadCount },
            groupCount = matches.count { it.isGroup }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesUiState())
}
