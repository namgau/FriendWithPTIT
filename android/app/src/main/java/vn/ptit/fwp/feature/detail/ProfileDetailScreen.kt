package vn.ptit.fwp.feature.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.ptit.fwp.core.design.CompatibilityOrb
import vn.ptit.fwp.core.design.DemoPortrait
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.MatchCelebration
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SectionHeader
import vn.ptit.fwp.core.design.VerifiedMark
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatDistance
import vn.ptit.fwp.core.util.fwpViewModel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileDetailScreen(
    profileId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { ProfileDetailViewModel(container, profileId) }
    val state by viewModel.uiState.collectAsState()
    val celebration by viewModel.celebration.collectAsState()
    val message by viewModel.message.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var showReasons by remember { mutableStateOf(false) }
    val profile = state.profile

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(finished) {
        if (finished) onBack()
    }

    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loading) {
                androidx.compose.material3.CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Không tìm thấy hồ sơ.", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onBack) { Text("Quay lại") }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FwpTopBar(
                title = profile.nickname,
                subtitle = if (profile.verified) "Đã xác thực email trường" else "Hồ sơ demo",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Tùy chọn khác")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Báo cáo hồ sơ") },
                                leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                                onClick = { showMenu = false; showReport = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Chặn người dùng") },
                                leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                onClick = { showMenu = false; showBlock = true }
                            )
                        }
                    }
                }
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(410.dp)
                        .clip(RoundedCornerShape(30.dp))
                ) {
                    ProfilePhoto(
                        profile = profile,
                        modifier = Modifier.fillMaxSize(),
                        blurLevel = 0.86f,
                        shape = RoundedCornerShape(30.dp),
                        showAnonymousHint = false
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.10f),
                                    0.55f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.82f)
                                )
                            )
                    )
                    CompatibilityOrb(
                        score = state.score,
                        personality = state.score,
                        emotion = (state.score - 5).coerceAtLeast(20),
                        interest = (state.score + 5).coerceAtMost(98),
                        orbSize = 96.dp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.nickname,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${profile.age}", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.82f))
                            if (profile.verified) {
                                Spacer(Modifier.width(6.dp))
                                VerifiedMark()
                            }
                        }
                        Text(
                            if (profile.alumni) "Cựu sinh viên · ${profile.academicYear}" else "${profile.academicYear} · ${profile.major}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            GpaBadge(profile.gpaTier, compact = true)
                            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
                                Text(
                                    profile.mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Icon(Icons.Rounded.LocationOn, null, tint = PtitGold, modifier = Modifier.size(13.dp))
                            Text(
                                formatDistance(profile.distanceKm),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Về ${profile.nickname}")
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.bio.ifBlank { "Hồ sơ này chưa có lời giới thiệu." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        DetailLine(Icons.Rounded.School, "Trường", profile.school)
                        DetailLine(Icons.Rounded.School, "Cơ sở", profile.campus)
                        DetailLine(Icons.Rounded.School, "Khoa", profile.faculty)
                        DetailLine(Icons.Rounded.School, "Ngành", profile.major)
                        DetailLine(Icons.Rounded.LocationOn, "Khoảng cách", "${formatDistance(profile.distanceKm)} · ước lượng")
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Sở thích")
                Spacer(Modifier.height(9.dp))
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    profile.interests.forEach { interest ->
                        FwpChip(
                            text = interest,
                            selected = interest in state.sharedInterests,
                            onClick = { }
                        )
                    }
                }
                if (state.sharedInterests.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = PtitGold.copy(alpha = 0.13f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Điểm chung giữa hai bạn", style = MaterialTheme.typography.titleSmall, color = Color(0xFF6D5100))
                            Text(
                                state.sharedInterests.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PtitRedSoft.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Ảnh và thông tin nhạy cảm vẫn được ẩn danh ở màn hình này. FwP chỉ mở ảnh khi cả hai đồng ý, đã trò chuyện đủ 8 tin nhắn và đạt điểm tương thích từ 72.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(Modifier.height(26.dp))
            }

            if (state.matchId == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionBubble(Icons.Rounded.Close, PtitRed, state.busy) { viewModel.pass() }
                    ActionBubble(Icons.Rounded.Star, Color(0xFF1565C0), state.busy) { viewModel.like(superLike = true) }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (state.isLiked) MaterialTheme.colorScheme.surfaceVariant else PtitRed,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable(enabled = !state.busy) {
                                if (state.isLiked) showReasons = true else viewModel.like()
                            }
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.Favorite,
                                null,
                                tint = if (state.isLiked) PtitRed else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isLiked) "Đã gửi lời mời" else "Gửi lời mời",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (state.isLiked) PtitRed else Color.White
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PtitRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp)
                        .clickable { onOpenChat(state.matchId!!) }
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ChatBubble, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Nhắn tin ngay", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp)
        )
        celebration?.let { data ->
            MatchCelebration(
                me = state.me,
                other = data.profile,
                onChat = {
                    viewModel.dismissCelebration()
                    onOpenChat(data.matchId)
                },
                onContinue = viewModel::dismissCelebration
            )
        }
    }

    if (showReport) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Báo cáo hồ sơ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Chọn lý do để FwP xem xét. Hồ sơ này sẽ bị ẩn ngay khỏi gợi ý của bạn.")
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
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("Hủy") }
            }
        )
    }

    if (showBlock) {
        AlertDialog(
            onDismissRequest = { showBlock = false },
            title = { Text("Chặn ${profile.nickname}?") },
            text = { Text("Bạn sẽ không thấy hồ sơ này nữa và cuộc trò chuyện hiện tại sẽ bị xóa.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlock = false
                    viewModel.block()
                }) { Text("Chặn", color = PtitRed) }
            },
            dismissButton = {
                TextButton(onClick = { showBlock = false }) { Text("Hủy") }
            }
        )
    }

    if (showReasons) {
        AlertDialog(
            onDismissRequest = { showReasons = false },
            title = { Text("Lời mời đã được gửi") },
            text = { Text("FwP sẽ thông báo khi ${profile.nickname} phản hồi. Bạn có thể hoàn tác lượt thích trong tab Khám phá hoặc hủy match nếu đã kết nối.") },
            confirmButton = {
                TextButton(onClick = { showReasons = false }) { Text("Đã hiểu") }
            }
        )
    }
}

@Composable
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    busy: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(56.dp)
            .clickable(enabled = !busy, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}
