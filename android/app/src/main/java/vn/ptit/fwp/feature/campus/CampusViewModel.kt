package vn.ptit.fwp.feature.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.remote.CampusSettingsDto
import vn.ptit.fwp.core.data.remote.UpdateCampusSettingsRequest
import vn.ptit.fwp.core.model.CampusAnonymousMode
import vn.ptit.fwp.core.model.CampusAuthorProfile
import vn.ptit.fwp.core.model.CampusCategory
import vn.ptit.fwp.core.model.CampusComment
import vn.ptit.fwp.core.model.CampusPost
import vn.ptit.fwp.core.model.PickedCampusImage

enum class CampusTab(val api: String, val label: String) {
    FOR_YOU("for-you", "Dành cho bạn"),
    SAVED("saved", "Đã lưu"),
    MINE("mine", "Bài của tôi")
}

data class CampusMatchNotice(
    val matchId: String,
    val alias: String
)

data class CampusUiState(
    val configured: Boolean = true,
    val settings: CampusSettingsDto? = null,
    val settingsLoading: Boolean = false,
    val settingsSaving: Boolean = false,
    val settingsError: String? = null,
    val tab: CampusTab = CampusTab.FOR_YOU,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val posts: List<CampusPost> = emptyList(),
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val creating: Boolean = false,
    val busyPostIds: Set<String> = emptySet(),
    val message: String? = null,
    val error: String? = null,
    val matchNotice: CampusMatchNotice? = null,
    val author: CampusAuthorProfile? = null,
    val authorLoading: Boolean = false,
    val commentsFor: CampusPost? = null,
    val comments: List<CampusComment> = emptyList(),
    val commentsCursor: String? = null,
    val commentsHasMore: Boolean = false,
    val commentsLoading: Boolean = false,
    val sendingComment: Boolean = false
)

class CampusViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.campusRepository
    private var feedJob: Job? = null
    private val feedGeneration = RequestGeneration()
    private val commentsGeneration = RequestGeneration()
    private val authorGeneration = RequestGeneration()
    private val _state = MutableStateFlow(
        CampusUiState(configured = repository.isConfigured)
    )
    val state: StateFlow<CampusUiState> = _state.asStateFlow()

    init {
        refresh()
        loadSettings()
    }

    fun loadSettings() {
        if (_state.value.settingsLoading || _state.value.settingsSaving) return
        _state.update { it.copy(settingsLoading = true, settingsError = null) }
        viewModelScope.launch {
            runCatching { repository.settings() }
                .onSuccess { result -> _state.update { it.copy(settings = result, settingsLoading = false) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(settingsLoading = false, settingsError = humanError(error), settings = null) }
                }
        }
    }

    fun updateSettings(request: UpdateCampusSettingsRequest) {
        if (_state.value.settingsLoading || _state.value.settingsSaving) return
        _state.update { it.copy(settingsSaving = true, settingsError = null) }
        viewModelScope.launch {
            runCatching { repository.updateSettings(request) }
                .onSuccess { result ->
                    _state.update { it.copy(settings = result, settingsSaving = false) }
                    closeAuthor()
                    refresh()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(settingsSaving = false, settingsError = humanError(error)) }
                    loadSettings()
                    _state.update { it.copy(message = humanError(error)) }
                }
        }
    }

    fun refresh() {
        closeAuthor()
        feedJob?.cancel()
        val generation = feedGeneration.next()
        val tab = _state.value.tab
        _state.update {
            it.copy(
                loading = true,
                loadingMore = false,
                error = null,
                configured = repository.isConfigured
            )
        }
        feedJob = viewModelScope.launch {
            runCatching { repository.feed(tab.api) }
                .onSuccess { page ->
                    if (!feedGeneration.isCurrent(generation)) return@onSuccess
                    _state.update {
                        it.copy(
                            loading = false,
                            posts = page.items,
                            cursor = page.nextCursor,
                            hasMore = page.hasMore,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || !feedGeneration.isCurrent(generation)) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            error = humanError(error),
                            configured = repository.isConfigured
                        )
                    }
                }
        }
    }

    fun selectTab(tab: CampusTab) {
        if (_state.value.tab == tab) return
        _state.update {
            it.copy(
                tab = tab,
                posts = emptyList(),
                cursor = null,
                hasMore = false,
                error = null
            )
        }
        refresh()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        val cursor = current.cursor ?: return
        val tab = current.tab
        val generation = feedGeneration.snapshot()
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.feed(tab.api, cursor) }
                .onSuccess { page ->
                    if (!feedGeneration.isCurrent(generation) || _state.value.tab != tab) return@onSuccess
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            posts = it.posts + page.items,
                            cursor = page.nextCursor,
                            hasMore = page.hasMore
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException || !feedGeneration.isCurrent(generation)) return@onFailure
                    _state.update {
                        it.copy(loadingMore = false, message = humanError(error))
                    }
                }
        }
    }

    fun toggleLike(post: CampusPost) {
        if (post.viewer.isAuthor || post.id in _state.value.busyPostIds) return
        val nextLiked = !post.viewer.liked
        patchPost(post.id) {
            it.copy(
                viewer = it.viewer.copy(liked = nextLiked),
                counts = it.counts.copy(
                    likes = (it.counts.likes + if (nextLiked) 1 else -1)
                        .coerceAtLeast(0)
                )
            )
        }
        _state.update { it.copy(busyPostIds = it.busyPostIds + post.id) }
        viewModelScope.launch {
            runCatching { repository.setLiked(post.id, nextLiked) }
                .onSuccess { (liked, count) ->
                    patchPost(post.id) {
                        it.copy(
                            viewer = it.viewer.copy(liked = liked),
                            counts = it.counts.copy(likes = count)
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    patchPost(post.id) { rollbackLike(it, post) }
                    _state.update { it.copy(message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) refresh()
                }
            _state.update { it.copy(busyPostIds = it.busyPostIds - post.id) }
        }
    }

    fun toggleBookmark(post: CampusPost) {
        if (post.id in _state.value.busyPostIds) return
        val next = !post.viewer.bookmarked
        patchPost(post.id) {
            it.copy(
                viewer = it.viewer.copy(bookmarked = next),
                counts = it.counts.copy(
                    bookmarks = (it.counts.bookmarks + if (next) 1 else -1)
                        .coerceAtLeast(0)
                )
            )
        }
        _state.update { it.copy(busyPostIds = it.busyPostIds + post.id) }
        viewModelScope.launch {
            runCatching { repository.setBookmarked(post.id, next) }
                .onSuccess { (bookmarked, count) ->
                    patchPost(post.id) {
                        it.copy(
                            viewer = it.viewer.copy(bookmarked = bookmarked),
                            counts = it.counts.copy(bookmarks = count)
                        )
                    }
                    if (!bookmarked && _state.value.tab == CampusTab.SAVED) {
                        _state.update { state ->
                            state.copy(posts = state.posts.filterNot { it.id == post.id })
                        }
                    }
                    _state.update {
                        it.copy(
                            message = if (bookmarked) {
                                "Đã lưu bài viết vào mục Đã lưu."
                            } else {
                                "Đã bỏ lưu bài viết."
                            }
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    patchPost(post.id) { rollbackBookmark(it, post) }
                    _state.update { it.copy(message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) refresh()
                }
            _state.update { it.copy(busyPostIds = it.busyPostIds - post.id) }
        }
    }

    fun deletePost(post: CampusPost) {
        if (post.id in _state.value.busyPostIds) return
        _state.update { it.copy(busyPostIds = it.busyPostIds + post.id) }
        viewModelScope.launch {
            runCatching { repository.deletePost(post.id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            posts = it.posts.filterNot { item -> item.id == post.id },
                            message = "Đã xóa bài viết."
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) refresh()
                }
            _state.update { it.copy(busyPostIds = it.busyPostIds - post.id) }
        }
    }

    fun editPost(post: CampusPost, content: String, category: CampusCategory, onDone: () -> Unit) {
        if (post.id in _state.value.busyPostIds) return
        _state.update { it.copy(busyPostIds = it.busyPostIds + post.id) }
        viewModelScope.launch {
            runCatching { repository.updatePost(post.id, content, category.api) }
                .onSuccess {
                    _state.update { state -> state.copy(message = "Đã cập nhật bài viết.") }
                    onDone()
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) refresh()
                }
            _state.update { it.copy(busyPostIds = it.busyPostIds - post.id) }
        }
    }

    fun reportComment(comment: CampusComment, reason: String) {
        val post = _state.value.commentsFor ?: return
        val generation = commentsGeneration.snapshot()
        if (_state.value.sendingComment) return
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            runCatching { repository.reportComment(post.id, comment.id, reason) }
                .onSuccess {
                    _state.update { it.copy(sendingComment = false, message = "Đã gửi báo cáo bình luận.") }
                    if (commentsGeneration.isCurrent(generation)) openComments(post)
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(sendingComment = false, message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) {
                        if (commentsGeneration.isCurrent(generation)) closeComments()
                        refresh()
                    }
                }
        }
    }

    fun createPost(
        content: String,
        category: CampusCategory,
        mode: CampusAnonymousMode,
        images: List<PickedCampusImage>,
        onCreated: () -> Unit
    ) {
        if (_state.value.creating) return
        if (mode == CampusAnonymousMode.DISCOVERABLE && _state.value.settings?.discoveryEnabled != true) {
            _state.update { it.copy(message = "Hãy bật khám phá tác giả trong cài đặt Campus trước khi đăng.") }
            return
        }
        if (content.isBlank() && images.isEmpty()) {
            _state.update { it.copy(message = "Hãy nhập nội dung hoặc chọn ít nhất một ảnh.") }
            return
        }
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            runCatching {
                repository.createPost(content, category.api, mode.api, images)
            }.onSuccess { created ->
                if (_state.value.tab == CampusTab.FOR_YOU) {
                    _state.update {
                        it.copy(posts = listOf(created) + it.posts, creating = false)
                    }
                } else {
                    _state.update { it.copy(creating = false) }
                }
                _state.update {
                    it.copy(message = "Đã đăng bài ẩn danh lên Campus Feed.")
                }
                onCreated()
                refresh()
                loadSettings()
            }.onFailure { error ->
                _state.update {
                    it.copy(creating = false, message = humanError(error))
                }
                if (error is HttpException && error.code() == 409) loadSettings()
            }
        }
    }

    fun report(post: CampusPost, reason: String) {
        if (post.id in _state.value.busyPostIds) return
        _state.update { it.copy(busyPostIds = it.busyPostIds + post.id) }
        viewModelScope.launch {
            runCatching { repository.reportPost(post.id, reason) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            posts = it.posts.filterNot { item -> item.id == post.id },
                            message = "Đã gửi báo cáo. Bài viết không còn xuất hiện trong feed của bạn."
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = humanError(error)) }
                    if (error is HttpException && error.code() == 404) refresh()
                }
            _state.update { it.copy(busyPostIds = it.busyPostIds - post.id) }
        }
    }

    fun openAuthor(token: String) {
        val generation = authorGeneration.next()
        _state.update { it.copy(authorLoading = true, author = null) }
        viewModelScope.launch {
            runCatching { repository.author(token) }
                .onSuccess { author ->
                    if (!authorGeneration.isCurrent(generation)) return@onSuccess
                    _state.update { it.copy(authorLoading = false, author = author) }
                }
                .onFailure { error ->
                    if (!authorGeneration.isCurrent(generation) || error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(authorLoading = false, message = humanError(error))
                    }
                    if (error is HttpException && error.code() == 404) refresh()
                }
        }
    }

    fun closeAuthor() {
        authorGeneration.next()
        _state.update { it.copy(author = null, authorLoading = false) }
    }

    fun dismissMatchNotice() {
        _state.update { it.copy(matchNotice = null) }
    }

    fun swipeAuthor(
        author: CampusAuthorProfile,
        action: String
    ) {
        if (_state.value.authorLoading) return
        val generation = authorGeneration.snapshot()
        _state.update { it.copy(authorLoading = true) }
        viewModelScope.launch {
            runCatching { repository.swipeAuthor(author.token, action) }
                .onSuccess { result ->
                    if (!authorGeneration.isCurrent(generation)) return@onSuccess
                    _state.update {
                        it.copy(
                            authorLoading = false,
                            author = null,
                            matchNotice = if (result.matched && result.matchId != null) {
                                CampusMatchNotice(result.matchId, author.alias)
                            } else {
                                it.matchNotice
                            },
                            message = if (result.matched) {
                                null
                            } else if (action == "PASS") {
                                "Đã bỏ qua tác giả ẩn danh."
                            } else {
                                "Đã gửi lượt thích ẩn danh."
                            }
                        )
                    }
                }
                .onFailure { error ->
                    if (!authorGeneration.isCurrent(generation) || error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(authorLoading = false, message = humanError(error))
                    }
                    if (error is HttpException && error.code() == 404) {
                        closeAuthor()
                        refresh()
                    }
                }
        }
    }

    fun openComments(post: CampusPost) {
        val generation = commentsGeneration.next()
        _state.update {
            it.copy(
                commentsFor = post,
                comments = emptyList(),
                commentsCursor = null,
                commentsHasMore = false,
                commentsLoading = true
            )
        }
        viewModelScope.launch {
            runCatching { repository.comments(post.id) }
                .onSuccess { page ->
                    if (!commentsGeneration.isCurrent(generation)) return@onSuccess
                    _state.update {
                        it.copy(
                            commentsLoading = false,
                            comments = page.items,
                            commentsCursor = page.nextCursor,
                            commentsHasMore = page.hasMore
                        )
                    }
                }
                .onFailure { error ->
                    if (!commentsGeneration.isCurrent(generation) || error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(commentsLoading = false, message = humanError(error))
                    }
                    if (error is HttpException && error.code() == 404) { closeComments(); refresh() }
                }
        }
    }

    fun closeComments() {
        commentsGeneration.next()
        _state.update {
            it.copy(
                commentsFor = null,
                comments = emptyList(),
                commentsCursor = null,
                commentsHasMore = false
            )
        }
    }

    fun loadMoreComments() {
        val current = _state.value
        val post = current.commentsFor ?: return
        val cursor = current.commentsCursor ?: return
        val generation = commentsGeneration.snapshot()
        if (current.commentsLoading || !current.commentsHasMore) return
        _state.update { it.copy(commentsLoading = true) }
        viewModelScope.launch {
            runCatching { repository.comments(post.id, cursor) }
                .onSuccess { page ->
                    if (!commentsGeneration.isCurrent(generation)) return@onSuccess
                    _state.update {
                        it.copy(
                            commentsLoading = false,
                            comments = it.comments + page.items,
                            commentsCursor = page.nextCursor,
                            commentsHasMore = page.hasMore
                        )
                    }
                }
                .onFailure { error ->
                    if (!commentsGeneration.isCurrent(generation) || error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(commentsLoading = false, message = humanError(error))
                    }
                }
        }
    }

    fun addComment(content: String, onDone: () -> Unit) {
        val post = _state.value.commentsFor ?: return
        val generation = commentsGeneration.snapshot()
        if (_state.value.sendingComment || content.isBlank()) return
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            runCatching { repository.addComment(post.id, content) }
                .onSuccess { comment ->
                    patchPost(post.id) {
                        it.copy(counts = it.counts.copy(comments = it.counts.comments + 1))
                    }
                    _state.update {
                        it.copy(
                            sendingComment = false,
                            comments = if (commentsGeneration.isCurrent(generation)) listOf(comment) + it.comments else it.comments
                        )
                    }
                    if (commentsGeneration.isCurrent(generation)) onDone()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(sendingComment = false, message = humanError(error))
                    }
                    if (error is HttpException && error.code() == 404) {
                        if (commentsGeneration.isCurrent(generation)) closeComments()
                        refresh()
                    }
                }
        }
    }

    fun deleteComment(comment: CampusComment) {
        val post = _state.value.commentsFor ?: return
        val generation = commentsGeneration.snapshot()
        if (_state.value.sendingComment) return
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            runCatching { repository.deleteComment(post.id, comment.id) }
                .onSuccess {
                    patchPost(post.id) {
                        it.copy(
                            counts = it.counts.copy(
                                comments = (it.counts.comments - 1).coerceAtLeast(0)
                            )
                        )
                    }
                    _state.update {
                        it.copy(
                            sendingComment = false,
                            comments = if (commentsGeneration.isCurrent(generation)) it.comments.filterNot { item -> item.id == comment.id } else it.comments
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(sendingComment = false, message = humanError(error))
                    }
                    if (error is HttpException && error.code() == 404) {
                        if (commentsGeneration.isCurrent(generation)) openComments(post)
                        refresh()
                    }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun patchPost(postId: String, transform: (CampusPost) -> CampusPost) {
        _state.update { state ->
            state.copy(
                posts = state.posts.map { if (it.id == postId) transform(it) else it },
                commentsFor = state.commentsFor?.let {
                    if (it.id == postId) transform(it) else it
                }
            )
        }
    }

    private fun humanError(error: Throwable): String = campusError(error)
}
