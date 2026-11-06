package vn.ptit.fwp.feature.campus

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.ptit.fwp.core.design.DemoPortrait
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTextField
import vn.ptit.fwp.core.design.PrimaryButton
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.SecondaryButton
import vn.ptit.fwp.core.model.CampusAnonymousMode
import vn.ptit.fwp.core.model.CampusAuthorProfile
import vn.ptit.fwp.core.model.CampusCategory
import vn.ptit.fwp.core.model.CampusComment
import vn.ptit.fwp.core.model.CampusPost
import vn.ptit.fwp.core.model.PickedCampusImage
import vn.ptit.fwp.core.data.remote.UpdateCampusSettingsRequest
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusScreen() {
    val container = appContainer()
    val viewModel = fwpViewModel { CampusViewModel(container) }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var composerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<CampusPost?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Campus Feed", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Ẩn danh, thật lòng, đúng chuyện",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.loadSettings(); settingsOpen = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Cài đặt riêng tư Campus")
                }
                IconButton(onClick = { viewModel.refresh(); viewModel.loadSettings() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Làm mới Campus Feed")
                }
            }

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CampusTab.entries) { tab ->
                    FwpChip(
                        text = tab.label,
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) }
                    )
                }
            }
            Text(
                "Following/Interested chưa mở vì chưa có subscription. Tạm thời dùng “Đã lưu” để theo dõi bài viết.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            when {
                !state.configured -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Campus Feed cần backend",
                    subtitle = "Truyền -PFWP_BACKEND_BASE_URL=http://10.0.2.2:3000/ khi build Android và chạy NestJS + PostgreSQL. App không dùng dữ liệu mock cho Campus Feed.",
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                )
                state.loading && state.posts.isEmpty() -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                state.error != null && state.posts.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Chưa tải được Campus Feed",
                    subtitle = state.error ?: "",
                    modifier = Modifier.weight(1f),
                    actionLabel = "Thử lại",
                    onAction = viewModel::refresh
                )
                state.posts.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = when (state.tab) { CampusTab.SAVED -> "Chưa lưu bài nào"; CampusTab.MINE -> "Bạn chưa có bài viết"; else -> "Campus đang yên ắng" },
                    subtitle = if (state.tab == CampusTab.SAVED) {
                        "Nhấn biểu tượng bookmark trên bài viết để lưu lại xem sau."
                    } else {
                        "Hãy là người mở đầu câu chuyện bằng một bài viết ẩn danh."
                    },
                    modifier = Modifier.weight(1f),
                    actionLabel = if (state.tab == CampusTab.FOR_YOU) "Đăng bài đầu tiên" else null,
                    onAction = if (state.tab == CampusTab.FOR_YOU) {
                        { composerOpen = true }
                    } else null
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 4.dp,
                        bottom = 92.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        CampusPostCard(
                            post = post,
                            busy = post.id in state.busyPostIds,
                            onLike = { viewModel.toggleLike(post) },
                            onBookmark = { viewModel.toggleBookmark(post) },
                            onComments = { viewModel.openComments(post) },
                            onAuthor = { token -> viewModel.openAuthor(token) },
                            onEdit = { editingPost = post },
                            onRefreshMedia = viewModel::refresh,
                            onDelete = { viewModel.deletePost(post) },
                            onReport = { reason -> viewModel.report(post, reason) }
                        )
                    }
                    item {
                        if (state.loadingMore) {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else if (state.hasMore) {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tải thêm bài viết")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { composerOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = PtitRed,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Đăng bài Campus")
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
        )
    }

    if (composerOpen) {
        CampusComposerSheet(
            creating = state.creating,
            discoveryEnabled = state.settings?.discoveryEnabled == true && !state.settingsLoading && !state.settingsSaving,
            onDismiss = { composerOpen = false },
            onSubmit = { content, category, mode, images ->
                viewModel.createPost(content, category, mode, images) {
                    composerOpen = false
                    scope.launch { snackbar.showSnackbar("Đã đăng bài ẩn danh lên Campus Feed.") }
                }
            }
        )
    }

    if (settingsOpen) {
        CampusSettingsSheet(
            state = state,
            onDismiss = { settingsOpen = false },
            onRetry = viewModel::loadSettings,
            onUpdate = viewModel::updateSettings
        )
    }

    editingPost?.let { post ->
        CampusEditDialog(post, post.id in state.busyPostIds, onDismiss = { editingPost = null }) { content, category ->
            viewModel.editPost(post, content, category) { editingPost = null }
        }
    }

    val commentsFor = state.commentsFor
    if (commentsFor != null) {
        CampusCommentsSheet(
            post = commentsFor,
            comments = state.comments,
            loading = state.commentsLoading,
            sending = state.sendingComment,
            hasMore = state.commentsHasMore,
            onDismiss = viewModel::closeComments,
            onLoadMore = viewModel::loadMoreComments,
            onSend = { text, done -> viewModel.addComment(text, done) },
            onDelete = viewModel::deleteComment,
            onReport = viewModel::reportComment
        )
    }

    if (state.authorLoading || state.author != null) {
        CampusAuthorSheet(
            author = state.author,
            loading = state.authorLoading,
            onDismiss = viewModel::closeAuthor,
            onPass = { author -> viewModel.swipeAuthor(author, "PASS") },
            onLike = { author -> viewModel.swipeAuthor(author, "LIKE") }
        )
    }

    state.matchNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMatchNotice,
            title = { Text("Đã match ẩn danh") },
            text = {
                Text(
                    "Backend đã tạo match với ${notice.alias} (${notice.matchId}). " +
                        "Mục Match/Chat trên app hiện vẫn dùng dữ liệu demo local nên chưa hiển thị match này; " +
                        "danh tính thật vẫn không được trả về qua Campus API."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMatchNotice) { Text("Tiếp tục") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusSettingsSheet(
    state: CampusUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onUpdate: (UpdateCampusSettingsRequest) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Riêng tư Campus", style = MaterialTheme.typography.headlineSmall)
            Text("Cài đặt được lưu trên backend. Tắt khám phá sẽ thu hồi các liên kết tác giả hiện có.", style = MaterialTheme.typography.bodySmall)
            if (state.settingsLoading) {
                CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
            } else if (state.settings == null) {
                Text(state.settingsError ?: "Chưa tải được cài đặt.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Thử lại") }
            } else {
                val settings = state.settings
                Text("Bài có chế độ khám phá đang hoạt động: ${settings.discoverablePosts}", style = MaterialTheme.typography.bodySmall)
                CampusSettingsToggle("Cho phép khám phá tác giả", settings.discoveryEnabled, !state.settingsSaving) {
                    onUpdate(UpdateCampusSettingsRequest(discoveryEnabled = it))
                }
                CampusSettingsToggle("Hiện khoa", settings.showFaculty, !state.settingsSaving) {
                    onUpdate(UpdateCampusSettingsRequest(showFaculty = it))
                }
                CampusSettingsToggle("Hiện campus", settings.showCampus, !state.settingsSaving) {
                    onUpdate(UpdateCampusSettingsRequest(showCampus = it))
                }
                CampusSettingsToggle("Hiện một số sở thích", settings.showInterests, !state.settingsSaving) {
                    onUpdate(UpdateCampusSettingsRequest(showInterests = it))
                }
                if (state.settingsSaving) Text("Đang lưu...")
                state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CampusSettingsToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CampusPostCard(
    post: CampusPost,
    busy: Boolean,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onComments: () -> Unit,
    onAuthor: (String) -> Unit,
    onEdit: () -> Unit,
    onRefreshMedia: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DemoPortrait(
                    seed = post.author.avatarSeed,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        post.author.alias,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (post.anonymousMode) {
                                CampusAnonymousMode.FULLY_ANONYMOUS -> "Ẩn danh hoàn toàn"
                                CampusAnonymousMode.DISCOVERABLE -> "Ẩn danh · có thể khám phá"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (post.anonymousMode == CampusAnonymousMode.DISCOVERABLE) {
                                PtitRed
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            " · ${formatCampusTime(post.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PtitGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        post.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = "Tùy chọn bài viết")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (post.viewer.isAuthor) {
                            DropdownMenuItem(
                                text = { Text("Sửa nội dung và chủ đề") },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa bài viết") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Báo cáo bài viết") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showReport = true
                                }
                            )
                        }
                    }
                }
            }

            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(post.content, style = MaterialTheme.typography.bodyLarge)
            }
            if (post.media.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                CampusMediaGrid(post, onRefreshMedia)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CampusMetric(
                    icon = if (post.viewer.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    text = post.counts.likes.toString(),
                    tint = if (post.viewer.liked) PtitRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !busy && !post.viewer.isAuthor,
                    onClick = onLike
                )
                CampusMetric(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    text = post.counts.comments.toString(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = true,
                    onClick = onComments
                )
                CampusMetric(
                    icon = if (post.viewer.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    text = post.counts.bookmarks.toString(),
                    tint = if (post.viewer.bookmarked) PtitGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !busy,
                    onClick = onBookmark
                )
                Spacer(Modifier.weight(1f))
                val token = post.author.authorToken
                if (token != null && post.author.discoverable && !post.viewer.isAuthor) {
                    Text(
                        "Hồ sơ ẩn danh",
                        style = MaterialTheme.typography.labelLarge,
                        color = PtitRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAuthor(token) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showReport) {
        CampusReportDialog(
            onDismiss = { showReport = false },
            onReport = { reason ->
                showReport = false
                onReport(reason)
            }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Xóa bài viết?") },
            text = { Text("Bài viết sẽ biến mất khỏi Campus Feed và ảnh kèm theo sẽ bị xóa khỏi dev storage.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun CampusMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun CampusMediaGrid(post: CampusPost, onRefresh: () -> Unit) {
    val media = post.media.take(4)
    if (media.size == 1) {
        CampusRemoteImage(media.first().url, Modifier.fillMaxWidth().height(210.dp), onRefresh)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    CampusRemoteImage(item.url, Modifier.weight(1f).height(150.dp), onRefresh)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CampusRemoteImage(url: String, modifier: Modifier, onRefresh: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableStateOf(0) }
    Box(modifier.clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        key(attempt) {
            AsyncImage(model = url, contentDescription = "Ảnh bài viết", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(), onError = { failed = true }, onSuccess = { failed = false })
        }
        if (failed) TextButton(onClick = { failed = false; attempt++; onRefresh() }) { Text("Không tải được ảnh · Làm mới") }
    }
}

@Composable
private fun CampusEditDialog(
    post: CampusPost,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, CampusCategory) -> Unit
) {
    var content by remember(post.id) { mutableStateOf(post.content) }
    var category by remember(post.id) { mutableStateOf(post.category) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Sửa bài viết") },
        text = {
            Column {
                Text("Chỉ sửa nội dung và chủ đề. Bài không còn hoạt động có thể không sửa được.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = content, onValueChange = { content = it.take(2000) }, label = { Text("Nội dung") })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CampusCategory.entries) { item ->
                        FilterChip(selected = item == category, onClick = { category = item }, label = { Text(item.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (content.isNotBlank() || post.media.isNotEmpty()), onClick = { onSave(content, category) }) { Text("Lưu") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun CampusReportDialog(
    title: String = "Báo cáo bài viết",
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    val reasons = listOf(
        "SPAM" to "Spam / quảng cáo",
        "HARASSMENT" to "Quấy rối",
        "HATE_SPEECH" to "Ngôn từ thù địch",
        "SEXUAL_CONTENT" to "Nội dung tình dục",
        "VIOLENCE" to "Bạo lực",
        "PRIVACY" to "Lộ thông tin cá nhân",
        "OTHER" to "Khác"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { (value, label) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onReport(value) }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusComposerSheet(
    creating: Boolean,
    discoveryEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        String,
        CampusCategory,
        CampusAnonymousMode,
        List<PickedCampusImage>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CampusCategory.GENERAL) }
    var mode by remember { mutableStateOf(CampusAnonymousMode.FULLY_ANONYMOUS) }
    var picked by remember { mutableStateOf<List<PickedUiImage>>(emptyList()) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    var readingImages by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (uris.size > 4) pickerError = "Chỉ chọn tối đa 4 ảnh."
        scope.launch {
            readingImages = true
            val result = mutableListOf<PickedUiImage>()
            for (uri in uris.take(4)) {
                runCatching { readCampusImage(context, uri) }
                    .onSuccess { if (it != null) result += it }
                    .onFailure { pickerError = it.message ?: "Không đọc được ảnh." }
            }
            picked = result
            readingImages = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Đăng lên Campus", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Bài viết luôn ẩn danh. Chọn chế độ hiển thị hồ sơ tối giản nếu muốn người khác có thể Like/Swipe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FwpTextField(
                value = content,
                onValueChange = { content = it.take(2000) },
                label = "Bạn muốn nói gì?",
                placeholder = "Một câu hỏi, một tâm sự, một mẩu chuyện trong campus...",
                singleLine = false,
                maxLines = 8
            )
            Spacer(Modifier.height(14.dp))
            Text("Chủ đề", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(7.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CampusCategory.entries) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.label) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Chế độ ẩn danh", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(7.dp))
            CampusAnonymousMode.entries.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (mode == item) {
                        PtitRed.copy(alpha = 0.07f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (mode == item) PtitRed else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable(enabled = item != CampusAnonymousMode.DISCOVERABLE || discoveryEnabled) { mode = item }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == item, onClick = { mode = item }, enabled = item != CampusAnonymousMode.DISCOVERABLE || discoveryEnabled)
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (!discoveryEnabled) {
                Text("Bật khám phá tác giả trong cài đặt Campus để chọn chế độ có thể khám phá.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    text = if (readingImages) "Đang đọc ảnh..." else "Chọn ảnh",
                    icon = Icons.Rounded.PhotoLibrary,
                    onClick = { picker.launch("image/*") },
                    enabled = !readingImages && picked.size < 4,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${picked.size}/4",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(picked) { item ->
                        Box {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = "Ảnh đã chọn",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            IconButton(
                                onClick = { picked = picked - item },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Bỏ ảnh",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            pickerError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ảnh JPG/PNG/WebP/GIF, tối đa 4 ảnh và 3 MB mỗi ảnh. Ảnh lưu ở dev storage, không lưu trong PostgreSQL.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Đăng ẩn danh",
                loading = creating,
                enabled = !creating && !readingImages && (mode != CampusAnonymousMode.DISCOVERABLE || discoveryEnabled) && (content.isNotBlank() || picked.isNotEmpty()),
                onClick = {
                    onSubmit(
                        content,
                        category,
                        mode,
                        picked.map { PickedCampusImage(it.dataUrl, it.width, it.height) }
                    )
                }
            )
        }
    }
}

private data class PickedUiImage(
    val uri: Uri,
    val dataUrl: String,
    val width: Int?,
    val height: Int?
)

private suspend fun readCampusImage(context: Context, uri: Uri): PickedUiImage? =
    withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val allowed = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        if (mime !in allowed) {
            throw IllegalArgumentException("Chỉ hỗ trợ ảnh JPG, PNG, WebP hoặc GIF.")
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        if (bytes.size > 3 * 1024 * 1024) {
            throw IllegalArgumentException("Mỗi ảnh phải nhỏ hơn 3 MB.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        PickedUiImage(
            uri = uri,
            dataUrl = "data:$mime;base64,$encoded",
            width = options.outWidth.takeIf { it > 0 },
            height = options.outHeight.takeIf { it > 0 }
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusCommentsSheet(
    post: CampusPost,
    comments: List<CampusComment>,
    loading: Boolean,
    sending: Boolean,
    hasMore: Boolean,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
    onSend: (String, () -> Unit) -> Unit,
    onDelete: (CampusComment) -> Unit,
    onReport: (CampusComment, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var reporting by remember { mutableStateOf<CampusComment?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp)
        ) {
            Text("Bình luận", style = MaterialTheme.typography.titleLarge)
            Text(
                "${post.author.alias} · ${post.counts.comments} bình luận",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (loading && comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Row(verticalAlignment = Alignment.Top) {
                            DemoPortrait(
                                seed = comment.avatarSeed,
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        comment.alias,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        " · ${formatCampusTime(comment.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (comment.isMine) {
                                        Text(
                                            " · Bạn",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PtitRed
                                        )
                                    }
                                }
                                Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (comment.isMine) {
                                IconButton(
                                    onClick = { onDelete(comment) },
                                    enabled = !sending
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Xóa bình luận",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { reporting = comment }, enabled = !sending) {
                                    Icon(Icons.Rounded.Flag, contentDescription = "Báo cáo bình luận", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    if (hasMore) {
                        item {
                            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                                Text("Tải thêm bình luận")
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FwpTextField(
                    value = text,
                    onValueChange = { text = it.take(600) },
                    label = "Viết bình luận...",
                    modifier = Modifier.weight(1f),
                    trailingContent = {
                        IconButton(
                            onClick = {
                                onSend(text) { text = "" }
                            },
                            enabled = !sending && text.isNotBlank()
                        ) {
                            if (sending) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Send, contentDescription = "Gửi bình luận")
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    reporting?.let { comment ->
        CampusReportDialog(title = "Báo cáo bình luận", onDismiss = { reporting = null }, onReport = { reason ->
            reporting = null
            onReport(comment, reason)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusAuthorSheet(
    author: CampusAuthorProfile?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPass: (CampusAuthorProfile) -> Unit,
    onLike: (CampusAuthorProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading || author == null) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            DemoPortrait(
                seed = author.avatarSeed,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(12.dp))
            Text(author.alias, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Hồ sơ ẩn danh · không có tên thật hay userId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            if (author.bio.isNotBlank()) {
                Text(
                    author.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                author.faculty?.let { InfoChip(it) }
                author.campus?.let { InfoChip(it) }
            }
            if (author.interests.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(author.interests) { interest ->
                        FwpChip(text = interest, selected = false, onClick = {})
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Bỏ qua",
                    onClick = { onPass(author) },
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Thích",
                    icon = Icons.Rounded.FavoriteBorder,
                    onClick = { onLike(author) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Like/Swipe đi vào match logic hiện có. Campus API không trả về danh tính thật.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatCampusTime(value: Long): String {
    val delta = System.currentTimeMillis() - value
    return when {
        delta < 60_000 -> "vừa xong"
        delta < 3_600_000 -> "${delta / 60_000} phút trước"
        delta < 86_400_000 -> "${delta / 3_600_000} giờ trước"
        delta < 7 * 86_400_000L -> "${delta / 86_400_000} ngày trước"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(value))
    }
}
