package vn.ptit.fwp.feature.main

import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.AppSettings
import vn.ptit.fwp.core.data.allowsLocalDemo
import vn.ptit.fwp.core.data.demoCountOrZero
import vn.ptit.fwp.core.data.presentationIdentity
import vn.ptit.fwp.feature.social.BackendDiscoverScreen
import vn.ptit.fwp.feature.social.BackendMatchesScreen
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.feature.campus.CampusScreen
import vn.ptit.fwp.feature.discover.DiscoverScreen
import vn.ptit.fwp.feature.explore.ExploreScreen
import vn.ptit.fwp.feature.matches.MatchesScreen
import vn.ptit.fwp.feature.profile.ProfileScreen

private data class MainTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    MainTab("Hôm nay", Icons.Rounded.LocalFireDepartment),
    MainTab("Khám phá", Icons.Rounded.Explore),
    MainTab("Trang chủ", Icons.Rounded.Home),
    // Match và Chat gộp một tab: danh sách match chính là danh sách cuộc trò chuyện.
    MainTab("Match", Icons.Rounded.Forum),
    MainTab("Tôi", Icons.Rounded.Person)
)

@Composable
fun MainScreen(
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenDoubleDate: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenQuestions: () -> Unit = {}
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val matches by container.repository.matches.collectAsState(initial = emptyList())
    val profiles by container.repository.profiles.collectAsState(initial = emptyList())
    val swipes by container.repository.swipes.collectAsState(initial = emptyList())
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val demo = allowsLocalDemo(settings.authenticated, settings.localDemoSession)
    val generation by container.backendSession.sessionGeneration.collectAsState()
    val identity = presentationIdentity(settings, generation)
    val unreadMessages = matches.sumOf { it.unreadCount }
    val swipedIds = swipes.map { it.targetId }.toSet()
    val incomingLikes = profiles.count { it.likedYou && it.id !in swipedIds }
    // Backend: chấm đỏ ở tab Match khi có thông báo match / tin nhắn / lượt thích chưa đọc (hỏi lại mỗi 20 giây).
    val matchUnread by androidx.compose.runtime.produceState(0, identity, demo) {
        if (demo || !identity.authenticated) return@produceState
        val api = container.backendSocial.forIdentity(identity)
        while (true) {
            value = runCatching { api.notifications().items.count { !it.read && it.kind in setOf("MATCH", "MESSAGE", "LIKE") } }.getOrDefault(value)
            kotlinx.coroutines.delay(20_000)
        }
    }
    var matchSeen by remember { mutableIntStateOf(0) }
    val matchDot = matchUnread > matchSeen
    LaunchedEffect(selected) {
        if (selected == 3 && !demo && identity.authenticated) {
            matchSeen = matchUnread
            runCatching { container.backendSocial.forIdentity(identity).readNotifications() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    val badge = when (index) {
                        3 -> demoCountOrZero(demo, incomingLikes + unreadMessages)
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge > 0) {
                                        Badge(containerColor = PtitRed) {
                                            Text(if (badge > 99) "99+" else badge.toString())
                                        }
                                    }
                                }
                            ) {
                                AnimatedTabIcon(index, tab, selected == index, dot = index == 3 && matchDot)
                            }
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PtitRed,
                            selectedTextColor = PtitRed,
                            indicatorColor = PtitRed.copy(alpha = 0.11f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 24 })
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                },
                label = "tabContent"
            ) { current ->
                when (current) {
                    0 -> if (!demo) BackendDiscoverScreen(identity, onOpenChat, onOpenProfile, onOpenNotifications, onOpenQuestions) else Column {
                      Text("Khám phá demo trên thiết bị · không đồng bộ tài khoản backend", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                      DiscoverScreen(
                        onOpenChat = onOpenChat,
                        onOpenNotifications = onOpenNotifications
                      )
                    }
                    1 -> if (!demo) vn.ptit.fwp.feature.social.BackendExploreScreen(identity, onOpenProfile) else Column {
                      Text("Hồ sơ gợi ý demo trên thiết bị", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                      ExploreScreen(
                        onOpenProfile = onOpenProfile,
                        onOpenMatches = { selected = 3 },
                        onOpenNearby = onOpenNearby
                      )
                    }
                    2 -> CampusScreen(onOpenChat = onOpenChat)
                    3 -> if (!demo) BackendMatchesScreen(identity, onOpenChat, onOpenProfile) else Column {
                      Text("Match demo trên thiết bị · chưa đồng bộ backend", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                      MatchesScreen(
                        onOpenChat = onOpenChat,
                        onOpenProfile = onOpenProfile,
                        onOpenDoubleDate = onOpenDoubleDate
                      )
                    }
                    else -> ProfileScreen(
                        onEditProfile = onEditProfile,
                        onSettings = onOpenSettings,
                        onOpenMatches = { selected = 3 },
                        onOpenQuestions = onOpenQuestions
                    )
                }
            }
        }
    }
}

/**
 * Icon tab có animation riêng khi được chọn: lửa bập bùng, la bàn xoay, nhà nhún, khung chat lắc, người bật lên.
 * dot = chấm đỏ nhấp nháy ở góc phải trên.
 */
@Composable
private fun AnimatedTabIcon(index: Int, tab: MainTab, selected: Boolean, dot: Boolean) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) { anim.snapTo(0f); anim.animateTo(1f, androidx.compose.animation.core.tween(if (index == 1) 650 else 520)) }
    }
    val t = anim.value
    val wave = kotlin.math.sin(t * Math.PI.toFloat())
    Box {
        Icon(
            tab.icon, contentDescription = tab.label,
            modifier = Modifier.graphicsLayer {
                when (index) {
                    0 -> { // lửa: phập phồng + lắc nhẹ
                        val flicker = 1f + kotlin.math.sin(t * 6 * Math.PI.toFloat()) * 0.12f * (1 - t)
                        scaleX = flicker; scaleY = flicker * (1f + wave * 0.1f)
                        rotationZ = kotlin.math.sin(t * 4 * Math.PI.toFloat()) * 8f * (1 - t)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    1 -> rotationZ = t * 360f // la bàn xoay một vòng
                    2 -> translationY = -wave * 10f // nhà nhún lên
                    3 -> rotationZ = kotlin.math.sin(t * 3 * Math.PI.toFloat()) * 14f * (1 - t) // chat lắc
                    else -> { scaleX = 1f + wave * 0.22f; scaleY = 1f + wave * 0.22f }
                }
            }
        )
        if (dot) {
            val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
            val alpha by blink.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "dotAlpha")
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(9.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(PtitRed, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}
