package vn.ptit.fwp.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.*
import vn.ptit.fwp.core.data.remote.*
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.SwipeAction

internal fun socialError(error: Throwable): String = when {
    error is SessionRequiredException -> "Phiên đã thay đổi. Hãy đăng nhập lại."
    error is HttpException && error.code() == 404 -> "Nội dung không còn khả dụng hoặc cuộc trò chuyện đã kết thúc."
    error is HttpException && error.code() == 409 -> "Định danh tin nhắn xung đột. Không tự động gửi lại."
    error is HttpException && error.code() == 400 -> "Yêu cầu không hợp lệ. Hãy kiểm tra nội dung và thử lại."
    error is HttpException && error.code() == 429 -> "Thao tác quá nhanh. Hãy thử lại sau."
    else -> "Không kết nối được backend. Hãy thử lại."
}

/** Old ViewModels may live on a back stack; invalidate them rather than borrowing a new session. */
abstract class BackendScopedViewModel(container: AppContainer, val identity: PresentationIdentity) : ViewModel() {
    protected val api = container.backendSocial.forIdentity(identity)
    protected var valid = true
    protected fun observeIdentity(container: AppContainer) {
        viewModelScope.launch {
            combine(container.settingsStore.settings, container.backendSession.sessionGeneration) { settings, generation ->
                presentationIdentity(settings, generation)
            }.collect { current ->
                if (current != identity) { valid = false; invalidate() }
            }
        }
    }
    protected abstract fun invalidate()
}

data class BackendDiscoverState(
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val profiles: List<SocialProfileDto> = emptyList(), val filters: DiscoveryFilters = DiscoveryFilters(),
    val total: Int = 0, val offset: Int = 0, val match: SocialMatchDto? = null, val notice: String? = null
)
class BackendDiscoverViewModel(container: AppContainer, identity: PresentationIdentity) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(BackendDiscoverState())
    val state = mutable.asStateFlow()
    private var refreshJob: Job? = null
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = BackendDiscoverState(loading = false); refreshJob?.cancel() }
    fun refresh(filters: DiscoveryFilters = mutable.value.filters) {
        if (!valid || mutable.value.busy) return
        refreshJob?.cancel()
        mutable.update { it.copy(loading = true, error = null, profiles = emptyList(), filters = filters, offset = 0) }
        refreshJob = viewModelScope.launch {
            try { val page = api.discover(0, filters); if (valid) mutable.update { it.copy(loading = false, profiles = page.items, total = page.total, offset = page.items.size) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, error = socialError(e)) } }
        }
    }
    fun more() {
        val s = mutable.value
        if (!valid || s.loading || s.busy || s.offset >= s.total) return
        mutable.update { it.copy(loading = true, error = null) }
        refreshJob = viewModelScope.launch {
            try { val page = api.discover(s.offset, s.filters); if (valid) mutable.update { it.copy(loading = false, profiles = (it.profiles + page.items).distinctBy { p -> p.id }, total = page.total, offset = s.offset + page.items.size) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, error = socialError(e)) } }
        }
    }
    fun swipe(action: SwipeAction) {
        val s = mutable.value; val profile = s.profiles.firstOrNull() ?: return
        if (!valid || s.busy || s.loading) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = api.swipe(profile.userId, action)
                if (valid) {
                    // Discover excludes ordinary swipes server-side, so refetch from offset zero.
                    mutable.update { it.copy(busy = false, match = result.match, notice = if (result.matched) "Hai bạn đã match." else if (action == SwipeAction.PASS) "Đã bỏ qua hồ sơ." else "Đã gửi lượt thích. Chờ người ấy thích lại.") }
                    refresh()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (valid) { mutable.update { it.copy(busy = false, error = socialError(e)) }; if (e is HttpException && e.code() == 404) refresh() }
            }
        }
    }
    fun dismissNotice() { mutable.update { it.copy(match = null, notice = null) } }
}

data class BackendMatchesState(val loading: Boolean = true, val error: String? = null,
    val matches: List<SocialMatchDto> = emptyList(), val incoming: List<SocialProfileDto> = emptyList())
class BackendMatchesViewModel(container: AppContainer, identity: PresentationIdentity) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(BackendMatchesState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = BackendMatchesState(loading = false); job?.cancel() }
    fun refresh() {
        if (!valid) return
        job?.cancel(); mutable.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val matches = api.matches(); val incoming = api.incoming()
                if (valid) mutable.value = BackendMatchesState(false, matches = matches.items, incoming = incoming.items)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, error = socialError(e)) } }
        }
    }
}

data class BackendDetailState(val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val profile: SocialProfileDto? = null, val match: SocialMatchDto? = null, val notice: String? = null, val finished: Boolean = false)
class BackendDetailViewModel(container: AppContainer, identity: PresentationIdentity, private val id: String) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(BackendDetailState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = BackendDetailState(loading = false); loadJob?.cancel() }
    fun refresh() {
        if (!valid || mutable.value.busy) return
        loadJob?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try { val profile = api.profile(id); if (valid) mutable.update { it.copy(loading = false, profile = profile) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, profile = null, error = socialError(e)) } }
        }
    }
    fun swipe(action: SwipeAction) = act {
        val profile = mutable.value.profile ?: return@act
        val result = api.swipe(profile.userId, action)
        if (valid) mutable.update { it.copy(match = result.match, finished = action == SwipeAction.PASS, notice = if (result.matched) "Hai bạn đã match." else "Đã gửi lượt thích. Chờ người ấy phản hồi.") }
    }
    fun block() = act { mutable.value.profile?.let { api.blockUser(it.userId) }; if (valid) mutable.update { it.copy(finished = true) } }
    private fun act(block: suspend () -> Unit) {
        if (!valid || mutable.value.busy || mutable.value.loading) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(error = socialError(e)) } }
            finally { if (valid) mutable.update { it.copy(busy = false) } }
        }
    }
}

data class BackendNotificationsState(val loading: Boolean = true, val items: List<SocialNotificationDto> = emptyList(), val unread: Int = 0, val error: String? = null)
class BackendNotificationsViewModel(container: AppContainer, identity: PresentationIdentity) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(BackendNotificationsState())
    val state = mutable.asStateFlow()
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = BackendNotificationsState(loading = false) }
    fun refresh(read: Boolean = false) {
        if (!valid) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (read) api.readNotifications()
                val result = api.notifications()
                if (valid) mutable.value = BackendNotificationsState(false, result.items, result.unread)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, error = socialError(e)) } }
        }
    }
}
