package vn.ptit.fwp.feature.doubledate

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.PrimaryButton
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel

@Composable
fun DoubleDateScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { DoubleDateViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdMatchId) {
        val id = state.createdMatchId ?: return@LaunchedEffect
        onOpenChat(id)
    }

    Column(Modifier.fillMaxSize()) {
        FwpTopBar(
            title = "Double Date",
            subtitle = "Ghép hai match thành một phòng trò chuyện chung",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = PtitRedSoft.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(PtitRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Bốn người, một cuộc trò chuyện", style = MaterialTheme.typography.titleSmall, color = PtitRed)
                            Text(
                                "Chọn hai match mà bạn thấy hợp gu. FwP sẽ mở phòng chung để cả nhóm tự giới thiệu và chọn chủ đề.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Chọn 2 match (${state.selectedIds.size}/2)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Bạn chỉ có thể chọn hai người cho một phòng Double Date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.candidates.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Groups,
                        title = "Cần ít nhất hai match",
                        subtitle = "Hãy kết nối thêm vài người trong tab Match trước khi mở Double Date.",
                        actionLabel = "Quay lại",
                        onAction = onBack
                    )
                }
            } else {
                items(state.candidates, key = { it.match.id }) { candidate ->
                    val selected = candidate.match.id in state.selectedIds
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) PtitRedSoft.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 1.6.dp else 1.dp,
                            if (selected) PtitRed else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.toggle(candidate.match.id) }
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PtitRedSoft)
                            ) {
                                ProfilePhoto(
                                    profile = candidate.profile,
                                    modifier = Modifier.fillMaxSize(),
                                    blurLevel = 0.88f,
                                    shape = RoundedCornerShape(16.dp),
                                    showAnonymousHint = false,
                                    showAnonBadge = false
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(candidate.profile.nickname, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${candidate.profile.mode.label} · ${candidate.profile.campus.substringBefore(" – ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (candidate.sharedInterests.isNotEmpty()) {
                                    Text(
                                        "Chung: ${candidate.sharedInterests.take(2).joinToString(" · ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) PtitRed else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                if (state.selectedIds.size == 2) "Sẵn sàng mở phòng cho ${state.candidates.filter { it.match.id in state.selectedIds }.joinToString(" & ") { it.profile.nickname }}"
                else "Chọn thêm ${2 - state.selectedIds.size} người để tiếp tục",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PrimaryButton(
                text = "Tạo phòng Double Date",
                icon = Icons.Rounded.Groups,
                enabled = state.selectedIds.size == 2,
                loading = state.creating,
                onClick = viewModel::create
            )
        }
    }
}
