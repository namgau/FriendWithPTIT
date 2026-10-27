package vn.ptit.fwp.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.model.FwpNotification
import vn.ptit.fwp.core.model.NotificationType
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import vn.ptit.fwp.core.util.timeAgo

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = fwpViewModel { NotificationsViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        FwpTopBar(
            title = "Thông báo",
            subtitle = if (state.unread > 0) "${state.unread} chưa đọc" else "Bạn đã xem hết thông báo",
            onBack = onBack,
            actions = {
                if (state.unread > 0) {
                    TextButton(onClick = viewModel::markAllRead) {
                        Text("Đọc tất cả", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        )
        if (state.notifications.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Rounded.NotificationsNone,
                title = "Chưa có thông báo",
                subtitle = "Match mới, tin nhắn và nhắc nhở an toàn sẽ xuất hiện ở đây."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.notifications, key = { it.id }) { item ->
                    NotificationRow(item)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: FwpNotification) {
    val tint = when (item.type) {
        NotificationType.MATCH -> PtitRed
        NotificationType.MESSAGE -> Color(0xFF1565C0)
        NotificationType.SAFETY -> PtitGold
        NotificationType.ACHIEVEMENT -> Color(0xFFB88600)
        NotificationType.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (item.type) {
        NotificationType.MATCH -> Icons.Rounded.Favorite
        NotificationType.MESSAGE -> Icons.Rounded.Forum
        NotificationType.SAFETY -> Icons.Rounded.Shield
        NotificationType.ACHIEVEMENT -> Icons.Rounded.EmojiEvents
        NotificationType.SYSTEM -> Icons.Rounded.Verified
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (item.read) MaterialTheme.colorScheme.surface else PtitRedSoft.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (!item.read) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(PtitRed)
                        )
                    }
                }
                Spacer(Modifier.padding(top = 2.dp))
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(top = 5.dp))
                Text(
                    timeAgo(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
