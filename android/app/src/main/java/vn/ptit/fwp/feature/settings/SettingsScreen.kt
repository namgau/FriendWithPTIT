package vn.ptit.fwp.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.core.data.ThemeMode
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SectionHeader
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onDeleted: () -> Unit
) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel = fwpViewModel { SettingsViewModel(container) }
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setLocationEnabled(granted)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotifications(granted)
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(loggedOut) {
        if (loggedOut) onLoggedOut()
    }
    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FwpTopBar(title = "Cài đặt", subtitle = "Tài khoản, riêng tư và trải nghiệm", onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                SettingsSectionLabel("Tài khoản")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Email trường", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            settings.email.ifBlank { "Chưa cập nhật" },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFE3F2FD)) {
                            Text(
                                if (settings.email.isNotBlank()) "Đã xác thực trong chế độ demo" else "Chưa xác thực",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1565C0),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Giao diện")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FwpChip(
                            text = mode.label,
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) }
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Quyền riêng tư & an toàn")
                SettingsToggle(
                    icon = Icons.Rounded.Science,
                    title = "Phân tích hội thoại",
                    subtitle = "Tính M3 và gợi ý mở lời. Tắt để FwP không phân tích nội dung tin nhắn.",
                    checked = settings.aiAnalysisEnabled,
                    onCheckedChange = viewModel::setAiAnalysis
                )
                SettingsToggle(
                    icon = Icons.Rounded.Visibility,
                    title = "Hiển thị hồ sơ",
                    subtitle = "Cho phép người khác thấy bạn trong gợi ý. Ảnh vẫn luôn được làm mờ.",
                    checked = settings.profileVisible,
                    onCheckedChange = viewModel::setProfileVisible
                )
                SettingsToggle(
                    icon = Icons.Rounded.Security,
                    title = "Hiển thị GPA",
                    subtitle = "Tắt để chỉ hiển thị tầng học lực thay vì con số cụ thể.",
                    checked = settings.showGpaPublic,
                    onCheckedChange = viewModel::setShowGpa
                )
                SettingsToggle(
                    icon = Icons.Rounded.LocationOn,
                    title = "Hiển thị khoảng cách",
                    subtitle = "Chỉ dùng vị trí ước lượng, không chia sẻ tọa độ chính xác.",
                    checked = settings.showLocationPublic,
                    onCheckedChange = viewModel::setShowLocation
                )
                SettingsToggle(
                    icon = Icons.Rounded.LocationOn,
                    title = "Dùng vị trí thiết bị",
                    subtitle = "Cần quyền vị trí để tính khoảng cách gần đúng giữa các campus.",
                    checked = settings.locationEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.setLocationEnabled(true) else {
                                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        } else {
                            viewModel.setLocationEnabled(false)
                        }
                    }
                )
                SettingsToggle(
                    icon = Icons.Rounded.Notifications,
                    title = "Thông báo",
                    subtitle = "Nhắc bạn khi có match mới, tin nhắn hoặc gợi ý sức khỏe hội thoại.",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.setNotifications(true) else {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.setNotifications(enabled)
                        }
                    }
                )
                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Trải nghiệm demo")
                SettingsToggle(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Chế độ demo",
                    subtitle = "Giữ 20 hồ sơ mẫu và dữ liệu mô phỏng để trải nghiệm đầy đủ tính năng.",
                    checked = settings.demoMode,
                    onCheckedChange = viewModel::setDemoMode
                )
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    icon = Icons.Rounded.Science,
                    title = "AI Service",
                    body = if (BuildConfig.AI_BASE_URL.isBlank()) {
                        "Chưa cấu hình. M1/M2/M3 đang dùng Quy tắc FwP (fallback) cục bộ."
                    } else {
                        "Đã cấu hình tại ${BuildConfig.AI_BASE_URL}. Khi mất kết nối, ứng dụng tự quay về fallback."
                    }
                )
                Spacer(Modifier.height(8.dp))
                InfoCard(
                    icon = Icons.Rounded.Info,
                    title = "Backend",
                    body = if (BuildConfig.BACKEND_BASE_URL.isBlank()) {
                        "Chưa cấu hình backend. Toàn bộ dữ liệu demo lưu bằng Room trên thiết bị."
                    } else {
                        "Backend: ${BuildConfig.BACKEND_BASE_URL}"
                    }
                )
                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Nguyên tắc cộng đồng")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PtitGold.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("FwP không phải ứng dụng hẹn hò ẩn danh hoàn toàn.", style = MaterialTheme.typography.titleSmall, color = Color(0xFF6D5100))
                        Text(
                            "Danh tính email được xác thực, nhưng ảnh và thông tin nhạy cảm chỉ mở khi cả hai đồng ý. Hãy tôn trọng, báo cáo hành vi xấu và không chia sẻ thông tin của người khác.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.logOut() }
                        .padding(vertical = 13.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(11.dp))
                    Text("Đăng xuất", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showDelete = true }
                        .padding(vertical = 13.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = PtitRed)
                    Spacer(Modifier.width(11.dp))
                    Text("Xóa tài khoản và dữ liệu", style = MaterialTheme.typography.titleSmall, color = PtitRed)
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    "FriendWithPTIT · ${BuildConfig.VERSION_NAME} · Made for PTIT students",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Xóa toàn bộ dữ liệu?") },
            text = { Text("Hồ sơ, lượt thích, match, tin nhắn và cài đặt sẽ bị xóa khỏi thiết bị. Hành động này không thể hoàn tác trong demo.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteAccount()
                }) { Text("Xóa vĩnh viễn", color = PtitRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Giữ lại") }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 9.dp)
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PtitRedSoft.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = PtitRed, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
