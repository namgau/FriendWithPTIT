package vn.ptit.fwp.feature.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.data.remote.CommunityDto
import vn.ptit.fwp.core.data.remote.CommunityRequestDto
import vn.ptit.fwp.core.design.AnimalAvatar
import vn.ptit.fwp.core.design.PtitRed

/** Danh sách mọi cộng đồng: tham gia / chờ duyệt / rời, tạo mới, chủ cộng đồng mở yêu cầu chờ duyệt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunitiesSheet(
    communities: List<CommunityDto>,
    onDismiss: () -> Unit,
    onToggleJoin: (String) -> Unit,
    onCreate: (name: String, description: String, requiresApproval: Boolean, hidden: Boolean) -> Unit,
    onJoinByCode: (String) -> Unit,
    onOpenRequests: (CommunityDto) -> Unit,
    onOpenFeed: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cộng đồng", style = MaterialTheme.typography.headlineSmall)
                    Text("Tham gia để đăng bài vào cộng đồng ; bài cũng có thể không thuộc cộng đồng nào.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (creating) {
                CreateCommunityForm(onCancel = { creating = false }, onCreate = { n, d, a, h -> onCreate(n, d, a, h); creating = false })
            } else {
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Tạo cộng đồng") }
                var code by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(code, { code = it.take(8).uppercase() }, singleLine = true,
                        placeholder = { Text("Mã mời cộng đồng ẩn") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onJoinByCode(code); code = "" }, enabled = code.length == 8) { Text("Vào") }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(communities, key = { it.id }) { c ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        buildString {
                                            append("${c.memberCount} thành viên")
                                            if (c.hidden) append(" · ẩn")
                                            if (c.requiresApproval) append(" · cần duyệt")
                                            if (c.isOwner) append(" · bạn là chủ")
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                when {
                                    c.isOwner -> OutlinedButton(onClick = { onOpenRequests(c) }) {
                                        Text(if (c.pendingCount > 0) "Duyệt (${c.pendingCount})" else "Duyệt")
                                    }
                                    c.joined -> OutlinedButton(onClick = { onToggleJoin(c.id) }) { Text("Rời") }
                                    c.pending -> OutlinedButton(onClick = { onToggleJoin(c.id) }) { Text("Hủy yêu cầu") }
                                    else -> Button(onClick = { onToggleJoin(c.id) }) { Text(if (c.requiresApproval) "Xin vào" else "Tham gia") }
                                }
                            }
                            if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                            c.inviteCode?.let { Text("Mã mời: $it", style = MaterialTheme.typography.labelLarge, color = PtitRed,
                                modifier = Modifier.padding(top = 4.dp)) }
                            if (c.pending) Text("Đang chờ chủ cộng đồng duyệt", style = MaterialTheme.typography.labelMedium, color = PtitRed,
                                modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { onOpenFeed(c.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Xem bài trong cộng đồng")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCommunityForm(onCancel: () -> Unit, onCreate: (String, String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Tạo cộng đồng mới", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Tên cộng đồng") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(200) }, label = { Text("Mô tả ngắn") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Duyệt thành viên", style = MaterialTheme.typography.bodyMedium)
                    Text("Bật: người xin vào phải chờ bạn duyệt", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(approval, onCheckedChange = { approval = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Ẩn cộng đồng", style = MaterialTheme.typography.bodyMedium)
                    Text("Không hiện công khai ; chỉ vào được bằng mã mời", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(hidden, onCheckedChange = { hidden = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Hủy") }
                Button(onClick = { onCreate(name, description, approval, hidden) }, enabled = name.trim().length >= 3) { Text("Tạo") }
            }
        }
    }
}

/** Yêu cầu tham gia chờ duyệt (chỉ chủ cộng đồng). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityRequestsSheet(
    community: CommunityDto,
    requests: List<CommunityRequestDto>,
    onDismiss: () -> Unit,
    onDecide: (userId: String, approve: Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Duyệt thành viên · ${community.name}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (requests.isEmpty()) Text("Chưa có ai xin vào.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            requests.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    AnimalAvatar(r.avatarSeed, Modifier.size(40.dp).clip(CircleShape))
                    Text(r.nickname, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(onClick = { onDecide(r.userId, false) }) { Text("Từ chối") }
                    Button(onClick = { onDecide(r.userId, true) }) { Text("Duyệt") }
                }
            }
        }
    }
}
