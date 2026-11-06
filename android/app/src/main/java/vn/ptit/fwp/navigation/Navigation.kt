package vn.ptit.fwp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.allowsDemoRoute
import vn.ptit.fwp.core.data.presentationIdentity
import vn.ptit.fwp.feature.auth.AuthScreen
import vn.ptit.fwp.feature.chat.ChatScreen
import vn.ptit.fwp.feature.detail.ProfileDetailScreen
import vn.ptit.fwp.feature.doubledate.DoubleDateScreen
import vn.ptit.fwp.feature.main.MainScreen
import vn.ptit.fwp.feature.nearby.NearbyScreen
import vn.ptit.fwp.feature.notifications.NotificationsScreen
import vn.ptit.fwp.feature.onboarding.OnboardingScreen
import vn.ptit.fwp.feature.settings.SettingsScreen
import vn.ptit.fwp.feature.setup.ProfileSetupScreen
import vn.ptit.fwp.feature.splash.SplashScreen
import vn.ptit.fwp.feature.splash.SplashViewModel
import vn.ptit.fwp.core.util.fwpViewModel

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val SETUP = "setup"
    const val MAIN = "main"
    const val EDIT_PROFILE = "edit_profile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val DOUBLE_DATE = "double_date"
    const val NEARBY = "nearby"
    const val PROFILE = "profile/{profileId}"
    const val CHAT = "chat/{matchId}"

    fun profile(id: String) = "profile/$id"
    fun chat(id: String) = "chat/$id"
}

@Composable
fun FwpNavHost(container: AppContainer, darkTheme: Boolean) {
    val navController = rememberNavController()
    val sessionSettings by container.settingsStore.settings.collectAsState(initial = null)
    val generation by container.backendSession.sessionGeneration.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val identity = sessionSettings?.let { presentationIdentity(it, generation) }
    var previousIdentity by remember { mutableStateOf<vn.ptit.fwp.core.data.PresentationIdentity?>(null) }
    LaunchedEffect(identity) {
        val previous = previousIdentity
        previousIdentity = identity
        val route = navController.currentDestination?.route
        if (identity != null && previous != null && identity != previous && identity.authenticated && previous.authenticated &&
            route != Routes.SPLASH && route != Routes.AUTH && route != Routes.ONBOARDING) {
            navController.navigate(Routes.MAIN) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
        }
    }
    LaunchedEffect(sessionSettings?.authenticated, entry?.destination?.route) {
        val route = entry?.destination?.route
        if (sessionSettings?.authenticated == false && route != null && route !in setOf(Routes.SPLASH, Routes.ONBOARDING, Routes.AUTH)) {
            navController.navigate(Routes.AUTH) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            val viewModel = fwpViewModel { SplashViewModel(container) }
            val destination by viewModel.destination.collectAsState()
            val error by viewModel.error.collectAsState()
            SplashScreen(darkTheme = darkTheme, error = error, onRetry = viewModel::restore)
            LaunchedEffect(destination) {
                val target = destination ?: return@LaunchedEffect
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.AUTH) {
            AuthScreen(
                onVerified = { complete ->
                    navController.navigate(if (complete) Routes.MAIN else Routes.SETUP) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onDemoReady = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SETUP) {
            ProfileSetupScreen(
                editMode = false,
                onExit = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
          key(identity) {
            MainScreen(
                container = container,
                onOpenChat = { navController.navigate(Routes.chat(it)) },
                onOpenProfile = { navController.navigate(Routes.profile(it)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenDoubleDate = { navController.navigate(Routes.DOUBLE_DATE) },
                onOpenNearby = { navController.navigate(Routes.NEARBY) }
            )
          }
        }
        composable(Routes.EDIT_PROFILE) {
            ProfileSetupScreen(
                editMode = true,
                onExit = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType })
        ) { entry ->
            val matchId = entry.arguments?.getString("matchId") ?: return@composable
            if (allowsDemoRoute(sessionSettings?.authenticated == true, sessionSettings?.localDemoSession == true, Routes.CHAT, matchId))
                ChatScreen(matchId = matchId, onBack = { navController.popBackStack() })
            else DemoRouteUnavailable()
        }
        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { entry ->
            val profileId = entry.arguments?.getString("profileId") ?: return@composable
            if (allowsDemoRoute(sessionSettings?.authenticated == true, sessionSettings?.localDemoSession == true, Routes.PROFILE, profileId)) ProfileDetailScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(it)) }
            ) else DemoRouteUnavailable()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = { },
                onDeleted = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.NOTIFICATIONS) {
            if (allowsDemoRoute(sessionSettings?.authenticated == true, sessionSettings?.localDemoSession == true, Routes.NOTIFICATIONS))
                NotificationsScreen(onBack = { navController.popBackStack() }) else DemoRouteUnavailable()
        }
        composable(Routes.DOUBLE_DATE) {
            if (allowsDemoRoute(sessionSettings?.authenticated == true, sessionSettings?.localDemoSession == true, Routes.DOUBLE_DATE)) DoubleDateScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(it)) }
            ) else DemoRouteUnavailable()
        }
        composable(Routes.NEARBY) {
            if (allowsDemoRoute(sessionSettings?.authenticated == true, sessionSettings?.localDemoSession == true, Routes.NEARBY)) NearbyScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Routes.profile(it)) }
            ) else DemoRouteUnavailable()
        }
    }
}

@Composable
private fun DemoRouteUnavailable() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Nội dung này chỉ có trong phiên demo trên thiết bị. Tính năng backend chưa được tích hợp.")
    }
}
