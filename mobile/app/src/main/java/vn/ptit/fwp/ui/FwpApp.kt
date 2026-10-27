package vn.ptit.fwp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.ptit.fwp.ui.chat.ChatScreen
import vn.ptit.fwp.ui.matches.MatchesScreen
import vn.ptit.fwp.ui.profile.ProfileScreen
import vn.ptit.fwp.ui.swipe.SwipeScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Khám phá", Icons.Filled.Search),
    Matches("matches", "Tin nhắn", Icons.Filled.Email),
    Profile("profile", "Hồ sơ", Icons.Filled.Person),
}

private const val CHAT_ROUTE = "chat/{matchId}"
private fun chatRoute(matchId: String) = "chat/$matchId"

@Composable
fun FwpApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (route != CHAT_ROUTE) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Discover.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Discover.route) { SwipeScreen(onOpenChat = { nav.navigate(chatRoute(it)) }) }
            composable(Tab.Matches.route) { MatchesScreen(onOpenChat = { nav.navigate(chatRoute(it)) }) }
            composable(Tab.Profile.route) { ProfileScreen() }
            composable(CHAT_ROUTE, arguments = listOf(navArgument("matchId") { type = NavType.StringType })) { entry ->
                ChatScreen(matchId = entry.arguments?.getString("matchId").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}
