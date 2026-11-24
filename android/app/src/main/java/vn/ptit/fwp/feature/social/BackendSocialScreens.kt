package vn.ptit.fwp.feature.social

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.data.*
import vn.ptit.fwp.core.design.*
import vn.ptit.fwp.core.model.*
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import vn.ptit.fwp.feature.discover.SwipeCard
import vn.ptit.fwp.feature.discover.FilterSheet
import vn.ptit.fwp.feature.matches.IncomingLikeCard
import vn.ptit.fwp.feature.matches.IncomingLikeData
import vn.ptit.fwp.feature.matches.MatchCardData
import vn.ptit.fwp.feature.matches.MatchRow
import vn.ptit.fwp.feature.notifications.NotificationRow
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendDiscoverScreen(identity: PresentationIdentity, onOpenChat: (String) -> Unit, onOpenProfile: (String) -> Unit, onNotifications: () -> Unit = {}, onQuestions: () -> Unit = {}) {
    val container = appContainer()
    val vm = fwpViewModel(key = "discover:$identity") { BackendDiscoverViewModel(container, identity) }
    val state by vm.state.collectAsState()
    var filters by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            FwpLogo(size = 36.dp, withWordmark = true, modifier = Modifier.weight(1f))
            IconButton(onClick = onQuestions) { Icon(Icons.Rounded.Quiz, "Câu hỏi hợp gu") }
            IconButton(onClick = onNotifications) { Icon(Icons.Rounded.Notifications, "Thông báo") }
            IconButton(onClick = { vm.refresh() }, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Làm mới") }
            IconButton(onClick = { filters = true }, enabled = !state.busy) { Icon(Icons.Rounded.FilterList, "Bộ lọc") }
        }
        Text("Khám phá hôm nay", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            state.quota?.let { if (it.unlimited) "Không giới hạn lượt quẹt (tài khoản demo)" else "Còn ${it.remaining}/${it.limit} lượt quẹt hôm nay" }
                ?: "Lướt để tìm người hợp gu",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        Box(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            val top = state.profiles.firstOrNull()
            when {
                state.quota?.let { !it.unlimited && it.remaining == 0 } == true -> EmptyState(Icons.Rounded.HourglassBottom, "Hết lượt quẹt hôm nay",
                    "Mỗi ngày bạn có ${state.quota?.limit} lượt để lướt chậm và chọn kỹ. Lượt mới mở lại lúc 0h. Trong lúc chờ, thử trả lời vài câu hỏi để FwP hiểu gu bạn hơn.",
                    actionLabel = "Trả lời câu hỏi", onAction = onQuestions)
                top != null -> key(top.id) { SwipeCard(
                    ranked = RankedProfile(top.displayProfile(), top.compatibility ?: 0, top.rankingReasons.orEmpty(), "FwP"),
                    me = null, busy = state.busy || state.loading, modifier = Modifier.fillMaxSize(),
                    onLike = { vm.swipe(SwipeAction.LIKE) }, onPass = { vm.swipe(SwipeAction.PASS) }, onSuperLike = { vm.swipe(SwipeAction.SUPER_LIKE) }
                ) }
                state.loading -> CircularProgressIndicator()
                state.offset < state.total -> TextButton(onClick = vm::more) { Text("Tải thêm hồ sơ") }
                else -> EmptyState(Icons.Rounded.LocalFireDepartment, "Tạm hết gợi ý", "Đổi bộ lọc hoặc làm mới để kiểm tra gợi ý mới.", actionLabel = "Làm mới", onAction = { vm.refresh() })
            }
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }) {
        FilterSheet(state.filters, onApply = { filters = false; vm.refresh(it) }, onReset = { filters = false; vm.refresh(DiscoveryFilters()) })
    }
    state.notice?.let { notice -> AlertDialog(onDismissRequest = vm::dismissNotice,
        title = { Text(if (state.match != null) "Đã match" else "Đã gửi") }, text = { Text(notice) },
        confirmButton = { TextButton(onClick = { val match = state.match; vm.dismissNotice(); match?.let { onOpenChat(it.id) } }) { Text(if (state.match != null) "Mở trò chuyện" else "Đã hiểu") } }) }
}

