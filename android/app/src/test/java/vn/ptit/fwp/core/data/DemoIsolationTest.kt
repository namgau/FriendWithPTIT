package vn.ptit.fwp.core.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import vn.ptit.fwp.navigation.Routes

class DemoIsolationTest {
    private val demo = AppSettings(authenticated = true, localDemoSession = true, email = "demo@ptit.edu.vn")
    private val accountA = AppSettings(authenticated = true, email = "a@example.test")

    @Test fun localRowsUnsubscribeAcrossDemoBackendAccountSwitchLogoutAndReturn() = runBlocking {
        val settings = MutableStateFlow(demo)
        val rows = MutableStateFlow(listOf("p01", "match-minhanh", "seed-msg-0"))
        var roomCollectors = 0
        val room = flow {
            roomCollectors++
            try { rows.collect { emit(it) } } finally { roomCollectors-- }
        }
        val received = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            room.demoOnly(settings, emptyList()).collect { received.send(it) }
        }
        suspend fun next() = withTimeout(2_000) { received.receive() }
        try {
            assertEquals(rows.value, next())
            assertEquals(1, roomCollectors)
            settings.value = accountA
            assertEquals(emptyList<String>(), next())
            assertEquals(0, roomCollectors)
            settings.value = accountA.copy(email = "b@example.test")
            yield()
            assertEquals(0, roomCollectors)
            rows.value = rows.value + "retained-demo-history"
            settings.value = AppSettings()
            yield()
            assertEquals(0, roomCollectors)
            settings.value = accountA
            yield()
            assertEquals(0, roomCollectors)
            // Backend/logged-out states do not subscribe to or consume the demo history.
            settings.value = demo
            assertEquals(rows.value, next())
            assertEquals(1, roomCollectors)
            assertTrue(rows.value.contains("retained-demo-history"))
        } finally { job.cancelAndJoin(); received.close() }
        assertEquals(0, roomCollectors)
    }

    @Test fun loggedOutAndBackendSessionsNeverSubscribeToRoom() = runBlocking {
        for (state in listOf(AppSettings(), demo.copy(authenticated = false), accountA, accountA.copy(email = "b@example.test"))) {
            var subscriptions = 0
            val source = flow { subscriptions++; emit(listOf("demo")) }
            val output = Channel<List<String>>(1)
            val job = launch { source.demoOnly(MutableStateFlow(state), emptyList()).collect { output.send(it) } }
            try {
                assertEquals(emptyList<String>(), withTimeout(2_000) { output.receive() })
                assertEquals(0, subscriptions)
            } finally { job.cancelAndJoin(); output.close() }
        }
    }

    @Test fun everyRoomOnlyDestinationFailsClosedOutsideDemo() {
        val destinations = listOf(Routes.PROFILE to "p01", Routes.CHAT to "match-minhanh",
            Routes.NEARBY to null, Routes.DOUBLE_DATE to null, Routes.NOTIFICATIONS to null)
        for ((route, id) in destinations) {
            assertTrue(route, allowsDemoRoute(true, true, route, id))
            assertFalse(route, allowsDemoRoute(true, false, route, id))
            assertFalse(route, allowsDemoRoute(false, true, route, id))
            assertFalse(route, allowsDemoRoute(false, false, route, id))
        }
        assertTrue(allowsDemoRoute(true, true, Routes.CHAT, "double-1730000000000"))
        assertFalse(allowsDemoRoute(true, true, "unregistered"))
    }

    @Test fun opaqueAuthorTokensAndAnonymousMatchesAreNeverRoomIds() {
        for (id in listOf("author-token", "550e8400-e29b-41d4-a716-446655440000", "anon:match-minhanh", "anon:p01", "", "p01/other")) {
            assertFalse(id, allowsDemoRoute(true, true, Routes.PROFILE, id))
            assertFalse(id, allowsDemoRoute(true, true, Routes.CHAT, id))
        }
        assertFalse(allowsDemoRoute(true, true, Routes.PROFILE, null))
        assertFalse(allowsDemoRoute(true, true, Routes.CHAT, null))
    }

    @Test fun presentationIdentitySeparatesAccountsModesLogoutAndSameAccountRelogin() {
        val original = presentationIdentity(accountA, 1)
        assertNotEquals(original, presentationIdentity(accountA.copy(email = "b@example.test"), 1))
        assertNotEquals(original, presentationIdentity(accountA, 2))
        assertNotEquals(original, presentationIdentity(accountA.copy(authenticated = false), 1))
        assertNotEquals(original, presentationIdentity(accountA.copy(localDemoSession = true), 1))
        assertEquals(original, presentationIdentity(accountA.copy(email = " A@EXAMPLE.TEST "), 1))
        assertFalse(allowsLocalDemo(true, false)) // demoMode's default true is not a demo session.
    }

    @Test fun roomBadgesAreDemoOnly() {
        assertEquals(12, demoCountOrZero(true, 12))
        for (state in listOf(accountA, accountA.copy(email = "b@example.test"), AppSettings())) {
            assertEquals(0, demoCountOrZero(allowsLocalDemo(state.authenticated, state.localDemoSession), 12))
        }
    }
}
