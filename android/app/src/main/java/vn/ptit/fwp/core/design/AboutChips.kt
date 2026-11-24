package vn.ptit.fwp.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private fun aboutIcon(key: String): ImageVector = when (key) {
    "smoking" -> Icons.Rounded.SmokeFree
    "drinking" -> Icons.Rounded.LocalBar
    "heightCm" -> Icons.Rounded.Height
    "pets" -> Icons.Rounded.Pets
    "religion" -> Icons.Rounded.SelfImprovement
    "exercise" -> Icons.Rounded.FitnessCenter
    else -> Icons.Rounded.AutoAwesome
}

/** "Thêm về tôi" + "Ngôn ngữ" dạng chip có icon (kiểu Badoo). Không vẽ gì khi trống. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutChips(about: Map<String, String>, languages: List<String>, modifier: Modifier = Modifier) {
    if (about.isEmpty() && languages.isEmpty()) return
    Column(modifier) {
        if (about.isNotEmpty()) {
            Text("Thêm về tôi", style = MaterialTheme.typography.titleSmall, color = PtitRed, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                about.forEach { (key, value) -> IconChip(aboutIcon(key), if (key == "heightCm") "$value cm" else value) }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (languages.isNotEmpty()) {
            Text("Ngôn ngữ nói được", style = MaterialTheme.typography.titleSmall, color = PtitRed, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                languages.forEach { IconChip(Icons.Rounded.Translate, it) }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
