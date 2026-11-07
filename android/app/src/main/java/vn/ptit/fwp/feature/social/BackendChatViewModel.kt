package vn.ptit.fwp.feature.social

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.PresentationIdentity
import vn.ptit.fwp.core.data.remote.SocialMessagesDto

class BackendChatViewModel(container: AppContainer, identity: PresentationIdentity, private val matchId: String) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(BackendChatState())
    val state = mutable.asStateFlow()
    private val reads = Mutex()
    init { observeIdentity(container); refresh() }
    override fun invalidate() { mutable.value = BackendChatState(loading = false, unavailable = true) }

    fun refresh() {
        if (!valid || mutable.value.refreshing || mutable.value.unavailable) return
        mutable.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                reads.withLock {
                    if (!valid || mutable.value.unavailable) return@withLock
                    val match = api.match(matchId)
                    val page = api.messages(matchId)
                    if (valid) {
                        val previous = mutable.value
                        val overlap = page.items.any { new -> previous.messages.any { it.id == new.id } }
                        mutable.update { it.copy(loading = false, refreshing = false, match = match,
                            messages = mergeHistory(it.messages, page.items, older = false),
                            nextBefore = if (previous.messages.isNotEmpty() && overlap) previous.nextBefore else page.nextBefore,
                            pending = reconcilePending(it.pending, page.items), error = null) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { if (valid) mutable.update { it.copy(loading = false, refreshing = false) } }
        }
    }

    fun older() {
        val before = mutable.value.nextBefore ?: return
        if (!valid || mutable.value.paging || mutable.value.refreshing || mutable.value.unavailable) return
        mutable.update { it.copy(paging = true, error = null) }
        viewModelScope.launch {
            try {
                reads.withLock {
                    if (!valid || mutable.value.unavailable) return@withLock
                    val page = api.messages(matchId, before)
                    check(page.nextBefore != before) { "Cursor lịch sử không tiến lên." }
                    if (valid) applyOlder(page)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { if (valid) mutable.update { it.copy(paging = false) } }
        }
    }
    private fun applyOlder(page: SocialMessagesDto) = mutable.update { it.copy(messages = mergeHistory(it.messages, page.items, older = true),
        nextBefore = page.nextBefore, pending = reconcilePending(it.pending, page.items)) }

    fun send(text: String): Boolean {
        if (!valid || mutable.value.match == null || mutable.value.unavailable) return false
        val pending = try { newPendingSend(text) } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(error = "Tin nhắn cần từ 1 đến 2000 ký tự.") }; return false
        }
        mutable.update { it.copy(pending = it.pending + pending) }
        submit(pending)
        return true
    }
    fun retry(key: String) {
        val pending = mutable.value.pending.firstOrNull { it.request.clientMessageId == key } ?: return
        if (pending.status != SendStatus.RETRY || !valid || mutable.value.unavailable) return
        mutable.update { it.copy(pending = it.pending.map { p -> if (p == pending) p.copy(status = SendStatus.SENDING) else p }) }
        submit(pending)
    }
    private fun submit(pending: PendingSend) {
        viewModelScope.launch {
            try {
                val confirmed = api.send(matchId, pending.request)
                if (valid && !mutable.value.unavailable) {
                    // Only history pages establish server order. Appending a receipt could create
                    // false overlap across an unseen >50-message gap during the next refresh.
                    mutable.update { it.copy(pending = if (it.messages.any { message -> message.id == confirmed.id })
                        reconcilePending(it.pending, listOf(confirmed))
                    else it.pending.map { p -> if (p.request.clientMessageId == confirmed.clientMessageId) p.copy(status = SendStatus.CONFIRMED) else p }) }
                    refresh()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (valid) {
                    mutable.update { it.copy(pending = it.pending.map { p ->
                        if (p.request.clientMessageId == pending.request.clientMessageId)
                            p.copy(status = when {
                                e is HttpException && e.code() == 409 -> SendStatus.CONFLICT
                                e is HttpException && e.code() in 400..499 && e.code() !in listOf(408, 429) -> SendStatus.REJECTED
                                else -> SendStatus.RETRY
                            }) else p
                    }) }
                    fail(e)
                    if (e !is HttpException || e.code() != 409) refresh()
                }
            }
        }
    }
    fun reveal(enabled: Boolean) = action {
        val match = api.reveal(matchId, enabled)
        if (valid) {
            // A withdrawn reveal must mask the screen immediately, before a history refetch.
            mutable.update { it.copy(match = match) }
            refresh()
        }
    }
    fun block() = action { val match = mutable.value.match ?: return@action; api.block(match); closeConversation() }
    fun report(reason: String) = action { api.report(matchId, reason); closeConversation() }
    fun unmatch() = action { api.unmatch(matchId); closeConversation() }
    private fun closeConversation() { if (valid) mutable.value = BackendChatState(loading = false, unavailable = true) }
    private fun action(block: suspend () -> Unit) {
        if (!valid || mutable.value.busy || mutable.value.unavailable) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { reads.withLock { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { if (valid) mutable.update { it.copy(busy = false) } }
        }
    }
    private fun fail(error: Exception) {
        if (!valid) return
        if (error is HttpException && error.code() == 404) closeConversation()
        else mutable.update { it.copy(error = socialError(error)) }
    }
}
