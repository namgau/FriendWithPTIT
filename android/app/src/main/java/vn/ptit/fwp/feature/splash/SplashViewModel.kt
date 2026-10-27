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

class SplashViewModel(private val container: AppContainer) : ViewModel() {
    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination

    init {
        viewModelScope.launch {
            container.repository.initialize()
            delay(900)
            val settings = container.settingsStore.settings.first()
            _destination.value = when {
                !settings.onboardingDone -> Routes.ONBOARDING
                !settings.authenticated -> Routes.AUTH
                !settings.profileComplete -> Routes.SETUP
                else -> Routes.MAIN
            }
        }
    }
}
