package vn.ptit.fwp.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Emoji con vật cho danh tính ẩn danh ; vị trí i ứng với avatarSeed i + 1 (khớp ANIMAL_ALIASES ở backend). */
private val ANIMALS = listOf("🐱", "🐶", "🦊", "🐼", "🐨", "🐯", "🦁", "🐸", "🐵", "🐰", "🐻", "🐹", "🐧", "🦉", "🐙", "🦄", "🐷", "🐥", "🐬", "🐢")
private val PASTELS = listOf(
    Color(0xFFFFE0B2), Color(0xFFC8E6C9), Color(0xFFBBDEFB), Color(0xFFF8BBD0), Color(0xFFD1C4E9),
    Color(0xFFFFF9C4), Color(0xFFB2EBF2), Color(0xFFFFCCBC)
)

fun animalEmoji(seed: Int): String = ANIMALS[Math.floorMod(seed - 1, ANIMALS.size)]

/** Avatar ẩn danh hình con vật trên nền pastel, chữ co theo kích thước ô. */
@Composable
fun AnimalAvatar(seed: Int, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableIntStateOf(0) }
    val fontSize = with(LocalDensity.current) { (sizePx * 0.55f).toSp() }
    Box(
        modifier
            .background(PASTELS[Math.floorMod(seed, PASTELS.size)])
            .onSizeChanged { sizePx = minOf(it.width, it.height) },
        contentAlignment = Alignment.Center
    ) {
        if (sizePx > 0) Text(animalEmoji(seed), fontSize = fontSize)
    }
}
