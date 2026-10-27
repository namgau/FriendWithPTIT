package vn.ptit.fwp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.ptit.fwp.AppContainer
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

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            val viewModel = fwpViewModel { SplashViewModel(container) }
            val destination by viewModel.destination.collectAsState()
            SplashScreen(darkTheme = darkTheme)
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
                onVerified = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onDemoReady = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
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
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
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
            ChatScreen(matchId = matchId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { entry ->
            val profileId = entry.arguments?.getString("profileId") ?: return@composable
            ProfileDetailScreen(
                profileId = profileId,
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(it)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onDeleted = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOUBLE_DATE) {
            DoubleDateScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(it)) }
            )
        }
        composable(Routes.NEARBY) {
            NearbyScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Routes.profile(it)) }
            )
        }
    }
}
