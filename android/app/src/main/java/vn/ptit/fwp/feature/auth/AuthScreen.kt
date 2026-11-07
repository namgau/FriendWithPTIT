package vn.ptit.fwp.feature.auth

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.core.design.FwpLogo
import vn.ptit.fwp.core.design.FwpTextField
import vn.ptit.fwp.core.design.PrimaryButton
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SecondaryButton
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel

@Composable
fun AuthScreen(onVerified: (Boolean) -> Unit, onDemoReady: () -> Unit) {
    val container = appContainer()
    val viewModel = fwpViewModel { AuthViewModel(container) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.verified) { if (state.verified) onVerified(state.profileComplete) }
    LaunchedEffect(state.demoReady) { if (state.demoReady) onDemoReady() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, PtitRedSoft.copy(alpha = 0.45f))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(PtitGold.copy(alpha = 0.13f), radius = size.minDimension * 0.30f, center = Offset(size.width * 0.92f, size.height * 0.05f))
            drawCircle(PtitRed.copy(alpha = 0.06f), radius = size.minDimension * 0.28f, center = Offset(size.width * 0.02f, size.height * 0.9f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(44.dp))
            FwpLogo(size = 56.dp, withWordmark = true)
            Spacer(Modifier.height(30.dp))
            Text("Chào mừng đến FwP", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(7.dp))
            Text("Một không gian an toàn để gặp đúng người, đúng nhịp và đúng gu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                        AuthTab("Đăng ký", state.mode == AuthMode.REGISTER, Modifier.weight(1f)) { if (state.mode != AuthMode.REGISTER && !state.loading) viewModel.toggleMode() }
                        AuthTab("Đăng nhập", state.mode == AuthMode.LOGIN, Modifier.weight(1f)) { if (state.mode != AuthMode.LOGIN && !state.loading) viewModel.toggleMode() }
                    }
                    Spacer(Modifier.height(20.dp))
                    FwpTextField(value = state.email, onValueChange = { if (!state.loading) viewModel.setEmail(it) }, label = "Email trường", placeholder = "ten.ban@ptit.edu.vn", leadingIcon = Icons.Rounded.Mail,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(14.dp))
                    FwpTextField(value = state.password, onValueChange = { if (!state.loading) viewModel.setPassword(it) }, label = "Mật khẩu", placeholder = if (state.mode == AuthMode.REGISTER) "Ít nhất 8 ký tự" else "Nhập mật khẩu", leadingIcon = Icons.Rounded.Lock,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (state.mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done),
                        visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = { IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(if (state.showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (state.showPassword) "Ẩn mật khẩu" else "Hiện mật khẩu")
                        } })
                    if (state.mode == AuthMode.REGISTER) {
                        Spacer(Modifier.height(14.dp))
                        FwpTextField(value = state.confirmPassword, onValueChange = { if (!state.loading) viewModel.setConfirmPassword(it) }, label = "Nhập lại mật khẩu", leadingIcon = Icons.Rounded.Lock,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), visualTransformation = PasswordVisualTransformation())
                    }
                    AuthError(state.error)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(text = if (state.mode == AuthMode.REGISTER) "Tạo tài khoản" else "Đăng nhập", icon = Icons.Rounded.ArrowForward, loading = state.loading, onClick = viewModel::submitForm)
                    Spacer(Modifier.height(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.School, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đăng nhập bằng tài khoản backend.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
                    Text("hoặc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
                }
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    text = "Khám phá bản demo ngay",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = viewModel::enterDemo,
                    enabled = !state.loading,
                    containerColor = PtitRed.copy(alpha = 0.06f)
                )
                Spacer(Modifier.height(12.dp))
                Text("Demo chỉ lưu trên thiết bị và không đăng nhập Campus backend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun AuthTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(11.dp)).background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun AuthError(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(12.dp))
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
    }
}
