package vn.ptit.fwp.feature.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.ptit.fwp.core.design.CompatibilityOrb
import vn.ptit.fwp.core.design.DemoModeBadge
import vn.ptit.fwp.core.design.EmptyState
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpLogo
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.MatchCelebration
import vn.ptit.fwp.core.design.ProfilePhoto
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.VerifiedMark
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile
import vn.ptit.fwp.core.model.SwipeAction
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatDistance
import vn.ptit.fwp.core.util.fwpViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onOpenChat: (String) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val container = appContainer()
    val viewModel = fwpViewModel { DiscoverViewModel(container) }
    val state by viewModel.uiState.collectAsState()
    val celebration by viewModel.celebration.collectAsState()
    val message by viewModel.message.collectAsState()
    val unreadNotifications by container.repository.unreadNotifications.collectAsState(initial = 0)
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FwpLogo(size = 38.dp, withWordmark = true, modifier = Modifier.weight(1f))
                DemoModeBadge(onClick = { }, modifier = Modifier.padding(end = 4.dp))
                BadgedBox(
                    badge = {
                        if (unreadNotifications > 0) {
                            Badge(containerColor = PtitRed) { Text(unreadNotifications.toString()) }
                        }
                    }
                ) {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Rounded.Notifications, contentDescription = "Thông báo")
                    }
                }
                BadgedBox(
                    badge = {
                        if (state.filters.activeCount > 0) {
                            Badge(containerColor = PtitGold, contentColor = Color(0xFF3B2F00)) {
                                Text(state.filters.activeCount.toString())
                            }
                        }
                    }
                ) {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Bộ lọc")
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Khám phá hôm nay", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (state.deck.isEmpty()) "Chưa có gợi ý mới" else "${state.deck.size} người đang chờ bạn mở lời",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { viewModel.refreshRanking() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (state.refreshing) "Đang cập nhật" else "Nguồn: ${shortSource(state.source)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                if (state.loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (state.deck.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = "Tạm hết gợi ý phù hợp",
                        subtitle = "Bạn đã xem hết hồ sơ trong bộ lọc hiện tại. Có thể nới bộ lọc hoặc làm mới lượt xem để khám phá lại từ đầu.",
                        modifier = Modifier.align(Alignment.Center),
                        actionLabel = "Làm mới lượt xem",
                        onAction = viewModel::resetDeck
                    )
                } else {
                    val visible = state.deck.take(3)
                    visible.reversed().forEachIndexed { reverseIndex, ranked ->
                        val index = visible.lastIndex - reverseIndex
                        if (index == 0) {
                            SwipeCard(
                                ranked = ranked,
                                me = state.me,
                                busy = state.busy,
                                modifier = Modifier.fillMaxSize(),
                                onLike = { viewModel.swipe(SwipeAction.LIKE) },
                                onPass = { viewModel.swipe(SwipeAction.PASS) },
                                onSuperLike = { viewModel.swipe(SwipeAction.SUPER_LIKE) }
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = (index * 10).dp, start = (index * 8).dp, end = (index * 8).dp)
                                    .graphicsLayer {
                                        scaleX = 1f - index * 0.035f
                                        scaleY = 1f - index * 0.035f
                                    }
                            ) {}
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleAction(
                    icon = Icons.Rounded.Undo,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = state.canUndo,
                    size = 50,
                    onClick = viewModel::undo
                )
                CircleAction(
                    icon = Icons.Rounded.Close,
                    tint = PtitRed,
                    enabled = state.deck.isNotEmpty() && !state.busy,
                    size = 64,
                    onClick = { viewModel.swipe(SwipeAction.PASS) }
                )
                CircleAction(
                    icon = Icons.Rounded.Star,
                    tint = Color(0xFF1565C0),
                    enabled = state.deck.isNotEmpty() && !state.busy,
                    size = 52,
                    onClick = { viewModel.swipe(SwipeAction.SUPER_LIKE) }
                )
                CircleAction(
                    icon = Icons.Rounded.Favorite,
                    tint = Color(0xFFD81B60),
                    enabled = state.deck.isNotEmpty() && !state.busy,
                    size = 64,
                    onClick = { viewModel.swipe(SwipeAction.LIKE) }
                )
            }
            Text(
                "Vuốt trái để bỏ qua · Vuốt phải để thích",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 112.dp)
        )

        celebration?.let { data ->
            MatchCelebration(
                me = state.me,
                other = data.profile,
                onChat = {
                    viewModel.dismissCelebration()
                    onOpenChat(data.matchId)
                },
                onContinue = viewModel::dismissCelebration
            )
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            FilterSheet(
                initial = state.filters,
                onApply = {
                    viewModel.setFilters(it)
                    showFilters = false
                },
                onReset = {
                    viewModel.resetFilters()
                    showFilters = false
                }
            )
        }
    }
}

