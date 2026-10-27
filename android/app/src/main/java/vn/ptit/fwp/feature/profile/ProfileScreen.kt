package vn.ptit.fwp.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.data.AppSettings
import vn.ptit.fwp.core.design.DemoPortrait
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.InfoRow
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SecondaryButton
import vn.ptit.fwp.core.design.SectionHeader
import vn.ptit.fwp.core.design.StatPill
import vn.ptit.fwp.core.design.VerifiedMark
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatGpa
import vn.ptit.fwp.core.util.fwpViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onOpenMatches: () -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { ProfileViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val me = state.me

    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(PtitRed, Color(0xFF8E1B1B), Color(0xFF5C1010))
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 14.dp)
                ) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Cài đặt", tint = Color.White)
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(PtitRedSoft)
                    ) {
                        if (!me.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = me.photoUrl,
                                contentDescription = "Ảnh đại diện",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DemoPortrait(me.avatarSeed, Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                me.nickname,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("${me.age}", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                            if (me.verified) {
                                Spacer(Modifier.width(4.dp))
                                VerifiedMark()
                            }
                        }
                        Text(
                            "${me.academicYear} · ${me.major}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
                                Text(
                                    me.mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(50), color = PtitGold) {
                                Text(
                                    "DEMO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4A3800),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .offsetProfileCard()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenMatches)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill(state.matchCount.toString(), "Match")
                    StatPill(state.activeChatCount.toString(), "Đang chat")
                    StatPill(state.incomingLikes.toString(), "Lượt thích")
                    StatPill(if (state.bestCompatibility > 0) "${state.bestCompatibility}%" else "—", "Hợp nhất")
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                SecondaryButton(
                    text = "Chỉnh sửa hồ sơ",
                    icon = Icons.Rounded.Edit,
                    onClick = onEditProfile
                )
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Thông tin học vấn")
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(Icons.Rounded.Verified, "Trường", me.school)
                        InfoRow(Icons.Rounded.Verified, "Cơ sở", me.campus)
                        InfoRow(Icons.Rounded.Verified, "Khoa", me.faculty)
                        InfoRow(Icons.Rounded.Verified, "Ngành", me.major)
                        InfoRow(
                            Icons.Rounded.Star,
                            "GPA",
                            if (me.showGpa) "${formatGpa(me.gpa)} / 4.00" else "Ẩn số cụ thể · Tầng ${me.gpaTier.label}"
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GpaBadge(me.gpaTier)
                    if (me.alumni) {
                        Surface(shape = RoundedCornerShape(8.dp), color = PtitRedSoft) {
                            Text(
                                "Cựu sinh viên",
                                style = MaterialTheme.typography.labelMedium,
                                color = PtitRed,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (me.showLocation) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                "Hiện khoảng cách",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Giới thiệu")
                Spacer(Modifier.height(8.dp))
                Text(
                    me.bio.ifBlank { "Bạn chưa viết lời giới thiệu." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                SectionHeader(title = "Sở thích", subtitle = "Dùng để tính tương thích và gợi ý mở lời.")
                Spacer(Modifier.height(9.dp))
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    me.interests.forEach { interest ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PtitRed.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PtitRed.copy(alpha = 0.18f))
                        ) {
                            Text(
                                interest,
                                style = MaterialTheme.typography.labelMedium,
                                color = PtitRed,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                SectionHeader(title = "Huy hiệu", subtitle = "Những cột mốc nhỏ trên hành trình kết nối.")
                Spacer(Modifier.height(10.dp))
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(state.achievements) { achievement ->
                    AchievementCard(achievement)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(22.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PtitGold.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PtitGold.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = Color(0xFF7A5C00), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Chế độ demo đang bật", style = MaterialTheme.typography.titleSmall, color = Color(0xFF6D5100))
                            Text(
                                "Dữ liệu hồ sơ, tin nhắn và điểm số là mô phỏng cục bộ. ${if (settings.aiAnalysisEnabled) "Phân tích hội thoại đang bật." else "Phân tích hội thoại đang tắt."}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (me.verified) "Email trường đã xác thực · ${settings.email}" else "Email: ${settings.email.ifBlank { "chưa cập nhật" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (achievement.unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (achievement.unlocked) PtitGold.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.width(158.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (achievement.unlocked) PtitGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (achievement.unlocked) Icons.Rounded.Star else Icons.Rounded.Lock,
                    null,
                    tint = if (achievement.unlocked) Color(0xFFB88600) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                achievement.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                achievement.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.offsetProfileCard(): Modifier = this.padding(top = 0.dp)
