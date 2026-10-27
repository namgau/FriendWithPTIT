package vn.ptit.fwp.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.FwpApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as FwpApplication).container

@Composable
inline fun <reified VM : ViewModel> fwpViewModel(
    crossinline creator: () -> VM
): VM = viewModel(factory = SimpleViewModelFactory { creator() })

fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "Vừa xong"
        minutes < 60 -> "$minutes phút"
        hours < 24 -> "$hours giờ"
        days < 7 -> "$days ngày"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(timestamp))
    }
}

fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("vi", "VN")).format(Date(timestamp))

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd 'tháng' MM", Locale("vi", "VN")).format(Date(timestamp))

fun formatGpa(gpa: Double): String = String.format(Locale.US, "%.2f", gpa)

fun formatDistance(km: Double): String = when {
    km < 1 -> "${(km * 1000).toInt()} m"
    km < 10 -> String.format(Locale.US, "%.1f km", km)
    else -> "${km.toInt()} km"
}