@Composable
private fun SwipeCard(
    ranked: RankedProfile,
    me: Profile?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit
) {
    val profile = ranked.profile
    val offsetX = remember(profile.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shared = me?.interests?.intersect(profile.interests.toSet())?.toList().orEmpty()

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = offsetX.value / 55f }
            .pointerInput(profile.id, busy) {
                if (busy) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    },
                    onDragEnd = {
                        when {
                            offsetX.value > 280f -> scope.launch {
                                offsetX.animateTo(1400f, tween(220))
                                onLike()
                            }
                            offsetX.value < -280f -> scope.launch {
                                offsetX.animateTo(-1400f, tween(220))
                                onPass()
                            }
                            else -> scope.launch { offsetX.animateTo(0f, tween(220)) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(220)) }
                    }
                )
            }
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                ProfilePhoto(
                    profile = profile,
                    modifier = Modifier.fillMaxSize(),
                    blurLevel = 0.82f,
                    revealed = false,
                    shape = RoundedCornerShape(30.dp),
                    showAnonymousHint = false
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.48f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.86f)
                            )
                        )
                )
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = modeColor(profile.mode)
                    ) {
                        Text(
                            profile.mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (profile.alumni) {
                        Surface(shape = RoundedCornerShape(50), color = PtitGold) {
                            Text(
                                "Alumni",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4A3800),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                CompatibilityOrb(
                    score = ranked.score,
                    personality = ranked.score,
                    emotion = (ranked.score - 6).coerceAtLeast(20),
                    interest = (ranked.score + 4).coerceAtMost(98),
                    orbSize = 84.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.nickname,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${profile.age}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        if (profile.verified) {
                            Spacer(Modifier.width(6.dp))
                            VerifiedMark()
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${profile.academicYear} · ${profile.major.ifBlank { profile.faculty }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GpaBadge(profile.gpaTier, compact = true)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            profile.school.substringBefore(" – ").ifBlank { profile.school },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            formatDistance(profile.distanceKm),
                            style = MaterialTheme.typography.labelSmall,
                            color = PtitGold
                        )
                    }
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ranked.reasons.take(2).forEach { reason ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    if (shared.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chung: ${shared.take(4).joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PtitGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    enabled: Boolean,
    size: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (enabled) 10.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(size.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else tint.copy(alpha = 0.34f),
                modifier = Modifier.size((size * 0.42f).dp)
            )
        }
    }
}

@Composable
private fun FilterSheet(
    initial: DiscoveryFilters,
    onApply: (DiscoveryFilters) -> Unit,
    onReset: () -> Unit
) {
    var filters by remember { mutableStateOf(initial) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Bộ lọc khám phá", style = MaterialTheme.typography.headlineSmall)
        Text(
            "FwP luôn ưu tiên người phù hợp với gu và nhịp sống của bạn.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Text("Chế độ kết nối", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionMode.entries.forEach { mode ->
                FwpChip(
                    text = mode.label,
                    selected = mode in filters.modes,
                    onClick = {
                        val next = if (mode in filters.modes) filters.modes - mode else filters.modes + mode
                        filters = filters.copy(modes = next.ifEmpty { ConnectionMode.entries.toSet() })
                    }
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row {
            Text("Độ tuổi", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${filters.ageRange.first} – ${filters.ageRange.last}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        RangeSlider(
            value = filters.ageRange.first.toFloat()..filters.ageRange.last.toFloat(),
            onValueChange = { range ->
                filters = filters.copy(ageRange = range.start.roundToInt()..range.endInclusive.roundToInt())
            },
            valueRange = 18f..30f
        )
        Spacer(Modifier.height(14.dp))
        Text("Tầng GPA", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GpaTier.entries.forEach { tier ->
                FwpChip(
                    text = tier.label,
                    selected = tier in filters.gpaTiers,
                    onClick = {
                        val next = if (tier in filters.gpaTiers) filters.gpaTiers - tier else filters.gpaTiers + tier
                        filters = filters.copy(gpaTiers = next.ifEmpty { GpaTier.entries.toSet() })
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row {
            Text("Khoảng cách tối đa", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${filters.maxDistanceKm} km",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = filters.maxDistanceKm.toFloat(),
            onValueChange = { filters = filters.copy(maxDistanceKm = it.roundToInt()) },
            valueRange = 1f..50f
        )
        FilterToggle(
            title = "Chỉ người cùng trường",
            checked = filters.sameSchoolOnly,
            onCheckedChange = { filters = filters.copy(sameSchoolOnly = it) }
        )
        FilterToggle(
            title = "Chỉ hồ sơ đã xác thực email",
            checked = filters.onlyVerified,
            onCheckedChange = { filters = filters.copy(onlyVerified = it) }
        )
        FilterToggle(
            title = "Dùng khoảng cách ước lượng",
            checked = filters.useApproximateLocation,
            onCheckedChange = { filters = filters.copy(useApproximateLocation = it) }
        )
        Spacer(Modifier.height(22.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = PtitRedSoft.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Vị trí chỉ được dùng để tính khoảng cách tương đối. FwP không hiển thị tọa độ chính xác và không chia sẻ vị trí cho người khác.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(13.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            vn.ptit.fwp.core.design.SecondaryButton(
                text = "Đặt lại",
                onClick = onReset,
                modifier = Modifier.weight(0.4f)
            )
            vn.ptit.fwp.core.design.PrimaryButton(
                text = "Áp dụng",
                onClick = { onApply(filters) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun modeColor(mode: ConnectionMode): Color = when (mode) {
    ConnectionMode.DATING -> PtitRed
    ConnectionMode.FRIENDS -> Color(0xFF7B1FA2)
    ConnectionMode.STUDY -> Color(0xFF1565C0)
}

private fun shortSource(source: String): String = when {
    source.contains("AI Service") -> "AI Service"
    else -> "FwP"
}
