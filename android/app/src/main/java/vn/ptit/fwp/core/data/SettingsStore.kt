package vn.ptit.fwp.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fwpSettings by preferencesDataStore(name = "fwp_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("Theo hệ thống"),
    LIGHT("Sáng"),
    DARK("Tối")
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val authenticated: Boolean = false,
    val profileComplete: Boolean = false,
    val email: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val aiAnalysisEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val profileVisible: Boolean = true,
    val locationEnabled: Boolean = false,
    val demoMode: Boolean = true,
    val showGpaPublic: Boolean = true,
    val showLocationPublic: Boolean = true
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val authenticated = booleanPreferencesKey("authenticated")
        val profileComplete = booleanPreferencesKey("profile_complete")
        val email = stringPreferencesKey("email")
        val theme = stringPreferencesKey("theme")
        val aiAnalysis = booleanPreferencesKey("ai_analysis")
        val notifications = booleanPreferencesKey("notifications")
        val profileVisible = booleanPreferencesKey("profile_visible")
        val locationEnabled = booleanPreferencesKey("location_enabled")
        val demoMode = booleanPreferencesKey("demo_mode")
        val showGpaPublic = booleanPreferencesKey("show_gpa_public")
        val showLocationPublic = booleanPreferencesKey("show_location_public")
    }

    val settings: Flow<AppSettings> = context.fwpSettings.data.map { prefs ->
        AppSettings(
            onboardingDone = prefs[Keys.onboarding] ?: false,
            authenticated = prefs[Keys.authenticated] ?: false,
            profileComplete = prefs[Keys.profileComplete] ?: false,
            email = prefs[Keys.email] ?: "",
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            aiAnalysisEnabled = prefs[Keys.aiAnalysis] ?: true,
            notificationsEnabled = prefs[Keys.notifications] ?: true,
            profileVisible = prefs[Keys.profileVisible] ?: true,
            locationEnabled = prefs[Keys.locationEnabled] ?: false,
            demoMode = prefs[Keys.demoMode] ?: true,
            showGpaPublic = prefs[Keys.showGpaPublic] ?: true,
            showLocationPublic = prefs[Keys.showLocationPublic] ?: true
        )
    }

    suspend fun completeOnboarding() = context.fwpSettings.edit { it[Keys.onboarding] = true }

    suspend fun signIn(email: String) = context.fwpSettings.edit {
        it[Keys.authenticated] = true
        it[Keys.email] = email
    }

    suspend fun signOut() = context.fwpSettings.edit {
        it[Keys.authenticated] = false
        it[Keys.profileComplete] = false
    }

    suspend fun completeProfile() = context.fwpSettings.edit { it[Keys.profileComplete] = true }

    suspend fun setTheme(mode: ThemeMode) = context.fwpSettings.edit { it[Keys.theme] = mode.name }

    suspend fun setAiAnalysis(enabled: Boolean) = context.fwpSettings.edit { it[Keys.aiAnalysis] = enabled }

    suspend fun setNotifications(enabled: Boolean) = context.fwpSettings.edit { it[Keys.notifications] = enabled }

    suspend fun setProfileVisible(visible: Boolean) = context.fwpSettings.edit { it[Keys.profileVisible] = visible }

    suspend fun setLocationEnabled(enabled: Boolean) = context.fwpSettings.edit { it[Keys.locationEnabled] = enabled }

    suspend fun setDemoMode(enabled: Boolean) = context.fwpSettings.edit { it[Keys.demoMode] = enabled }

    suspend fun setShowGpa(show: Boolean) = context.fwpSettings.edit { it[Keys.showGpaPublic] = show }

    suspend fun setShowLocation(show: Boolean) = context.fwpSettings.edit { it[Keys.showLocationPublic] = show }

    suspend fun reset() = context.fwpSettings.edit { it.clear() }
}
