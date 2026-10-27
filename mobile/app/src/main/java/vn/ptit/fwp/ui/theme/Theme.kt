package vn.ptit.fwp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bảng màu thương hiệu: hồng đào ấm + xanh tím than. Mọi màn hình dùng MaterialTheme.colorScheme,
// không viết mã màu trực tiếp.
private val Coral = Color(0xFFE5566B)
private val CoralDark = Color(0xFFFF8A9A)
private val Navy = Color(0xFF2B2D42)

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF3F0014),
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F0),
    onSecondaryContainer = Navy,
    tertiary = Color(0xFF2E8B74),
    tertiaryContainer = Color(0xFFC8F0E3),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFFFFBFA),
    surface = Color(0xFFFFFBFA),
    surfaceVariant = Color(0xFFF4EDEE),
    onSurfaceVariant = Color(0xFF524345),
)

private val DarkColors = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color(0xFF5F0D20),
    primaryContainer = Color(0xFF7F2335),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = Color(0xFFC3C5DA),
    secondaryContainer = Color(0xFF41435A),
    tertiary = Color(0xFF8ED6BE),
    tertiaryContainer = Color(0xFF0F5241),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF1A1113),
    surface = Color(0xFF1A1113),
    surfaceVariant = Color(0xFF2F2426),
    onSurfaceVariant = Color(0xFFD7C1C4),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/** Khoảng cách chuẩn — dùng thay cho số dp rời rạc để giao diện đều nhau. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun FwpTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
