package vn.ptit.fwp.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ptit.fwp.ui.theme.Spacing
import kotlin.math.abs

private val avatarPalettes = listOf(
    listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88)),
    listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC)),
    listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)),
    listOf(Color(0xFFFBC2EB), Color(0xFFA6C1EE)),
    listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
    listOf(Color(0xFFFFD194), Color(0xFFD1913C)),
)

/**
 * "Ảnh đại diện" bị làm mờ theo [blurLevel] (0 = rõ, 1 = mờ hẳn) — Module 2.
 * Hiện vẽ ảnh giả bằng gradient + chữ cái; khi có ảnh thật chỉ cần thay nội dung bên trong Box.
 * Modifier.blur chỉ có tác dụng từ Android 12 ; máy cũ hơn dùng lớp phủ mờ thay thế.
 */
@Composable
fun BlurredAvatar(
    seed: String,
    label: String,
    blurLevel: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size / 6),
) {
    val level by animateFloatAsState(blurLevel.coerceIn(0f, 1f), tween(600), label = "blur")
    val colors = avatarPalettes[abs(seed.hashCode()) % avatarPalettes.size]
    Box(modifier.size(size).clip(shape)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur((level * 25).dp) else Modifier)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.9f * level)))
        }
    }
}

/** Quả cầu độ hợp (bản 2D tạm): càng hợp càng to và sáng. Bản 3D làm sau (docs/05). */
@Composable
fun CompatOrb(compat: Float, size: Dp, modifier: Modifier = Modifier) {
    val c by animateFloatAsState(compat.coerceIn(0f, 1f), tween(800), label = "compat")
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2 * (0.55f + 0.45f * c)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), primary.copy(alpha = 0.4f + 0.6f * c), tertiary.copy(alpha = 0.3f)),
                    center = Offset(center.x - r / 3, center.y - r / 3),
                    radius = r * 1.4f,
                ),
                radius = r,
            )
        }
        Text("${(c * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<String>, highlighted: Set<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tags.forEach { tag ->
            val on = tag in highlighted
            AssistChip(
                onClick = {},
                label = { Text(tag) },
                colors = if (on) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
fun CenteredMessage(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.invoke()
    }
}
