package vn.ptit.fwp.feature.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.ptit.fwp.core.ai.CompatibilityEngine
import vn.ptit.fwp.core.design.AxisBar
import vn.ptit.fwp.core.design.CompatibilityOrb
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.HealthBanner
import vn.ptit.fwp.core.design.HealthPill
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.TypingDots
import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatClock
import vn.ptit.fwp.core.util.formatDate
import vn.ptit.fwp.core.util.fwpViewModel
import kotlinx.coroutines.launch

private enum class ChatSheet { REVEAL, HEALTH, ASSISTANT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(matchId: String, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel = fwpViewModel { ChatViewModel(container, matchId) }
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by rememberSaveable(matchId) { mutableStateOf("") }
    var sheet by remember { mutableStateOf<ChatSheet?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var showUnmatch by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.sendImage(uri.toString())
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(finished) {
        if (finished) onBack()
    }
    LaunchedEffect(state.messages.size, state.typing) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex + 2)
        }
    }

    val match = state.match
    val profile = state.profile

    if (match == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    Text("Cuộc trò chuyện này không còn tồn tại.", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onBack) { Text("Quay lại") }
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Quay lại")
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PtitRedSoft)
                ) {
                    if (profile != null) {
                        ProfilePhoto(
                            profile = profile,
                            modifier = Modifier.fillMaxSize(),
                            blurLevel = 0.9f,
                            shape = CircleShape,
                            showAnonymousHint = false,
                            showAnonBadge = false
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Groups, null, tint = PtitRed, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (match.isGroup) match.groupTitle ?: "Double Date" else profile?.nickname ?: "FwP",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.health != null) {
                            HealthPill(state.health!!.level)
                        } else {
                            Text(
                                if (match.isGroup) "Trò chuyện nhóm" else "Ẩn danh · đang tìm hiểu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.typing) {
                            Text(
                                " · đang nhập…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                IconButton(onClick = { sheet = ChatSheet.REVEAL }) {
                    Icon(Icons.Rounded.Shield, contentDescription = "Quyền riêng tư", tint = PtitRed)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Tùy chọn")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Hủy match") },
                            leadingIcon = { Icon(Icons.Rounded.HeartBroken, null) },
                            onClick = { showMenu = false; showUnmatch = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Chặn") },
                            leadingIcon = { Icon(Icons.Rounded.Block, null) },
                            onClick = { showMenu = false; showBlock = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Báo cáo") },
                            leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                            onClick = { showMenu = false; showReport = true }
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item {
                    CompatibilityCard(
                        state = state,
                        onOpenDetails = { sheet = ChatSheet.REVEAL },
                        onAnalyze = viewModel::analyzeAgain
                    )
                }
                if (state.health != null) {
                    item {
                        HealthBanner(
                            report = state.health!!,
                            onSuggest = { sheet = ChatSheet.HEALTH },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (!state.aiEnabled) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Phân tích hội thoại đang tắt. FwP không phân tích nội dung tin nhắn; ảnh sẽ tiếp tục được làm mờ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                if (state.openingMoves.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PtitGold.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF7A5C00), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gợi ý mở lời", style = MaterialTheme.typography.titleSmall, color = Color(0xFF6D5100))
                                }
                                Spacer(Modifier.height(7.dp))
                                state.openingMoves.forEach { move ->
                                    Text(
                                        move,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                input = move
                                                sheet = null
                                            }
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(state.messages, key = { _, item -> item.id }) { index, item ->
                    val previous = state.messages.getOrNull(index - 1)
                    if (previous == null || formatDate(previous.createdAt) != formatDate(item.createdAt)) {
                        DateDivider(item.createdAt)
                    }
                    MessageBubble(item)
                }
                if (state.typing) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                TypingDots(Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            Composer(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
                onAttach = { imagePicker.launch(arrayOf("image/*")) },
                onAssistant = { sheet = ChatSheet.ASSISTANT }
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
        )
    }

    if (sheet == ChatSheet.REVEAL) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            RevealSheet(state, onToggle = viewModel::setRevealOptIn)
        }
    }
    if (sheet == ChatSheet.HEALTH) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            val health = state.health
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Gợi ý cho cuộc trò chuyện", style = MaterialTheme.typography.headlineSmall)
                if (health != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sức khỏe hội thoại: ${health.level.label} · ${health.score}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    health.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Bạn có thể thử", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    health.suggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    input = suggestion
                                    sheet = null
                                }
                        ) {
                            Text(
                                suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(11.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Nguồn: ${health.source}. Đây là gợi ý, không phải đánh giá con người.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (sheet == ChatSheet.ASSISTANT) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Trợ lý mở lời", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Những câu gợi ý dựa trên sở thích chung và nhịp trò chuyện. Chạm để đưa vào ô nhắn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                (state.openingMoves + state.suggestions).distinct().forEach { move ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                input = move
                                sheet = null
                            }
                    ) {
                        Text(move, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (showReport) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Báo cáo cuộc trò chuyện") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Cuộc trò chuyện sẽ bị ẩn và FwP sẽ xem xét báo cáo của bạn.")
                    listOf("Quấy rối hoặc xúc phạm", "Hồ sơ giả mạo", "Nội dung không phù hợp", "Spam hoặc quảng cáo").forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (reason == item) PtitRedSoft else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reason = item }
                        ) {
                            Text(item, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        showReport = false
                        viewModel.report(reason)
                    }
                ) { Text("Gửi báo cáo", color = PtitRed) }
            },
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("Hủy") } }
        )
    }
    if (showBlock) {
        AlertDialog(
            onDismissRequest = { showBlock = false },
            title = { Text("Chặn ${profile?.nickname ?: "người này"}?") },
            text = { Text("Họ sẽ không thể liên hệ với bạn nữa và cuộc trò chuyện này sẽ bị xóa.") },
            confirmButton = {
                TextButton(onClick = { showBlock = false; viewModel.block() }) { Text("Chặn", color = PtitRed) }
            },
            dismissButton = { TextButton(onClick = { showBlock = false }) { Text("Hủy") } }
        )
    }
    if (showUnmatch) {
        AlertDialog(
            onDismissRequest = { showUnmatch = false },
            title = { Text("Hủy match?") },
            text = { Text("Toàn bộ tin nhắn trong cuộc trò chuyện này sẽ bị xóa. Bạn không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = { showUnmatch = false; viewModel.unmatch() }) { Text("Hủy match", color = PtitRed) }
            },
            dismissButton = { TextButton(onClick = { showUnmatch = false }) { Text("Giữ lại") } }
        )
    }
}

