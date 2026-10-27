package vn.ptit.fwp.feature.onboarding

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.ptit.fwp.core.design.FwpLogo
import vn.ptit.fwp.core.design.PrimaryButton
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.PtitWhite
import vn.ptit.fwp.core.util.appContainer

private data class OnboardingPage(
    val icon: ImageVector,
    val tag: String,
    val title: String,
    val description: String,
    val accent: Color
)

private val pages = listOf(
    OnboardingPage(
        Icons.Rounded.Favorite,
        "Ẩn danh có chủ đích",
        "Bắt đầu bằng một nickname,\nkhông phải ngoại hình",
        "FwP che ảnh và thông tin nhạy cảm lúc đầu. Hai bạn trò chuyện đủ nhiều, đủ đồng điệu rồi mới dần lộ diện.",
        PtitRed
    ),
    OnboardingPage(
        Icons.Rounded.School,
        "Cùng trường, cùng tầng",
        "Gặp người hiểu\nnhịp sống đại học của bạn",
        "Xác thực email trường, chọn khoa và sở thích. FwP ưu tiên những người có thể gặp nhau ở giảng đường, thư viện hoặc quán quen.",
        Color(0xFF1565C0)
    ),
    OnboardingPage(
        Icons.Rounded.Forum,
        "Đồng hành thật sự",
        "Không để cuộc trò chuyện\nchết dần trong im lặng",
        "M3 theo dõi nhịp hội thoại một cách tôn trọng và gợi ý mở lời khi hai bạn đang lạc nhịp. Bạn có thể bật hoặc tắt bất cứ lúc nào.",
        Color(0xFF2E7D32)
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })

    val finish = {
        scope.launch {
            container.settingsStore.completeOnboarding()
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        PtitRedSoft.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(PtitGold.copy(alpha = 0.10f), radius = size.minDimension * 0.32f, center = Offset(size.width * 0.92f, size.height * 0.06f))
            drawCircle(PtitRed.copy(alpha = 0.06f), radius = size.minDimension * 0.22f, center = Offset(size.width * 0.05f, size.height * 0.75f))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(36.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FwpLogo(size = 42.dp, withWordmark = true)
                Text(
                    "Bỏ qua",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { finish() }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(40.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 16.dp,
                        modifier = Modifier.size(230.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(page.accent.copy(alpha = 0.08f), radius = size.minDimension * 0.42f, center = center)
                                drawCircle(PtitGold.copy(alpha = 0.16f), radius = size.minDimension * 0.13f, center = Offset(size.width * 0.78f, size.height * 0.22f))
                                drawCircle(page.accent.copy(alpha = 0.09f), radius = size.minDimension * 0.09f, center = Offset(size.width * 0.2f, size.height * 0.82f))
                            }
                            Icon(
                                page.icon,
                                contentDescription = null,
                                tint = page.accent,
                                modifier = Modifier.size(86.dp)
                            )
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = PtitGold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(36.dp)
                                    .size(23.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    Text(
                        page.tag.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = page.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 26.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) PtitRed else MaterialTheme.colorScheme.outline)
                    )
                }
            }
            PrimaryButton(
                text = if (pagerState.currentPage == pages.lastIndex) "Bắt đầu ngay" else "Tiếp tục",
                icon = if (pagerState.currentPage == pages.lastIndex) Icons.Rounded.Favorite else null,
                onClick = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        finish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Bằng việc tiếp tục, bạn đồng ý với nguyên tắc an toàn và quyền riêng tư của FwP.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(26.dp))
        }
    }
}
