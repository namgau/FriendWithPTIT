package vn.ptit.fwp.core.design

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs
import vn.ptit.fwp.core.model.Profile

private data class PortraitPalette(
    val bg1: Color,
    val bg2: Color,
    val accent: Color,
    val skin: Color,
    val skinShadow: Color,
    val hair: Color,
    val cloth: Color
)

private val portraitPalettes = listOf(
    PortraitPalette(Color(0xFFFFD8C8), Color(0xFFF7A072), Color(0xFFFFE082), Color(0xFFF2C6A0), Color(0xFFD9A37A), Color(0xFF2B1B17), Color(0xFFC62828)),
    PortraitPalette(Color(0xFFD7E8FF), Color(0xFF8FB9E8), Color(0xFFFFC107), Color(0xFFF5C9A6), Color(0xFFDBA37C), Color(0xFF3A2418), Color(0xFF1E4E8C)),
    PortraitPalette(Color(0xFFE7D9FF), Color(0xFFB69CEB), Color(0xFFFFE082), Color(0xFFEFC09C), Color(0xFFD29A72), Color(0xFF1F1A17), Color(0xFF6A1B9A)),
    PortraitPalette(Color(0xFFFFE1E1), Color(0xFFF49A9A), Color(0xFFFFD54F), Color(0xFFF7CDAA), Color(0xFFD9A17A), Color(0xFF4A2C1B), Color(0xFF8E1B1B)),
    PortraitPalette(Color(0xFFD8F3E6), Color(0xFF82C9AE), Color(0xFFFFE082), Color(0xFFEEC29F), Color(0xFFCF9870), Color(0xFF2C1B12), Color(0xFF00695C)),
    PortraitPalette(Color(0xFFFFF0C9), Color(0xFFEAC46D), Color(0xFFFFF8E1), Color(0xFFF4C8A3), Color(0xFFD6A079), Color(0xFF27170F), Color(0xFFB26A00)),
    PortraitPalette(Color(0xFFD9EEFF), Color(0xFF7FB6DF), Color(0xFFFFD54F), Color(0xFFEEC39D), Color(0xFFCE9670), Color(0xFF2B1D15), Color(0xFF2E5C9A)),
    PortraitPalette(Color(0xFFFFE5F0), Color(0xFFEAA0C0), Color(0xFFFFF176), Color(0xFFF7CFAB), Color(0xFFD8A17B), Color(0xFF3D2218), Color(0xFFAD1457))
)

@Composable
fun DemoPortrait(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val p = portraitPalettes[abs(seed) % portraitPalettes.size]
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                colors = listOf(p.bg1, p.bg2),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        drawCircle(p.accent.copy(alpha = 0.32f), radius = w * 0.30f, center = Offset(w * 0.12f, h * 0.12f))
        drawCircle(Color.White.copy(alpha = 0.16f), radius = w * 0.22f, center = Offset(w * 0.92f, h * 0.30f))
        drawCircle(Color.White.copy(alpha = 0.10f), radius = w * 0.12f, center = Offset(w * 0.80f, h * 0.08f))

        val longHair = abs(seed) % 3 != 1
        if (longHair) {
            drawOval(
                color = p.hair,
                topLeft = Offset(w * 0.26f, h * 0.27f),
                size = Size(w * 0.48f, h * 0.55f)
            )
        }
        drawOval(
            color = p.cloth,
            topLeft = Offset(w * 0.10f, h * 0.63f),
            size = Size(w * 0.80f, h * 0.50f)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(w * 0.10f, h * 0.63f),
            size = Size(w * 0.80f, h * 0.16f)
        )
        drawRoundRect(
            color = p.skinShadow,
            topLeft = Offset(w * 0.43f, h * 0.50f),
            size = Size(w * 0.14f, h * 0.17f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f)
        )

        val faceRadius = w * 0.185f
        val faceCenter = Offset(w * 0.5f, h * 0.385f)
        drawCircle(p.hair, radius = faceRadius * 1.10f, center = Offset(w * 0.5f, h * 0.365f))
        drawCircle(p.skin, radius = faceRadius, center = faceCenter)
        drawArc(
            color = p.hair,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(faceCenter.x - faceRadius, faceCenter.y - faceRadius * 1.02f),
            size = Size(faceRadius * 2, faceRadius * 1.55f)
        )
        if (abs(seed) % 4 == 2) {
            drawArc(
                color = p.skinShadow.copy(alpha = 0.35f),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(faceCenter.x - faceRadius, faceCenter.y - faceRadius),
                size = Size(faceRadius * 2, faceRadius * 2),
                style = Stroke(width = w * 0.006f)
            )
        }

        val eyeY = h * 0.385f
        val eyeDx = faceRadius * 0.42f
        drawCircle(Color(0xFF2D1B16), radius = w * 0.015f, center = Offset(w * 0.5f - eyeDx, eyeY))
        drawCircle(Color(0xFF2D1B16), radius = w * 0.015f, center = Offset(w * 0.5f + eyeDx, eyeY))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.0045f, center = Offset(w * 0.5f - eyeDx + w * 0.004f, eyeY - w * 0.004f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.0045f, center = Offset(w * 0.5f + eyeDx + w * 0.004f, eyeY - w * 0.004f))
        drawArc(
            color = Color(0xFF8A4B3C),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.5f - faceRadius * 0.26f, h * 0.415f),
            size = Size(faceRadius * 0.52f, faceRadius * 0.28f),
            style = Stroke(width = w * 0.007f)
        )
        drawCircle(PtitGold, radius = w * 0.009f, center = Offset(w * 0.5f - faceRadius * 0.95f, h * 0.42f))
        drawCircle(PtitGold, radius = w * 0.009f, center = Offset(w * 0.5f + faceRadius * 0.95f, h * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.14f), radius = w * 0.07f, center = Offset(w * 0.84f, h * 0.68f))
    }
}

@Composable
fun ProfilePhoto(
    profile: Profile,
    modifier: Modifier = Modifier,
    blurLevel: Float = 0.75f,
    revealed: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    showAnonymousHint: Boolean = true,
    showAnonBadge: Boolean = true
) {
    Box(modifier = modifier.clip(shape).background(PtitRedSoft)) {
        val blurRadius = if (revealed) 0.dp else (blurLevel.coerceIn(0f, 1f) * 34f).dp
        if (!profile.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = if (revealed) "Ảnh của ${profile.nickname}" else "Ảnh đang được làm mờ",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(blurRadius)
            )
        } else {
            DemoPortrait(
                seed = profile.avatarSeed,
                modifier = Modifier
                    .matchParentSize()
                    .blur(blurRadius)
            )
        }

        if (Build.VERSION.SDK_INT < 31 && !revealed) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.30f))
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
        if (!revealed) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAnonBadge) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = PtitGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Ẩn danh",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (showAnonymousHint && !revealed) {
            Text(
                text = "Ảnh sẽ rõ dần khi hai bạn trò chuyện",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
