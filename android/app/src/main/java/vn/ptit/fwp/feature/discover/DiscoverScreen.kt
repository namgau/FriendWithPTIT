package vn.ptit.fwp.feature.discover

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
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
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(Icons.Rounded.Undo, contentDescription = "Hoàn tác")
                }
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
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
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
                                shape = RoundedCornerShape(20.dp),
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

        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp)
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

/**
 * Thẻ hồ sơ: ảnh bìa chiếm trọn thẻ ; kéo lên trong thẻ để xem tiểu sử, thông tin, sở thích
 * và các ảnh còn lại. Ba nút tròn (bỏ qua / super like / thích) nổi cố định ở đáy thẻ.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SwipeCard(
    ranked: RankedProfile,
    me: Profile?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = !busy,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit,
    onOpenProfile: (() -> Unit)? = null
) {
    val profile = ranked.profile
    val scope = rememberCoroutineScope()
    val offsetX = remember(profile.id) { Animatable(0f) }
    val offsetY = remember(profile.id) { Animatable(0f) }
    // Thẻ mới phóng nhẹ từ phía sau lên (lò xo), như thẻ dưới cùng của chồng thẻ.
    val enter = remember(profile.id) { Animatable(0.93f) }
    LaunchedEffect(profile.id) { enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) }
    /** Nút bấm cũng cho thẻ bay ra theo hướng như khi kéo. */
    fun fly(dx: Float, dy: Float, then: () -> Unit) {
        scope.launch {
            launch { offsetY.animateTo(dy, tween(260)) }
            offsetX.animateTo(dx, tween(260))
            then()
            offsetX.snapTo(0f); offsetY.snapTo(0f)
        }
    }
    val scroll = rememberScrollState()
    val shared = me?.interests?.intersect(profile.interests.toSet())?.toList().orEmpty()
    val showGpa = profile.isDemo || profile.showGpa
    val shape = RoundedCornerShape(20.dp)

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = offsetX.value / 55f
                scaleX = enter.value; scaleY = enter.value
                alpha = ((enter.value - 0.93f) / 0.07f).coerceIn(0.4f, 1f)
            }
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
                                offsetX.snapTo(0f)
                            }
                            offsetX.value < -280f -> scope.launch {
                                offsetX.animateTo(-1400f, tween(220))
                                onPass()
                                offsetX.snapTo(0f)
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
        val cardHeight = maxHeight
        Surface(shape = shape, shadowElevation = 12.dp, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(cardHeight)) {
                    ProfilePhoto(
                        profile = profile,
                        modifier = Modifier.fillMaxSize(),
                        blurLevel = 0.45f,
                        revealed = false,
                        shape = RoundedCornerShape(0.dp),
                        showAnonymousHint = false,
                        showAnonBadge = false
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.18f),
                                    0.45f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.88f)
                                )
                            )
                    )
                    Row(
                        Modifier.align(Alignment.TopStart).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Pill(profile.mode.label, modeColor(profile.mode), Color.White)
                        if (profile.alumni) Pill("Alumni", PtitGold, Color(0xFF4A3800))
                    }
                    CompatibilityOrb(
                        score = ranked.score,
                        orbSize = 78.dp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 96.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.nickname,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (profile.verified) {
                                Spacer(Modifier.width(6.dp))
                                VerifiedMark()
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${vn.ptit.fwp.core.model.ageCohortLabel(profile.age, profile.academicYear)} · ${profile.major.ifBlank { profile.faculty }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showGpa) {
                                GpaBadge(profile.gpaTier, compact = true, gpa = profile.gpa)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                if (profile.isDemo || profile.showLocation) formatDistance(profile.distanceKm) else "Khoảng cách ẩn",
                                style = MaterialTheme.typography.labelSmall,
                                color = PtitGold
                            )
                            profile.questionMatch?.let {
                                Spacer(Modifier.width(7.dp))
                                Text("· Hợp gu $it%", style = MaterialTheme.typography.labelSmall, color = PtitGold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kéo lên để xem tiểu sử và ảnh khác",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    if (profile.bio.isNotBlank()) {
                        DetailTitle("Giới thiệu")
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(14.dp))
                    }
                    // Ảnh và câu trả lời xen kẽ, thứ tự ngẫu nhiên nhưng cố định theo hồ sơ.
                    val blocks = remember(profile.id, profile.photos, profile.answers) {
                        (profile.photos.drop(1).map { DetailBlock.Photo(it) } + profile.answers.take(5).map { DetailBlock.Answer(it.first, it.second) })
                            .shuffled(kotlin.random.Random(profile.id.hashCode()))
                    }
                    blocks.forEach { block ->
                        when (block) {
                            is DetailBlock.Photo -> ProfilePhoto(
                                profile = profile.copy(photoUrl = block.url),
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
                                blurLevel = 0.45f,
                                shape = RoundedCornerShape(12.dp),
                                showAnonymousHint = false,
                                showAnonBadge = false
                            )
                            is DetailBlock.Answer -> Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(block.question, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                    Text(block.answer, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    DetailTitle("Thông tin")
                    InfoLine("Trường", profile.school.substringBefore(" – ").ifBlank { profile.school })
                    InfoLine("Cơ sở", profile.campus)
                    InfoLine("Khoa", profile.faculty)
                    InfoLine("Ngành", profile.major)
                    InfoLine("Tuổi - khóa", vn.ptit.fwp.core.model.ageCohortLabel(profile.age, profile.academicYear))
                    if (showGpa) InfoLine("GPA", String.format(java.util.Locale.US, "%.2f", profile.gpa))
                    Spacer(Modifier.height(14.dp))
                    if (profile.interests.isNotEmpty()) {
                        DetailTitle("Sở thích")
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            profile.interests.forEach { interest ->
                                Pill(
                                    interest,
                                    if (interest in shared) PtitRed else MaterialTheme.colorScheme.surfaceVariant,
                                    if (interest in shared) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    profile.questionMatch?.let { percent ->
                        Text(
                            "Hợp gu $percent% trên ${profile.commonQuestions} câu hỏi cả hai cùng trả lời.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Chừa chỗ cho 3 nút nổi.
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleAction(Icons.Rounded.Close, PtitRed, actionsEnabled, 60) { fly(-1400f, 0f, onPass) }
            CircleAction(Icons.Rounded.Star, PtitGold, actionsEnabled, 50) { fly(0f, -2200f, onSuperLike) }
            CircleAction(Icons.Rounded.Favorite, Color(0xFFD81B60), actionsEnabled, 60) { fly(1400f, 0f, onLike) }
        }
        // Dấu "THÍCH" / "BỎ QUA" hiện rõ dần theo độ kéo.
        val drag = offsetX.value
        if (drag != 0f) {
            val stampAlpha = (kotlin.math.abs(drag) / 280f).coerceIn(0f, 1f)
            val liking = drag > 0
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(3.dp, if (liking) Color(0xFF2E7D32) else PtitRed),
                modifier = Modifier
                    .align(if (liking) Alignment.TopStart else Alignment.TopEnd)
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                    .graphicsLayer { alpha = stampAlpha; rotationZ = if (liking) -14f else 14f }
            ) {
                Text(
                    if (liking) "THÍCH" else "BỎ QUA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (liking) Color(0xFF2E7D32) else PtitRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private sealed interface DetailBlock {
    data class Photo(val url: String) : DetailBlock
    data class Answer(val question: String, val answer: String) : DetailBlock
}

@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DetailTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = PtitRed, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
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
    // Nhún khi bấm: phản hồi tức thì ~120ms.
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.88f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 800f), label = "press")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (enabled) 8.dp else 0.dp,
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
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
internal fun FilterSheet(
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
        Text("Tình trạng học tập", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vn.ptit.fwp.core.model.GraduationFilter.entries.forEach { graduation ->
                FwpChip(
                    text = when (graduation) {
                        vn.ptit.fwp.core.model.GraduationFilter.ALL -> "Tất cả"
                        vn.ptit.fwp.core.model.GraduationFilter.STUDYING -> "Đang học"
                        vn.ptit.fwp.core.model.GraduationFilter.ALUMNI -> "Đã tốt nghiệp"
                    },
                    selected = filters.graduation == graduation,
                    onClick = { filters = filters.copy(graduation = graduation) }
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
