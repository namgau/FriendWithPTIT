package vn.ptit.fwp.feature.chats

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.HealthPill
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SearchField
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import vn.ptit.fwp.core.util.timeAgo

@Composable
fun ChatsScreen(onOpenChat: (String) -> Unit, onOpenMatches: () -> Unit) {
    val container = appContainer()
    val viewModel = fwpViewModel { ChatsViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Trò chuyện", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (state.unreadTotal > 0) "${state.unreadTotal} tin nhắn chưa đọc" else "Không có tin nhắn nào đang chờ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.unreadTotal > 0) {
                Surface(shape = CircleShape, color = PtitRed) {
                    Text(
                        state.unreadTotal.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = "Tìm cuộc trò chuyện…"
            )
        }
        Spacer(Modifier.height(10.dp))

        if (state.entries.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Rounded.ChatBubbleOutline,
                title = if (query.isBlank()) "Chưa có cuộc trò chuyện nào" else "Không tìm thấy cuộc trò chuyện",
                subtitle = if (query.isBlank()) {
                    "Kết nối với ai đó trong tab Match để bắt đầu một cuộc trò chuyện ý nghĩa."
                } else {
                    "Thử tìm bằng nickname khác hoặc xóa từ khóa."
                },
                actionLabel = if (query.isBlank()) "Mở Match" else "Xóa tìm kiếm",
                onAction = {
                    if (query.isBlank()) onOpenMatches() else viewModel.setQuery("")
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.entries, key = { it.match.id }) { entry ->
                    ChatEntryRow(entry) { onOpenChat(entry.match.id) }
                }
            }
        }
    }
}

@Composable
private fun ChatEntryRow(entry: ChatListEntry, onClick: () -> Unit) {
    val unread = entry.match.unreadCount > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (unread) PtitRedSoft.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PtitRedSoft)
            ) {
                if (entry.profile != null) {
                    ProfilePhoto(
                        profile = entry.profile,
                        modifier = Modifier.fillMaxSize(),
                        blurLevel = 0.9f,
                        shape = CircleShape,
                        showAnonymousHint = false,
                        showAnonBadge = false
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Groups, null, tint = PtitRed, modifier = Modifier.size(25.dp))
                    }
                }
                if (unread) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(PtitRed)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    HealthPill(entry.health)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        entry.match.lastMessage != null -> entry.match.lastMessage
                        entry.match.isGroup -> "Phòng Double Date đã sẵn sàng"
                        else -> "Hãy gửi lời chào đầu tiên"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entry.compatibility}% tương thích",
                        style = MaterialTheme.typography.labelSmall,
                        color = PtitRed
                    )
                    if (entry.match.lastMessage == null) {
                        Text(" · Mới match", style = MaterialTheme.typography.labelSmall, color = PtitGold)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    timeAgo(entry.match.lastMessageAt ?: entry.match.matchedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SqlSpacer()
                if (unread) {
                    Surface(shape = CircleShape, color = PtitRed) {
                        Text(
                            entry.match.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlSpacer() {
    Spacer(Modifier.height(8.dp))
}
