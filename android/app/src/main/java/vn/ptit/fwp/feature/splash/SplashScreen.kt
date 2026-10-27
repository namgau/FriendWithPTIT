package vn.ptit.fwp.feature.splash

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ptit.fwp.core.design.FwpLogo
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitWhite

@Composable
fun SplashScreen(darkTheme: Boolean) {
    val transition = rememberInfiniteTransition(label = "splash")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF8E1B1B), PtitRed, Color(0xFF5C1010))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(PtitGold.copy(alpha = 0.13f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.9f, size.height * 0.12f))
            drawCircle(PtitWhite.copy(alpha = 0.05f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.05f, size.height * 0.84f))
            rotate(orbit, center) {
                val radius = size.minDimension * 0.38f
                repeat(5) { index ->
                    val angle = Math.toRadians((index * 72).toDouble())
                    drawCircle(
                        color = PtitGold.copy(alpha = 0.6f),
                        radius = if (index == 0) 4f else 2.4f,
                        center = Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * radius,
                            center.y + kotlin.math.sin(angle).toFloat() * radius
                        )
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FwpLogo(size = 108.dp, onDark = true)
            Spacer(Modifier.height(24.dp))
            Text(
                "Friend With PTIT",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                color = PtitWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Gió tầng nào, mây tầng đấy",
                style = MaterialTheme.typography.titleMedium,
                color = PtitGold
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Kết nối bằng sự đồng điệu,\nkhông phải bằng vẻ ngoài.",
                style = MaterialTheme.typography.bodyMedium,
                color = PtitWhite.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(42.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = PtitGold,
                    strokeWidth = 2.4.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Đang chuẩn bị không gian của bạn…",
                    style = MaterialTheme.typography.labelLarge,
                    color = PtitWhite.copy(alpha = 0.78f)
                )
            }
        }
        Text(
            "Dành cho sinh viên · Bản demo cục bộ",
            style = MaterialTheme.typography.labelSmall,
            color = PtitWhite.copy(alpha = 0.48f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
        )
    }
}
