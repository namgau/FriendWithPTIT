package vn.ptit.fwp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.BuildConfig

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.REGISTER,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val loading: Boolean = false,
    val verified: Boolean = false,
    val profileComplete: Boolean = false,
    val demoReady: Boolean = false,
    val showPassword: Boolean = false
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun toggleMode() = _state.update { it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN, error = null, password = "", confirmPassword = "") }
    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setConfirmPassword(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun submitForm() {
        val s = _state.value
        if (s.loading) return
        val error = when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches() -> "Email chưa đúng định dạng."
            s.mode == AuthMode.REGISTER && s.password.length !in 8..128 -> "Mật khẩu đăng ký cần từ 8 đến 128 ký tự."
            s.password.isEmpty() -> "Hãy nhập mật khẩu."
            s.mode == AuthMode.REGISTER && s.password != s.confirmPassword -> "Mật khẩu nhập lại chưa khớp."
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                container.backendSession.logout()
                val me = container.backendSession.signIn(s.email, s.password, s.mode == AuthMode.REGISTER)
                container.settingsStore.completeOnboarding()
                _state.update { it.copy(loading = false, password = "", confirmPassword = "", verified = true, profileComplete = me.profileComplete) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when {
                    e is HttpException && e.code() == 401 -> "Email hoặc mật khẩu không đúng."
                    e is HttpException && e.code() == 409 -> "Email đã được đăng ký. Hãy đăng nhập."
                    e is HttpException && e.code() == 400 -> "Email hoặc mật khẩu không hợp lệ."
                    e is IllegalStateException -> e.message ?: "Không thể đăng nhập."
                    else -> "Không kết nối được backend. Hãy kiểm tra mạng và thử lại."
                }
                _state.update { it.copy(loading = false, password = "", confirmPassword = "", error = message) }
            }
        }
    }

    fun enterDemo() {
        if (!BuildConfig.DEBUG || _state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                container.backendSession.logout()
                container.settingsStore.signInDemo("demo@ptit.edu.vn")
                container.settingsStore.completeOnboarding()
                val hasLocalProfile = container.repository.myProfile.first() != null
                _state.update { it.copy(loading = false, demoReady = hasLocalProfile, verified = !hasLocalProfile, profileComplete = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(loading = false, error = "Không thể mở demo trên thiết bị.") } }
        }
    }
}
