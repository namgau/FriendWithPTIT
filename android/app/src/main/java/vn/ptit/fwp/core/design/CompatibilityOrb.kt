package vn.ptit.fwp.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompatibilityOrb(
    score: Int,
    personality: Int,
    emotion: Int,
    interest: Int,
    modifier: Modifier = Modifier,
    orbSize: Dp = 118.dp,
    showLabels: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbAngle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "orbPulse"
    )
    val normalized = (score.coerceIn(0, 100) / 100f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.32f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PtitGold.copy(alpha = 0.32f * normalized + 0.05f), PtitRed.copy(alpha = 0.15f), Color.Transparent),
                    center = center,
                    radius = radius * 1.75f
                ),
                radius = radius * 1.75f,
                center = center
            )
            rotate(angle * 0.35f, center) {
                drawOval(
                    color = PtitGold.copy(alpha = 0.55f),
                    topLeft = Offset(center.x - radius * 1.65f, center.y - radius * 0.62f),
                    size = Size(radius * 3.3f, radius * 1.24f),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
            rotate(-angle * 0.55f, center) {
                drawOval(
                    color = PtitRed.copy(alpha = 0.42f),
                    topLeft = Offset(center.x - radius * 1.38f, center.y - radius * 0.95f),
                    size = Size(radius * 2.76f, radius * 1.9f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            rotate(angle, center) {
                repeat(3) { i ->
                    val a = Math.toRadians((i * 120).toDouble())
                    val x = center.x + cos(a).toFloat() * radius * 1.45f
                    val y = center.y + sin(a).toFloat() * radius * 1.45f
                    drawCircle(
                        color = if (i == 0) PtitGold else PtitRed.copy(alpha = 0.7f),
                        radius = 2.4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        PtitGold.copy(alpha = 0.75f),
                        PtitRed.copy(alpha = 0.82f),
                        PtitRedDeep.copy(alpha = 0.95f)
                    ),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f),
                    radius = radius * 1.4f
                ),
                radius = radius * pulse,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = radius * 0.92f,
                center = Offset(center.x - radius * 0.22f, center.y - radius * 0.28f),
                style = Stroke(width = 1.dp.toPx())
            )
            val axes = listOf(personality, emotion, interest)
            axes.forEachIndexed { i, value ->
                val a = Math.toRadians((-90 + i * 120).toDouble())
                val start = Offset(center.x, center.y)
                val end = Offset(
                    center.x + cos(a).toFloat() * radius * (value / 100f) * 1.75f,
                    center.y + sin(a).toFloat() * radius * (value / 100f) * 1.75f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = start,
                    end = end,
                    strokeWidth = 1.6.dp.toPx()
                )
                drawCircle(if (i == 1) PtitGold else Color.White, radius = 2.2.dp.toPx(), center = end)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "độ hợp",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
    if (showLabels) {
        // Labels are rendered outside the orb in the chat screen.
    }
}

@Composable
fun AxisBar(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text("$value%", style = MaterialTheme.typography.labelSmall, color = color)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth(value / 100f)
                    .height(5.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
