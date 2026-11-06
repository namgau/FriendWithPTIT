package vn.ptit.fwp.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
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
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.feature.campus.CampusScreen
import vn.ptit.fwp.feature.chats.ChatsScreen
import vn.ptit.fwp.feature.discover.DiscoverScreen
import vn.ptit.fwp.feature.explore.ExploreScreen
import vn.ptit.fwp.feature.matches.MatchesScreen
import vn.ptit.fwp.feature.profile.ProfileScreen

private data class MainTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    MainTab("Hôm nay", Icons.Rounded.LocalFireDepartment),
    MainTab("Khám phá", Icons.Rounded.Explore),
    MainTab("Campus", Icons.Rounded.School),
    MainTab("Match", Icons.Rounded.Favorite),
    MainTab("Chat", Icons.Rounded.Forum),
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
    onOpenNearby: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val matches by container.repository.matches.collectAsState(initial = emptyList())
    val profiles by container.repository.profiles.collectAsState(initial = emptyList())
    val swipes by container.repository.swipes.collectAsState(initial = emptyList())
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val demo = allowsLocalDemo(settings.authenticated, settings.localDemoSession)
    val unreadMessages = matches.sumOf { it.unreadCount }
    val swipedIds = swipes.map { it.targetId }.toSet()
    val incomingLikes = profiles.count { it.likedYou && it.id !in swipedIds }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    val badge = when (index) {
                        3 -> demoCountOrZero(demo, incomingLikes)
                        4 -> demoCountOrZero(demo, unreadMessages)
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
                                Icon(tab.icon, contentDescription = tab.label)
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
        ) {
            when (selected) {
                0 -> if (!demo) LocalDemoUnavailable() else Column {
                  Text("Khám phá demo trên thiết bị · không đồng bộ tài khoản backend", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                  DiscoverScreen(
                    onOpenChat = onOpenChat,
                    onOpenNotifications = onOpenNotifications
                  )
                }
                1 -> if (!demo) LocalDemoUnavailable() else Column {
                  Text("Hồ sơ gợi ý demo trên thiết bị", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                  ExploreScreen(
                    onOpenProfile = onOpenProfile,
                    onOpenMatches = { selected = 3 },
                    onOpenNearby = onOpenNearby
                  )
                }
                2 -> CampusScreen()
                3 -> if (!demo) LocalDemoUnavailable() else Column {
                  Text("Match demo trên thiết bị · chưa đồng bộ backend", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                  MatchesScreen(
                    onOpenChat = onOpenChat,
                    onOpenProfile = onOpenProfile,
                    onOpenDoubleDate = onOpenDoubleDate
                  )
                }
                4 -> if (!demo) LocalDemoUnavailable() else Column {
                  Text("Chat demo trên thiết bị · chưa đồng bộ backend", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall)
                  ChatsScreen(
                    onOpenChat = onOpenChat,
                    onOpenMatches = { selected = 3 }
                  )
                }
                else -> ProfileScreen(
                    onEditProfile = onEditProfile,
                    onSettings = onOpenSettings,
                    onOpenMatches = { selected = 3 }
                )
            }
        }
    }
}

@Composable
private fun LocalDemoUnavailable() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Khám phá, Match và Chat backend chưa tích hợp. Dữ liệu demo trên thiết bị được giữ riêng và không hiển thị trong tài khoản này.", modifier = Modifier.padding(24.dp))
    }
}
