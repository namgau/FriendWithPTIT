package vn.ptit.fwp.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.navigation.Routes
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import vn.ptit.fwp.core.data.SessionRequiredException
import vn.ptit.fwp.core.data.IdentityMismatchException
import vn.ptit.fwp.BuildConfig

class SplashViewModel(private val container: AppContainer) : ViewModel() {
    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        restore()
    }

    fun restore() {
        _error.value = null
        viewModelScope.launch {
            container.repository.initialize()
            delay(900)
            container.settingsStore.clearLegacyBackendSession()
            val settings = container.settingsStore.settings.first()
            if (!settings.onboardingDone) { _destination.value = Routes.ONBOARDING; return@launch }
            if (!settings.authenticated) { _destination.value = Routes.AUTH; return@launch }
            if (settings.localDemoSession && BuildConfig.DEBUG) {
                _destination.value = if (settings.profileComplete) Routes.MAIN else Routes.SETUP
                return@launch
            }
            try {
                val me = container.backendSession.restore()
                container.settingsStore.setAuthenticatedSession(me.user.email, me.profileComplete)
                _destination.value = if (me.profileComplete) Routes.MAIN else Routes.SETUP
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is SessionRequiredException || e is IdentityMismatchException || (e is HttpException && e.code() == 401)) {
                    container.backendSession.logout()
                    _destination.value = Routes.AUTH
                } else {
                    _error.value = "Không thể khôi phục phiên. Kiểm tra kết nối backend và thử lại."
                }
            }
        }
    }
}