@Composable
private fun CompatibilityCard(
    state: ChatUiState,
    onOpenDetails: () -> Unit,
    onAnalyze: () -> Unit
) {
    val compatibility = state.compatibility ?: return
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(PtitRed, Color(0xFF8E1B1B)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${compatibility.score}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text("tương thích", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        compatibility.revealEligible -> "Ảnh đã mở · hai bạn đã sẵn sàng"
                        state.match?.revealOptInMe == true -> "Bạn đã đồng ý mở ảnh"
                        else -> "Ảnh đang được ẩn danh"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${compatibility.messageCount}/${CompatibilityEngine.MIN_MESSAGES_TO_REVEAL} tin nhắn · cần từ ${CompatibilityEngine.REVEAL_SCORE} điểm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (compatibility.revealEligible) Color(0xFFE8F5E9) else PtitGold.copy(alpha = 0.16f),
                        modifier = Modifier.clickable(onClick = onOpenDetails)
                    ) {
                        Text(
                            if (compatibility.revealEligible) "Đã mở ảnh" else "Mở khóa ảnh",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (compatibility.revealEligible) Color(0xFF2E7D32) else Color(0xFF6D5100),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                    if (state.aiConfigured) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable(enabled = !state.analyzing, onClick = onAnalyze)
                        ) {
                            Text(
                                if (state.analyzing) "Đang phân tích" else "AI phân tích lại",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateDivider(timestamp: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                formatDate(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.type == MessageType.SYSTEM) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PtitGold.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isMine) 18.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 18.dp
                ),
                color = if (message.isMine) PtitRed else MaterialTheme.colorScheme.surface,
                border = if (message.isMine) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                if (message.type == MessageType.IMAGE) {
                    AsyncImage(
                        model = message.text,
                        contentDescription = "Ảnh đã gửi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            Text(
                formatClock(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onAssistant: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Gửi ảnh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAssistant) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = "Gợi ý mở lời", tint = PtitGold)
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f)
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            "Nhắn gì đó chân thành…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (value.isBlank()) MaterialTheme.colorScheme.surfaceVariant else PtitRed,
                modifier = Modifier
                    .size(46.dp)
                    .clickable(enabled = value.isNotBlank(), onClick = onSend)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "Gửi",
                        tint = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RevealSheet(state: ChatUiState, onToggle: (Boolean) -> Unit) {
    val compatibility = state.compatibility
    val profile = state.profile
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ẩn danh & mở ảnh", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        if (profile != null) {
            Box(
                Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(30.dp))
            ) {
                ProfilePhoto(
                    profile = profile,
                    modifier = Modifier.fillMaxSize(),
                    blurLevel = compatibility?.blurLevel ?: 0.9f,
                    revealed = compatibility?.revealEligible == true,
                    shape = RoundedCornerShape(30.dp),
                    showAnonymousHint = false
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            if (compatibility?.revealEligible == true) {
                "Hai bạn đã đồng ý và đủ điều kiện. Ảnh đã được mở."
            } else {
                "Ảnh sẽ rõ dần khi hai bạn trò chuyện đủ nhiều và cùng đồng ý."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tôi đồng ý chia sẻ ảnh", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Chỉ mở khi cả hai đồng ý, đủ ${CompatibilityEngine.MIN_MESSAGES_TO_REVEAL} tin nhắn và đạt ${CompatibilityEngine.REVEAL_SCORE} điểm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.match?.revealOptInMe == true,
                        onCheckedChange = onToggle
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge("Bạn", state.match?.revealOptInMe == true)
                    StatusBadge("Đối phương", state.match?.revealOptInThem == true)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tiến độ: ${compatibility?.messageCount ?: 0}/${CompatibilityEngine.MIN_MESSAGES_TO_REVEAL} tin nhắn · ${compatibility?.score ?: 0}/${CompatibilityEngine.REVEAL_SCORE} điểm tương thích",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nguồn điểm: ${compatibility?.source ?: "Quy tắc FwP (fallback)"}. Trong demo, dữ liệu là mô phỏng và ảnh chỉ mờ/không mờ cục bộ.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AxisBar("Cá tính", compatibility?.personality ?: 0, PtitRed, Modifier.weight(1f))
            AxisBar("Cảm xúc", compatibility?.emotion ?: 0, PtitGold, Modifier.weight(1f))
            AxisBar("Sở thích", compatibility?.interest ?: 0, Color(0xFF1565C0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusBadge(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "$label: ${if (active) "Đã đồng ý" else "Chưa"}" ,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
