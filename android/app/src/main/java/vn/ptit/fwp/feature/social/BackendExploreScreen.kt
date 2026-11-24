package vn.ptit.fwp.feature.social

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.PresentationIdentity
import vn.ptit.fwp.core.data.displayProfile
import vn.ptit.fwp.core.data.remote.SocialProfileDto
import vn.ptit.fwp.core.design.FwpLogo
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.ageCohortLabel
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel
import java.time.LocalDate
import java.time.ZoneId

data class ExploreState(val loading: Boolean = true, val error: String? = null, val profiles: List<SocialProfileDto> = emptyList())

class BackendExploreViewModel(container: AppContainer, identity: PresentationIdentity) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(ExploreState())
    val state = mutable.asStateFlow()
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = ExploreState(loading = false) }
    fun refresh() {
        if (!valid) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { val page = api.discover(0, DiscoveryFilters()); if (valid) mutable.value = ExploreState(false, profiles = page.items) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, error = socialError(e)) } }
        }
    }
}

/**
 * Khám phá kiểu Badoo: 4 "Đề cử hôm nay" đổi mỗi ngày (cố định trong ngày), rồi các hàng theo chủ đề.
 * Hôm nay = quẹt từng thẻ ; Khám phá = lướt nhiều người cùng lúc và mở hồ sơ để quyết định.
 */
@Composable
fun BackendExploreScreen(identity: PresentationIdentity, onOpenProfile: (String) -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "explore:$identity") { BackendExploreViewModel(container, identity) }
    val state by vm.state.collectAsState()
    val today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
    val picks = remember(state.profiles, today) {
        state.profiles.shuffled(kotlin.random.Random(today.toEpochDay() * 31 + identity.hashCode())).take(4)
    }
    val bestMatch = remember(state.profiles) { state.profiles.filter { it.questionMatch != null }.sortedByDescending { it.questionMatch }.take(10) }
    val nearby = remember(state.profiles) { state.profiles.filter { it.showLocationPublic && it.distanceKm > 0 }.sortedBy { it.distanceKm }.take(10) }
    val sameInterests = remember(state.profiles) { state.profiles.sortedByDescending { it.compatibility ?: 0 }.take(10) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                FwpLogo(size = 36.dp, withWordmark = true, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Làm mới") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
        if (picks.isNotEmpty()) item {
            SectionTitle("Đề cử hôm nay", "4 người chọn riêng cho bạn · đổi lúc 0h")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                picks.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { p -> PersonCard(p, Modifier.weight(1f), tall = true) { onOpenProfile(p.id) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        section("Hợp gu nhất", "Theo câu hỏi cả hai cùng trả lời", bestMatch, onOpenProfile)
        section("Gần bạn", "Khoảng cách tương đối, không lộ vị trí chính xác", nearby, onOpenProfile)
        section("Cùng sở thích", "Nhiều sở thích chung nhất", sameInterests, onOpenProfile)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, subtitle: String, people: List<SocialProfileDto>, onOpenProfile: (String) -> Unit
) {
    if (people.isEmpty()) return
    item {
        SectionTitle(title, subtitle)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(people, key = { it.id }) { p -> PersonCard(p, Modifier.width(150.dp), tall = false) { onOpenProfile(p.id) } }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Thẻ người: ảnh mờ nhẹ, tên + tuổi-khóa, nhãn hợp gu ; nhún nhẹ khi bấm. */
@Composable
private fun PersonCard(dto: SocialProfileDto, modifier: Modifier, tall: Boolean, onClick: () -> Unit) {
    val profile = dto.displayProfile()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f), label = "press")
    Box(
        modifier
            .aspectRatio(if (tall) 0.72f else 0.78f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ProfilePhoto(profile, Modifier.fillMaxSize(), blurLevel = 0.45f, shape = RoundedCornerShape(16.dp),
            showAnonymousHint = false, showAnonBadge = false)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(profile.nickname, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ageCohortLabel(profile.age, profile.academicYear), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            dto.questionMatch?.let { Text("Hợp gu $it%", style = MaterialTheme.typography.labelMedium, color = PtitGold) }
        }
        if (dto.superLike) Text("★", color = PtitRed, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}
