package vn.ptit.fwp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.MyProfileDraft
import vn.ptit.fwp.core.model.UniversityCatalog

enum class AuthMode { LOGIN, REGISTER }
enum class AuthStep { FORM, OTP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.REGISTER,
    val step: AuthStep = AuthStep.FORM,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val otp: String = "",
    val error: String? = null,
    val loading: Boolean = false,
    val verified: Boolean = false,
    val demoReady: Boolean = false,
    val showPassword: Boolean = false,
    val resendCooldown: Int = 0
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun toggleMode() {
        _state.update {
            it.copy(
                mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
                error = null,
                step = AuthStep.FORM
            )
        }
    }

    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setConfirmPassword(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }
    fun setOtp(value: String) = _state.update { it.copy(otp = value.filter(Char::isDigit).take(6), error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun submitForm() {
        val s = _state.value
        when {
            !s.email.contains("@") -> {
                _state.update { it.copy(error = "Email chưa đúng định dạng.") }
                return
            }
            !UniversityCatalog.isVerifiedEmail(s.email) -> {
                _state.update { it.copy(error = "Hiện FwP ưu tiên email trường đại học. Hãy dùng email @ptit.edu.vn hoặc email của trường trong danh sách.") }
                return
            }
            s.password.length < 6 -> {
                _state.update { it.copy(error = "Mật khẩu cần ít nhất 6 ký tự.") }
                return
            }
            s.mode == AuthMode.REGISTER && s.password != s.confirmPassword -> {
                _state.update { it.copy(error = "Mật khẩu nhập lại chưa khớp.") }
                return
            }
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(550)
            _state.update { it.copy(loading = false, step = AuthStep.OTP, error = null) }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        if (s.otp != DEMO_OTP) {
            _state.update { it.copy(error = "Mã OTP chưa đúng. Mã demo là $DEMO_OTP.") }
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            container.settingsStore.signIn(s.email)
            container.settingsStore.completeOnboarding()
            kotlinx.coroutines.delay(450)
            _state.update { it.copy(loading = false, verified = true, error = null) }
        }
    }

    fun resendOtp() {
        if (_state.value.resendCooldown > 0) return
        _state.update { it.copy(otp = "", error = null, resendCooldown = 30) }
        viewModelScope.launch {
            repeat(30) {
                kotlinx.coroutines.delay(1000)
                _state.update { it.copy(resendCooldown = (it.resendCooldown - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun enterDemo() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val draft = MyProfileDraft(
                nickname = "Mây Nhỏ",
                age = 20,
                gender = Gender.FEMALE,
                faculty = "Công nghệ Thông tin",
                major = "Kỹ thuật Phần mềm",
                academicYear = "K66",
                gpa = 3.45,
                bio = "Mình thích cà phê, code và những buổi chiều yên tĩnh. Đây là hồ sơ demo để trải nghiệm FwP.",
                interests = setOf("Cà phê", "Lập trình", "Indie", "Mèo", "Sách"),
                avatarSeed = 21,
                email = DEMO_EMAIL
            )
            container.settingsStore.completeOnboarding()
            container.settingsStore.signIn(DEMO_EMAIL)
            container.repository.saveMyProfile(draft)
            container.repository.addNotification(
                vn.ptit.fwp.core.model.NotificationType.SYSTEM,
                "Chế độ Demo đang bật",
                "Toàn bộ hồ sơ, tin nhắn và điểm tương thích trong demo là dữ liệu mô phỏng. Các nhãn AI sẽ ghi rõ khi dùng mô hình thật."
            )
            _state.update { it.copy(loading = false, demoReady = true) }
        }
    }

    fun backToForm() = _state.update { it.copy(step = AuthStep.FORM, error = null) }

    companion object {
        const val DEMO_OTP = "123456"
        const val DEMO_EMAIL = "demo@ptit.edu.vn"
    }
}
