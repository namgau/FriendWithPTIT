package vn.ptit.fwp.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.Profile

data class Achievement(
    val title: String,
    val description: String,
    val unlocked: Boolean
)

data class ProfileUiState(
    val loading: Boolean = true,
    val me: Profile? = null,
    val matchCount: Int = 0,
    val activeChatCount: Int = 0,
    val incomingLikes: Int = 0,
    val bestCompatibility: Int = 0,
    val achievements: List<Achievement> = emptyList()
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> = combine(
        container.repository.myProfile,
        container.repository.matches,
        container.repository.profiles,
        container.repository.swipes
    ) { me, matches, profiles, swipes ->
        val matchCount = matches.count { !it.isGroup }
        val active = matches.count { it.lastMessage != null }
        val swipedIds = swipes.map { it.targetId }.toSet()
        val incoming = profiles.count { it.likedYou && it.id !in swipedIds }
        val best = matches.maxOfOrNull { it.compatibility } ?: 0
        ProfileUiState(
            loading = false,
            me = me,
            matchCount = matchCount,
            activeChatCount = active,
            incomingLikes = incoming,
            bestCompatibility = best,
            achievements = buildAchievements(matches, matchCount, active)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private fun buildAchievements(
        matches: List<FwpMatch>,
        matchCount: Int,
        active: Int
    ): List<Achievement> = listOf(
        Achievement(
            "Mảnh ghép đầu tiên",
            "Có match đầu tiên trên FwP",
            matchCount >= 1
        ),
        Achievement(
            "Vòng tròn mở rộng",
            "Match với 3 người",
            matchCount >= 3
        ),
        Achievement(
            "Giữ nhịp giỏi",
            "Có cuộc trò chuyện đạt tương thích từ 70",
            matches.any { it.compatibility >= 70 }
        ),
        Achievement(
            "Chuyên gia phá băng",
            "Duy trì ít nhất 2 cuộc trò chuyện đang hoạt động",
            active >= 2
        ),
        Achievement(
            "Người bạn đồng hành",
            "Tạo một phòng Double Date",
            matches.any { it.isGroup }
        )
    )
}
