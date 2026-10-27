package vn.ptit.fwp.feature.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SectionHeader
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import vn.ptit.fwp.core.util.timeAgo

@Composable
fun MatchesScreen(
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenDoubleDate: () -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { MatchesViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Match", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.matches.size} kết nối · ${state.incomingLikes.size} người đang chờ bạn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PtitRedSoft,
                    modifier = Modifier.clickable(onClick = onOpenDoubleDate)
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Groups, null, tint = PtitRed, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Double Date", style = MaterialTheme.typography.labelSmall, color = PtitRed)
                    }
                }
            }
        }

        if (state.incomingLikes.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Đang thích bạn",
                    subtitle = "Những người đã gửi tín hiệu tích cực. Bạn có thể mở hồ sơ để phản hồi.",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(state.incomingLikes, key = { it.profile.id }) { like ->
                        IncomingLikeCard(like) { onOpenProfile(like.profile.id) }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        item {
            SectionHeader(
                title = "Kết nối của bạn",
                subtitle = if (state.matches.isEmpty()) "Match đầu tiên đang ở rất gần." else "Ảnh vẫn ẩn danh cho đến khi cả hai sẵn sàng.",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))
        }

        if (state.matches.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Favorite,
                    title = "Chưa có match nào",
                    subtitle = "Hãy tiếp tục khám phá. Khi cả hai cùng thích nhau, FwP sẽ mở một cuộc trò chuyện riêng cho bạn.",
                    actionLabel = if (state.incomingLikes.isNotEmpty()) "Xem người đang thích bạn" else null,
                    onAction = if (state.incomingLikes.isNotEmpty()) {
                        { onOpenProfile(state.incomingLikes.first().profile.id) }
                    } else null
                )
            }
        } else {
            items(state.matches, key = { it.match.id }) { card ->
                MatchRow(card) {
                    if (card.match.isGroup) onOpenChat(card.match.id) else onOpenChat(card.match.id)
                }
            }
        }
    }
}

@Composable
private fun IncomingLikeCard(like: IncomingLikeData, onClick: () -> Unit) {
    val profile = like.profile
    Box(
        Modifier
            .width(150.dp)
            .aspectRatio(0.76f)
            .clip(RoundedCornerShape(22.dp))
            .background(PtitRedSoft)
            .clickable(onClick = onClick)
    ) {
        ProfilePhoto(
            profile = profile,
            modifier = Modifier.fillMaxSize(),
            blurLevel = 0.92f,
            shape = RoundedCornerShape(22.dp),
            showAnonymousHint = false
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.84f)
                    )
                )
        )
        Surface(
            shape = CircleShape,
            color = PtitRed,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                Icons.Rounded.Favorite,
                null,
                tint = Color.White,
                modifier = Modifier.padding(6.dp).size(14.dp)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(11.dp)
        ) {
            Text(
                profile.nickname,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${like.score}% tương thích",
                style = MaterialTheme.typography.labelSmall,
                color = PtitGold
            )
        }
    }
}

@Composable
private fun MatchRow(card: MatchCardData, onClick: () -> Unit) {
    val profile = card.profile
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PtitRedSoft)
            ) {
                if (profile != null) {
                    ProfilePhoto(
                        profile = profile,
                        modifier = Modifier.fillMaxSize(),
                        blurLevel = 0.85f,
                        shape = RoundedCornerShape(18.dp),
                        showAnonymousHint = false,
                        showAnonBadge = false
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Groups, null, tint = PtitRed, modifier = Modifier.size(28.dp))
                    }
                }
                if (card.match.isGroup) {
                    Surface(
                        shape = CircleShape,
                        color = PtitRed,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                    ) {
                        Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.padding(4.dp).size(12.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (card.match.isGroup) card.match.groupTitle ?: "Double Date" else profile?.nickname ?: "FwP",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(5.dp))
                    if (profile != null && !card.match.isGroup) {
                        GpaBadge(profile.gpaTier, compact = true)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        card.match.lastMessage != null -> card.match.lastMessage
                        card.match.isGroup -> "Phòng trò chuyện nhóm"
                        else -> "Hai bạn đã match · Hãy mở lời trước nhé"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.sharedInterests.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        card.sharedInterests.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    timeAgo(card.match.lastMessageAt ?: card.match.matchedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (card.match.unreadCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = CircleShape, color = PtitRed) {
                        Text(
                            card.match.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}
