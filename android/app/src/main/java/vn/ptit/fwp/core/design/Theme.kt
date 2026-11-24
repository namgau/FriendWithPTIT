package vn.ptit.fwp.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.ptit.fwp.R

val PtitRed = Color(0xFFC62828)
val PtitRedDeep = Color(0xFF8E1B1B)
val PtitRedSoft = Color(0xFFFFE7E7)
val PtitGold = Color(0xFFFFC107)
val PtitGoldSoft = Color(0xFFFFF3CD)
val PtitWhite = Color(0xFFFFFFFF)
val PtitInk = Color(0xFF212121)
val PtitMuted = Color(0xFF6F5F5B)
val PtitLine = Color(0xFFE5D9D5)
val PtitCream = Color(0xFFFFFBF7)
val PtitGreen = Color(0xFF2E7D32)
val PtitOrange = Color(0xFFEF6C00)

private val LightColors = lightColorScheme(
    primary = PtitRed,
    onPrimary = PtitWhite,
    primaryContainer = PtitRedSoft,
    onPrimaryContainer = PtitRedDeep,
    secondary = Color(0xFFB88600),
    onSecondary = PtitWhite,
    secondaryContainer = PtitGoldSoft,
    onSecondaryContainer = Color(0xFF5C4300),
    tertiary = PtitGold,
    onTertiary = PtitInk,
    background = PtitCream,
    onBackground = PtitInk,
    surface = PtitWhite,
    onSurface = PtitInk,
    surfaceVariant = Color(0xFFF6EEEB),
    onSurfaceVariant = PtitMuted,
    outline = PtitLine,
    outlineVariant = Color(0xFFEFE6E3),
    // Nổi khối bằng tông màu (M3), không bằng bóng đổ tùy ý.
    surfaceContainerLowest = PtitWhite,
    surfaceContainerLow = Color(0xFFFFF7F3),
    surfaceContainer = Color(0xFFFBF1ED),
    surfaceContainerHigh = Color(0xFFF6EAE6),
    surfaceContainerHighest = Color(0xFFF0E3DF),
    error = Color(0xFFBA1A1A),
    onError = PtitWhite
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A7A),
    onPrimary = Color(0xFF3B0505),
    primaryContainer = Color(0xFF7A1F1F),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = PtitGold,
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = PtitGold,
    onTertiary = Color(0xFF382B00),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF3EDED),
    surface = Color(0xFF1C1919),
    onSurface = Color(0xFFF3EDED),
    surfaceVariant = Color(0xFF2A2424),
    onSurfaceVariant = Color(0xFFD6CBCB),
    outline = Color(0xFF4A4242),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val FwpFont = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold)
)

private val FwpTypography = androidx.compose.material3.Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-1).sp),
    displayMedium = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = FwpFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
)

@Composable
fun FwpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = FwpTypography,
        shapes = androidx.compose.material3.Shapes(
            // dp, không phải %: RoundedCornerShape(34) là 34% cạnh → bottom sheet cong quá mức.
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
