package vn.ptit.fwp.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.model.FwpNotification

data class NotificationsUiState(
    val loading: Boolean = true,
    val notifications: List<FwpNotification> = emptyList(),
    val unread: Int = 0
)

class NotificationsViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<NotificationsUiState> = combine(
        container.repository.notifications,
        container.repository.unreadNotifications
    ) { notifications, unread ->
        NotificationsUiState(loading = false, notifications = notifications, unread = unread)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun markAllRead() {
        viewModelScope.launch {
            container.repository.markNotificationsRead()
        }
    }
}
