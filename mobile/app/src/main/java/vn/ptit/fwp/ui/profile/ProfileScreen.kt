package vn.ptit.fwp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.ServiceLocator
import vn.ptit.fwp.data.model.PublicProfile
import vn.ptit.fwp.data.model.gpaTierLabel
import vn.ptit.fwp.data.model.graduationLabel
import vn.ptit.fwp.ui.components.BlurredAvatar
import vn.ptit.fwp.ui.components.TagRow
import vn.ptit.fwp.ui.theme.Spacing

@Composable
fun ProfileScreen() {
    val settings = ServiceLocator.settings
    val scope = rememberCoroutineScope()
    var useServer by remember { mutableStateOf(settings.useServer) }
    var aiSuggestions by remember { mutableStateOf(settings.aiSuggestions) }
    var safetyNudge by remember { mutableStateOf(settings.safetyNudge) }
    var me by remember { mutableStateOf<PublicProfile?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(useServer) {
        me = runCatching { ServiceLocator.repository().me() }
            .onFailure { status = "Không kết nối được server: ${it.message}" }
            .getOrNull()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        me?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                BlurredAvatar(p.userId, p.nickname, blurLevel = 0f, size = 72.dp)
                Column {
                    Text("${p.nickname}, ${p.age}", style = MaterialTheme.typography.titleLarge)
                    Text("${p.schoolName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${p.facultyName} · ${graduationLabel(p.graduationStatus)} · ${gpaTierLabel(p.gpaTier)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(p.bio, style = MaterialTheme.typography.bodyLarge)
            TagRow(p.interestTags, highlighted = emptySet())
        }

        HorizontalDivider()
        Text("Cài đặt", style = MaterialTheme.typography.titleMedium)
        SettingRow("Gợi ý cứu hội thoại", "AI gợi ý riêng cho bạn khi cuộc trò chuyện chững lại", aiSuggestions) {
            aiSuggestions = it; settings.aiSuggestions = it
        }
        SettingRow("Nhắc trước khi gửi", "Hỏi lại khi tin nhắn có thể gây khó chịu", safetyNudge) {
            safetyNudge = it; settings.safetyNudge = it
        }

        HorizontalDivider()
        Text("Dành cho nhóm phát triển", style = MaterialTheme.typography.titleMedium)
        SettingRow("Dùng server AI", "Tắt = dữ liệu giả trong máy ; bật = ${BuildConfig.AI_BASE_URL}", useServer) {
            useServer = it; settings.useServer = it; status = null
        }
        if (useServer) {
            OutlinedButton(onClick = {
                scope.launch {
                    status = runCatching { ServiceLocator.repository().reset() }
                        .fold({ "Đã reset dữ liệu demo trên server" }, { "Lỗi: ${it.message}" })
                }
            }) { Text("Reset dữ liệu demo") }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null) // cả dòng đã bấm được
    }
}
