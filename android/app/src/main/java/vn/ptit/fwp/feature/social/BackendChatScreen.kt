package vn.ptit.fwp.feature.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import vn.ptit.fwp.core.data.*
import vn.ptit.fwp.core.design.*
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import vn.ptit.fwp.feature.chat.Composer
import vn.ptit.fwp.feature.chat.MessageBubble

@Composable
fun BackendChatScreen(identity: PresentationIdentity, matchId: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "chat:$identity:$matchId") { BackendChatViewModel(container, identity, matchId) }
    val state by vm.state.collectAsState()
    var input by remember(identity, matchId) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(identity, matchId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh()
            while (true) { delay(8_000); vm.refresh() }
        }
    }
    val lastId = state.messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (lastId != null && list.layoutInfo.totalItemsCount > 0 && (list.firstVisibleItemIndex == 0 || state.messages.lastOrNull()?.isMine == true))
            list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        FwpTopBar(title = state.match?.displayProfile()?.nickname ?: "Trò chuyện", subtitle = if (state.match?.anonymous == true) "Ẩn danh · Backend" else "Backend",
            onBack = onBack, actions = {
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Làm mới") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Tùy chọn") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        for ((key, label) in listOf("unmatch" to "Hủy match", "block" to "Chặn", "report" to "Báo cáo"))
                            DropdownMenuItem(text = { Text(label) }, enabled = state.match != null && !state.busy && !state.unavailable,
                                onClick = { menu = false; confirm = key })
                    }
                }
            })
        if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        if (state.unavailable) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Cuộc trò chuyện không còn khả dụng.")
                    TextButton(onClick = onBack) { Text("Quay lại") }
                }
            }
        } else {
            val match = state.match
            if (match != null) Surface(Modifier.fillMaxWidth().padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (match.anonymous) "Danh tính đang được ẩn" else "${match.displayProfile().nickname} · ${match.compatibilityScore}% tương thích", style = MaterialTheme.typography.titleSmall)
                    Text("${match.messageCount} tin nhắn · ${if (match.revealEligible) "Server xác nhận đủ điều kiện reveal" else "Chưa được server mở reveal"}", style = MaterialTheme.typography.bodySmall)
                    Text("Đối phương: ${if (match.revealOptInThem) "đã đồng ý" else "chưa đồng ý"}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Đồng ý chia sẻ danh tính", modifier = Modifier.weight(1f))
                        Switch(match.revealOptInMe, onCheckedChange = vm::reveal, enabled = !state.busy)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (state.nextBefore != null) item { TextButton(onClick = vm::older, enabled = !state.paging && !state.refreshing) { Text(if (state.paging) "Đang tải…" else "Tải tin nhắn cũ hơn") } }
                items(state.messages, key = { it.id }) { message -> MessageBubble(message.displayMessage()) }
                items(state.pending, key = { "pending:${it.request.clientMessageId}" }) { pending ->
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = PtitRedSoft) {
                        Column(Modifier.padding(12.dp)) {
                            Text(pending.request.text)
                            Text(when (pending.status) { SendStatus.SENDING -> "Đang gửi…"; SendStatus.RETRY -> "Chưa xác nhận gửi"; SendStatus.CONFLICT -> "Xung đột UUID — không gửi lại tự động"; SendStatus.REJECTED -> "Server từ chối tin nhắn"; SendStatus.CONFIRMED -> "Đã gửi · đang đồng bộ lịch sử" }, style = MaterialTheme.typography.labelSmall)
                            if (pending.status == SendStatus.RETRY) TextButton(onClick = { vm.retry(pending.request.clientMessageId) }) { Text("Thử lại cùng tin nhắn") }
                        }
                    }
                }
            }
            Composer(input, { input = it }, onSend = { if (vm.send(input)) input = "" }, onAttach = {}, onAssistant = {}, backend = true, enabled = match != null)
        }
    }
    confirm?.let { action -> AlertDialog(onDismissRequest = { confirm = null },
        title = { Text(when (action) { "block" -> "Chặn người này?"; "report" -> "Báo cáo kết nối"; else -> "Hủy match?" }) },
        text = {
            if (action == "report") OutlinedTextField(report, { report = it.take(64) }, label = { Text("Lý do báo cáo") })
            else Text("Kết nối sẽ không còn khả dụng. Thao tác được xử lý trên server.")
        }, confirmButton = { TextButton(enabled = action != "report" || report.isNotBlank(), onClick = {
            confirm = null
            when (action) { "block" -> vm.block(); "report" -> vm.report(report.trim()); else -> vm.unmatch() }
        }) { Text("Xác nhận", color = PtitRed) } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Hủy") } }) }
}
