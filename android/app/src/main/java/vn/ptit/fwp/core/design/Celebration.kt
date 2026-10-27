package vn.ptit.fwp.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.core.model.Profile
import kotlin.random.Random

@Composable
fun MatchCelebration(
    me: Profile?,
    other: Profile,
    onChat: () -> Unit,
    onContinue: () -> Unit
) {
    val scale = remember { Animatable(0.72f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(420))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE6C62828), Color(0xF2FFF9F0), Color(0xFFFFF9F0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Confetti(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .padding(28.dp)
                .scale(scale.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = PtitGold,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = PtitRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("MATCH!", style = MaterialTheme.typography.labelLarge, color = PtitRedDeep, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                Surface(
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(3.dp, PtitWhite),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        DemoPortrait(seed = me?.avatarSeed ?: 1, modifier = Modifier.fillMaxSize())
                    }
                }
                Surface(
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(3.dp, PtitGold),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        DemoPortrait(seed = other.avatarSeed, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Hai bạn đã match!",
                style = MaterialTheme.typography.headlineMedium,
                color = PtitRedDeep,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${me?.nickname ?: "Bạn"} và ${other.nickname} đã cùng thích nhau. Ảnh vẫn được giữ ẩn danh cho đến khi cả hai sẵn sàng nhé.",
                style = MaterialTheme.typography.bodyMedium,
                color = PtitInk.copy(alpha = 0.76f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GpaBadge(me?.gpaTier ?: vn.ptit.fwp.core.model.GpaTier.GOOD, compact = true)
                Text("  ·  ", color = PtitMuted)
                GpaBadge(other.gpaTier, compact = true)
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton(text = "Nhắn ngay", onClick = onChat)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Tiếp tục lướt",
                onClick = onContinue,
                containerColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiProgress"
    )
    val pieces = remember {
        val random = Random(42)
        List(46) {
            ConfettiPiece(
                x = random.nextFloat(),
                delay = random.nextFloat(),
                speed = 0.45f + random.nextFloat() * 0.8f,
                size = 4f + random.nextFloat() * 7f,
                color = listOf(PtitGold, PtitRed, Color(0xFFEF5350), Color.White, Color(0xFFFFD54F)).random(random),
                spin = random.nextFloat() * 360f
            )
        }
    }
    Canvas(modifier = modifier) {
        pieces.forEach { piece ->
            val local = (progress + piece.delay) % 1f
            val y = local * (size.height + 80f) - 40f
            val x = piece.x * size.width + kotlin.math.sin(local * 8f) * 20f
            val alpha = (1f - local).coerceIn(0f, 1f)
            drawCircle(
                color = piece.color.copy(alpha = alpha * 0.9f),
                radius = piece.size,
                center = Offset(x, y)
            )
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float
)
