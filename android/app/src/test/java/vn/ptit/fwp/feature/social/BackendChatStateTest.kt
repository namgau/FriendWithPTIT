package vn.ptit.fwp.feature.social

import org.junit.Assert.*
import org.junit.Test
import vn.ptit.fwp.core.data.remote.SocialMessageDto

class BackendChatStateTest {
    private fun message(id: String, key: String? = null, mine: Boolean = true) = SocialMessageDto(id, "match", "sender", id, "TEXT", "2026-11-01T00:00:00.000Z", mine, key)

    @Test fun retryKeepsImmutableUuidTextAndPayloadAndNewSendGetsAnotherUuid() {
        var generated = 0
        val pending = newPendingSend(" hello ") { generated++; "uuid-$generated" }
        val retry = pending.copy(status = SendStatus.RETRY).copy(status = SendStatus.SENDING)
        assertSame(pending.request, retry.request)
        assertEquals("hello", retry.request.text); assertEquals("uuid-1", retry.request.clientMessageId)
        assertEquals(1, generated)
        assertNotEquals(pending.request.clientMessageId, newPendingSend("hello") { generated++; "uuid-$generated" }.request.clientMessageId)
    }
    @Test fun invalidMessageIsNotEnqueued() {
        for (text in listOf(" ", "x".repeat(2001))) assertTrue(runCatching { newPendingSend(text) }.isFailure)
    }
    @Test fun olderPagesPrependAndDeduplicateWithoutSortingSameTimestampByClient() {
        val existing = listOf(message("z"), message("a"))
        val merged = mergeHistory(existing, listOf(message("b"), message("z"), message("z")), true)
        assertEquals(listOf("b", "z", "a"), merged.map { it.id })
    }
    @Test fun refreshReplacesOverlappingTailInServerOrderWithoutDuplicateBubbles() {
        val merged = mergeHistory(listOf(message("old"), message("a"), message("b")), listOf(message("a"), message("b"), message("new")), false)
        assertEquals(listOf("old", "a", "b", "new"), merged.map { it.id })
    }
    @Test fun disjointRefreshDropsUnprovableGapAndCanReloadOlderUsingNewCursor() {
        assertEquals(listOf("new1", "new2"), mergeHistory(listOf(message("old")), listOf(message("new1"), message("new2")), false).map { it.id })
    }
    @Test fun pendingReconcilesOnlyServerConfirmedOwnClientUuid() {
        val a = newPendingSend("a") { "uuid-a" }; val b = newPendingSend("b") { "uuid-b" }
        assertEquals(listOf(b), reconcilePending(listOf(a, b), listOf(message("server-id", "uuid-a"))))
        assertEquals(listOf(a, b), reconcilePending(listOf(a, b), listOf(message("server-id", "uuid-a", mine = false))))
    }
}
