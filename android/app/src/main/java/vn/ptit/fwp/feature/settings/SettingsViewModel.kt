package vn.ptit.fwp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.AppSettings
import vn.ptit.fwp.core.data.ThemeMode

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsStore = container.settingsStore
    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }
    fun setAiAnalysis(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setAiAnalysis(enabled)
        _message.value = if (enabled) {
            "Đã bật phân tích hội thoại. FwP chỉ dùng dữ liệu trên thiết bị khi AI Service chưa cấu hình."
        } else {
            "Đã tắt phân tích hội thoại. M3 và gợi ý mở lời sẽ tạm ẩn."
        }
    }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settingsStore.setNotifications(enabled) }
    fun setProfileVisible(visible: Boolean) = viewModelScope.launch {
        if (canEditDemoPrivacy(settingsStore.settings.first())) settingsStore.setProfileVisible(visible)
    }
    fun setDemoMode(enabled: Boolean) = viewModelScope.launch { settingsStore.setDemoMode(enabled) }
    fun setShowGpa(show: Boolean) = viewModelScope.launch {
        if (canEditDemoPrivacy(settingsStore.settings.first())) settingsStore.setShowGpa(show)
    }
    fun setShowLocation(show: Boolean) = viewModelScope.launch {
        if (canEditDemoPrivacy(settingsStore.settings.first())) settingsStore.setShowLocation(show)
    }
    fun setLocationEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setLocationEnabled(enabled) }

    fun logOut() {
        viewModelScope.launch {
            try {
                container.backendSession.logout()
                _loggedOut.value = true
            } catch (_: Exception) { _message.value = "Không thể xóa phiên trên thiết bị. Hãy thử đăng xuất lại." }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _message.value = "Xóa tài khoản backend chưa được hỗ trợ. Không có dữ liệu nào bị xóa."
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
