package vn.ptit.fwp.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.Swipe
import vn.ptit.fwp.core.data.toLocalProfile
import vn.ptit.fwp.core.data.allowsLocalDemo
import vn.ptit.fwp.core.data.demoCountOrZero
import vn.ptit.fwp.core.data.PresentationIdentity
import vn.ptit.fwp.core.data.presentationIdentity

data class Achievement(
    val title: String,
    val description: String,
    val unlocked: Boolean
)

data class ProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val backendProfile: Boolean = false,
    val me: Profile? = null,
    val matchCount: Int = 0,
    val activeChatCount: Int = 0,
    val incomingLikes: Int = 0,
    val bestCompatibility: Int = 0,
    val achievements: List<Achievement> = emptyList(),
    internal val identity: PresentationIdentity? = null
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private data class RemoteState(val email: String = "", val generation: Long = -1, val profile: Profile? = null, val loading: Boolean = false, val error: String? = null)
    private val remote = MutableStateFlow(RemoteState())
    private var refreshJob: Job? = null
    private val shownProfile = combine(container.settingsStore.settings, container.repository.myProfile, remote, container.backendSession.sessionGeneration) { settings, local, backend, generation ->
        val demo = allowsLocalDemo(settings.authenticated, settings.localDemoSession)
        when {
            demo -> ShownProfile(local, false, null, presentationIdentity(settings, generation))
            !settings.authenticated || settings.localDemoSession -> ShownProfile(null, true, null, presentationIdentity(settings, generation))
            backend.generation != generation || !backend.email.equals(settings.email, ignoreCase = true) -> ShownProfile(null, true, null, presentationIdentity(settings, generation))
            else -> ShownProfile(backend.profile, true, backend.error, presentationIdentity(settings, generation))
        }
    }

    private data class ShownProfile(val me: Profile?, val backend: Boolean, val error: String?, val identity: PresentationIdentity)

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val settings = container.settingsStore.settings.first()
            if (!settings.authenticated || settings.localDemoSession) { remote.value = RemoteState(); return@launch }
            val generation = container.backendSession.sessionGeneration.value
            val email = settings.email
            remote.value = RemoteState(email = email, generation = generation, loading = true)
            try {
                val profile = container.backendProfile.load().toLocalProfile()
                val current = container.settingsStore.settings.first()
                if (current.authenticated && !current.localDemoSession && current.email.equals(email, true) && container.backendSession.sessionGeneration.value == generation)
                    remote.value = RemoteState(email = email, generation = generation, profile = profile)
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val current = container.settingsStore.settings.first()
                if (current.authenticated && !current.localDemoSession && current.email.equals(email, true) && container.backendSession.sessionGeneration.value == generation)
                    remote.value = RemoteState(email = email, generation = generation, error = e.message ?: "Không tải được hồ sơ. Hãy thử lại.")
            }
        }
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        shownProfile,
        container.repository.matches,
        container.repository.profiles,
        container.repository.swipes
    ) { profileState, matches, profiles, swipes ->
        val (me, backend, error, identity) = profileState
        profileUiState(me, backend, error, identity, matches, profiles, swipes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}

/** Backend profiles never inherit statistics or achievements from the local demo history. */
internal fun profileUiState(
    me: Profile?,
    backend: Boolean,
    error: String?,
    identity: PresentationIdentity,
    matches: List<FwpMatch>,
    profiles: List<Profile>,
    swipes: List<Swipe>
): ProfileUiState {
        if (backend) return ProfileUiState(loading = false, error = error, backendProfile = true, identity = identity, me = me)
        val matchCount = matches.count { !it.isGroup }
        val active = matches.count { it.lastMessage != null }
        val swipedIds = swipes.map { it.targetId }.toSet()
        val incoming = profiles.count { it.likedYou && it.id !in swipedIds }
        val best = matches.maxOfOrNull { it.compatibility } ?: 0
        return ProfileUiState(
            loading = false,
            error = error,
            backendProfile = backend,
            identity = identity,
            me = me,
            matchCount = demoCountOrZero(!backend, matchCount),
            activeChatCount = demoCountOrZero(!backend, active),
            incomingLikes = demoCountOrZero(!backend, incoming),
            bestCompatibility = demoCountOrZero(!backend, best),
            achievements = if (!backend) buildAchievements(matches, matchCount, active) else emptyList()
        )
}

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
