package vn.ptit.fwp.feature.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun BackendDiscoverScreen(identity: PresentationIdentity, onOpenChat: (String) -> Unit, onOpenProfile: (String) -> Unit, onNotifications: () -> Unit = {}) {
    val container = appContainer()
    val vm = fwpViewModel(key = "discover:$identity") { BackendDiscoverViewModel(container, identity) }
    val state by vm.state.collectAsState()
    var filters by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            FwpLogo(size = 38.dp, withWordmark = true, modifier = Modifier.weight(1f))
            Text("BACKEND", style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = onNotifications) { Icon(Icons.Rounded.Notifications, "Thông báo") }
            IconButton(onClick = { vm.refresh() }, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Làm mới") }
            IconButton(onClick = { filters = true }, enabled = !state.busy) { Icon(Icons.Rounded.FilterList, "Bộ lọc") }
        }
        Text("Khám phá hôm nay", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text("Hồ sơ và lượt thích từ server", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        Box(Modifier.weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
            val top = state.profiles.firstOrNull()
            when {
                top != null -> key(top.id) { SwipeCard(
                    ranked = RankedProfile(top.displayProfile(), top.compatibility ?: 0, listOf("Nguồn: Backend"), "Backend"),
                    me = null, busy = state.busy || state.loading, modifier = Modifier.fillMaxSize(),
                    onLike = { vm.swipe(SwipeAction.LIKE) }, onPass = { vm.swipe(SwipeAction.PASS) }, onSuperLike = { vm.swipe(SwipeAction.SUPER_LIKE) }
                ) }
                state.loading -> CircularProgressIndicator()
                state.offset < state.total -> TextButton(onClick = vm::more) { Text("Tải thêm hồ sơ") }
                else -> EmptyState(Icons.Rounded.LocalFireDepartment, "Tạm hết gợi ý", "Đổi bộ lọc hoặc làm mới để kiểm tra gợi ý mới.", actionLabel = "Làm mới", onAction = { vm.refresh() })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { vm.swipe(SwipeAction.PASS) }, enabled = state.profiles.isNotEmpty() && !state.busy && !state.loading) { Icon(Icons.Rounded.Close, "Bỏ qua", tint = PtitRed) }
            IconButton(onClick = { vm.swipe(SwipeAction.SUPER_LIKE) }, enabled = state.profiles.isNotEmpty() && !state.busy && !state.loading) { Icon(Icons.Rounded.Star, "Super Like", tint = PtitGold) }
            IconButton(onClick = { vm.swipe(SwipeAction.LIKE) }, enabled = state.profiles.isNotEmpty() && !state.busy && !state.loading) { Icon(Icons.Rounded.Favorite, "Thích", tint = PtitRed) }
            state.profiles.firstOrNull()?.let { profile -> TextButton(onClick = { onOpenProfile(profile.id) }) { Text("Hồ sơ") } }
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
        FwpTopBar("Thông báo", subtitle = "${state.unread} chưa đọc · Backend", onBack = onBack, actions = {
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
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (chats) "Chat" else "Match", style = MaterialTheme.typography.headlineSmall)
                    Text("${state.matches.size} kết nối · Backend", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Làm mới") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
        if (!chats && state.incoming.isNotEmpty()) item {
            SectionHeader("Đang thích bạn", modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                items(state.incoming, key = { it.id }) { profile -> IncomingLikeCard(IncomingLikeData(profile.displayProfile(), profile.compatibility ?: 0)) { onOpenProfile(profile.id) } }
            }
        }
        if (!state.loading && state.matches.isEmpty()) item { EmptyState(Icons.Rounded.Favorite, "Chưa có match", "Khi hai người cùng thích nhau, server sẽ mở cuộc trò chuyện.") }
        items(state.matches, key = { it.id }) { match ->
            MatchRow(MatchCardData(match.displayMatch(), match.displayProfile(), emptyList())) { onOpenChat(match.id) }
        }
    }
}

@Composable
fun BackendProfileDetailScreen(identity: PresentationIdentity, profileId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "detail:$identity:$profileId") { BackendDetailViewModel(container, identity, profileId) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    Column(Modifier.fillMaxSize()) {
        FwpTopBar("Hồ sơ", subtitle = "Backend", onBack = onBack)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)); TextButton(onClick = vm::refresh) { Text("Thử lại") } }
        val dto = state.profile
        if (dto != null) {
            val profile = dto.displayProfile()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                ProfilePhoto(profile, modifier = Modifier.fillMaxWidth().height(330.dp), blurLevel = 0.86f)
                Text("${profile.nickname} · ${profile.age}", style = MaterialTheme.typography.headlineMedium)
                Text(profile.bio, modifier = Modifier.padding(vertical = 12.dp))
                InfoRow(Icons.Rounded.School, "Trường", profile.school)
                InfoRow(Icons.Rounded.School, "Khoa", profile.faculty)
                InfoRow(Icons.Rounded.School, "Ngành", profile.major)
                if (profile.showGpa) GpaBadge(profile.gpaTier)
                Text(profile.interests.joinToString(" · "), modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = vm::block, enabled = !state.busy) { Text("Chặn người dùng", color = PtitRed) }
                Text("Báo cáo kết nối được thực hiện trong cuộc trò chuyện đã match.", style = MaterialTheme.typography.bodySmall)
            }
            state.notice?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (state.match != null) Button(onClick = { onOpenChat(state.match!!.id) }) { Text("Mở trò chuyện") }
                else {
                    TextButton(onClick = { vm.swipe(SwipeAction.PASS) }, enabled = !state.busy) { Text("Bỏ qua") }
                    TextButton(onClick = { vm.swipe(SwipeAction.SUPER_LIKE) }, enabled = !state.busy) { Text("Super Like") }
                    Button(onClick = { vm.swipe(SwipeAction.LIKE) }, enabled = !state.busy) { Text("Thích") }
                }
            }
        }
    }
}