@Composable
fun BackendNotificationsScreen(identity: PresentationIdentity, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "notifications:$identity") { BackendNotificationsViewModel(container, identity) }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        FwpTopBar("Thông báo", subtitle = "${state.unread} chưa đọc", onBack = onBack, actions = {
            TextButton(onClick = { vm.refresh(true) }, enabled = !state.loading) { Text("Đọc tất cả") }
            IconButton(onClick = { vm.refresh() }, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, "Làm mới") }
        })
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.id }) { item ->
                NotificationRow(FwpNotification(item.id, runCatching { NotificationType.valueOf(item.kind) }.getOrDefault(NotificationType.SYSTEM),
                    item.title, item.body, Instant.parse(item.createdAt).toEpochMilli(), item.read))
                item.data?.matchId?.takeIf { allowsBackendRoute(identity, it) }?.let { id -> TextButton(onClick = { onOpenChat(id) }) { Text("Mở trò chuyện") } }
            }
        }
    }
}

@Composable
fun BackendMatchesScreen(identity: PresentationIdentity, onOpenChat: (String) -> Unit, onOpenProfile: (String) -> Unit, chats: Boolean = false) {
    val container = appContainer()
    val vm = fwpViewModel(key = "matches:$identity") { BackendMatchesViewModel(container, identity) }
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.refresh() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Match & trò chuyện", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Làm mới") }
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, contentColor = PtitRed) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Match (${state.matches.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                BadgedBox(badge = { if (state.incoming.isNotEmpty()) Badge(containerColor = PtitRed) { Text("${state.incoming.size}") } }) {
                    Text("Đang thích bạn", modifier = Modifier.padding(end = 10.dp))
                }
            })
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        androidx.compose.animation.Crossfade(targetState = tab, label = "matchTabs") { current ->
            if (current == 0) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp)) {
                if (!state.loading && state.matches.isEmpty()) item { EmptyState(Icons.Rounded.Favorite, "Chưa có match", "Khi hai người cùng thích nhau, cuộc trò chuyện sẽ mở ở đây.") }
                items(state.matches, key = { it.id }) { match ->
                    val profile = match.displayProfile()
                    MatchRow(
                        MatchCardData(match.displayMatch(), profile, emptyList()),
                        onAvatarClick = if (profile.id != "masked") ({ onOpenProfile(match.other.id) }) else null
                    ) { onOpenChat(match.id) }
                }
            } else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (!state.loading && state.incoming.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    EmptyState(Icons.Rounded.Favorite, "Chưa ai thích bạn", "Người thích bạn mà bạn chưa thích lại sẽ hiện ở đây.")
                }
                gridItems(state.incoming, key = { it.id }) { profile ->
                    // Khung đỏ: người đã thích mình, chỉ cần mình thích lại là thành match.
                    IncomingLikeCard(
                        IncomingLikeData(profile.displayProfile(), profile.compatibility ?: 0),
                        modifier = Modifier.fillMaxWidth()
                            .border(2.dp, PtitRed, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    ) { onOpenProfile(profile.id) }
                }
            }
        }
    }
}

@Composable
fun BackendProfileDetailScreen(identity: PresentationIdentity, profileId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "detail:$identity:$profileId") { BackendDetailViewModel(container, identity, profileId) }
    val state by vm.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    Column(Modifier.fillMaxSize()) {
        FwpTopBar(state.profile?.displayProfile()?.nickname ?: "Hồ sơ", onBack = onBack, actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Tùy chọn") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Chặn người dùng", color = PtitRed) }, enabled = !state.busy && state.profile != null,
                        onClick = { menu = false; vm.block() })
                }
            }
        })
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)); TextButton(onClick = vm::refresh) { Text("Thử lại") } }
        val dto = state.profile
        if (dto != null) {
            Box(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                // Cùng một thẻ với Hôm nay: ảnh + câu trả lời xen kẽ, 3 nút nổi.
                SwipeCard(
                    ranked = RankedProfile(dto.displayProfile(), dto.compatibility ?: 0, dto.rankingReasons.orEmpty(), "FwP"),
                    me = null, busy = state.busy, modifier = Modifier.fillMaxSize(),
                    actionsEnabled = !state.busy && state.match == null,
                    onLike = { vm.swipe(SwipeAction.LIKE) }, onPass = { vm.swipe(SwipeAction.PASS) }, onSuperLike = { vm.swipe(SwipeAction.SUPER_LIKE) }
                )
            }
            state.notice?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            state.match?.let { match ->
                Button(onClick = { onOpenChat(match.id) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Nhắn tin") }
            }
        }
    }
}
