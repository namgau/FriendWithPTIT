package vn.ptit.fwp.feature.social

import vn.ptit.fwp.core.data.remote.*
import java.util.UUID

enum class SendStatus { SENDING, RETRY, CONFLICT, REJECTED, CONFIRMED }
data class PendingSend(val request: SocialSendRequest, val status: SendStatus = SendStatus.SENDING)
data class BackendChatState(
    val loading: Boolean = true, val refreshing: Boolean = false, val paging: Boolean = false,
    val match: SocialMatchDto? = null, val messages: List<SocialMessageDto> = emptyList(),
    val nextBefore: String? = null, val pending: List<PendingSend> = emptyList(),
    val error: String? = null, val unavailable: Boolean = false, val busy: Boolean = false
)

/** Keep the server's sequence, including sub-millisecond ordering that JSON dates may not retain. */
internal fun mergeHistory(existing: List<SocialMessageDto>, page: List<SocialMessageDto>, older: Boolean): List<SocialMessageDto> {
    val uniquePage = page.distinctBy { it.id }
    val ids = uniquePage.map { it.id }.toSet()
    if (older) return uniquePage + existing.filter { it.id !in ids }
    if (uniquePage.isEmpty()) return existing
    val firstOverlap = existing.indexOfFirst { it.id in ids }
    // No overlap means we cannot prove there is no gap. Caller resets its cursor/history.
    if (firstOverlap < 0) return uniquePage
    return existing.take(firstOverlap).filter { it.id !in ids } + uniquePage
}

internal fun reconcilePending(pending: List<PendingSend>, confirmed: List<SocialMessageDto>): List<PendingSend> {
    val keys = confirmed.filter { it.isMine }.mapNotNull { it.clientMessageId }.toSet()
    return pending.filter { it.request.clientMessageId !in keys }
}

/** The immutable request is retained across all retries, including uncertain network results. */
internal fun newPendingSend(text: String, uuid: () -> String = { UUID.randomUUID().toString() }): PendingSend {
    val trimmed = text.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 2000)
    return PendingSend(SocialSendRequest(uuid(), trimmed))
}
