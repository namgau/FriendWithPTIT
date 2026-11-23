package vn.ptit.fwp.feature.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.core.data.ChatNudgePreference
import vn.ptit.fwp.core.data.ChatNudgePreferences
import vn.ptit.fwp.core.model.ChatMessage

data class ChatSafetyState(
    val ready: Boolean = false,
    val enabled: Boolean = true,
    val overrides: Int = 0,
    val pendingDraft: String? = null,
    val failedDraft: String? = null,
    val busy: Boolean = false,
    val notice: String? = null
)

/** Shared by local and backend chat; all decisions use the persisted preference. */
class ChatSafetyPrompt(
    private val scope: CoroutineScope,
    private val preferences: ChatNudgePreferences,
    private val risk: suspend (String, List<ChatMessage>) -> Boolean,
    private val canSend: () -> Boolean,
    private val onSend: suspend (String) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val mutable = MutableStateFlow(ChatSafetyState())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            try { preferences.settings.collect { saved -> apply(saved) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError(e) }
        }
    }

    private fun apply(saved: ChatNudgePreference) = mutable.update {
        it.copy(ready = true, enabled = saved.enabled, overrides = saved.overrides)
    }

    fun send(text: String, context: List<ChatMessage>): Boolean {
        val draft = text.trim()
        if (draft.isEmpty() || mutable.value.busy || mutable.value.pendingDraft != null || !canSend()) return false
        mutable.update { it.copy(busy = true) }
        scope.launch {
            try {
                val saved = preferences.settings.first()
                apply(saved)
                if (!canSend()) return@launch
                val nudge = saved.enabled && risk(draft, context)
                currentCoroutineContext().ensureActive()
                if (!canSend()) return@launch
                if (nudge && preferences.settings.first().enabled) mutable.update { it.copy(pendingDraft = draft) }
                else onSend(draft)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (canSend()) mutable.update { it.copy(failedDraft = draft) }
                onError(e)
            }
            finally { mutable.update { it.copy(busy = false) } }
        }
        return true
    }

    fun confirmSend() {
        val draft = mutable.value.pendingDraft ?: return
        if (mutable.value.busy || !canSend()) return
        mutable.update { it.copy(pendingDraft = null, busy = true) }
        scope.launch {
            try {
                val saved = preferences.recordOverride()
                apply(saved)
                if (!saved.enabled) mutable.update { it.copy(notice = "Đã tắt nhắc an toàn cho cuộc chat này sau 3 lần Vẫn gửi. Bạn có thể bật lại trong menu chat.") }
                if (canSend()) onSend(draft)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(pendingDraft = draft) }
                onError(e)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun cancelSend() { if (!mutable.value.busy) mutable.update { it.copy(pendingDraft = null) } }

    fun setEnabled(enabled: Boolean) {
        if (!canSend() || mutable.value.busy || mutable.value.pendingDraft != null) return
        mutable.update { it.copy(busy = true) }
        scope.launch {
            try {
                preferences.setEnabled(enabled)
                apply(preferences.settings.first())
                mutable.update { it.copy(notice = if (enabled) "Đã bật lại nhắc an toàn cho cuộc chat này." else "Đã tắt nhắc an toàn cho cuộc chat này.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun consumeNotice() { mutable.update { it.copy(notice = null) } }
    fun consumeFailedDraft() { mutable.update { it.copy(failedDraft = null) } }
    fun invalidate() { mutable.update { it.copy(pendingDraft = null, failedDraft = null, notice = null) } }
}
