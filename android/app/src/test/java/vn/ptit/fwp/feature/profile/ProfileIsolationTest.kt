package vn.ptit.fwp.feature.profile

import org.junit.Assert.*
import org.junit.Test
import vn.ptit.fwp.core.data.AppSettings
import vn.ptit.fwp.core.data.DemoSeed
import vn.ptit.fwp.core.data.presentationIdentity
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.Swipe
import vn.ptit.fwp.core.model.SwipeAction

class ProfileIsolationTest {
    private val matches = listOf(
        FwpMatch("match-a", "p01", ConnectionMode.FRIENDS, 1, lastMessage = "demo", compatibility = 85),
        FwpMatch("match-b", "p02", ConnectionMode.FRIENDS, 1),
        FwpMatch("double-1", "p03", ConnectionMode.FRIENDS, 1, lastMessage = "group", isGroup = true)
    )
    private val profiles = DemoSeed.profiles
    private val swipes = listOf(Swipe("p01", SwipeAction.PASS, 1))

    @Test fun backendProfileStateContainsNoLocalStatsOrAchievementsEvenWithPopulatedRoom() {
        for (email in listOf("a@example.test", "b@example.test")) {
            val identity = presentationIdentity(AppSettings(authenticated = true, email = email), 3)
            val backendProfile = profiles.first().copy(nickname = "Backend", isDemo = false)
            val state = profileUiState(backendProfile, true, null, identity, matches, profiles, swipes)
            assertSame(backendProfile, state.me)
            assertTrue(state.backendProfile)
            assertEquals(identity, state.identity)
            assertEquals(0, state.matchCount)
            assertEquals(0, state.activeChatCount)
            assertEquals(0, state.incomingLikes)
            assertEquals(0, state.bestCompatibility)
            assertTrue(state.achievements.isEmpty())
        }
    }

    @Test fun returningToDemoKeepsOriginalStatsAndAchievementRules() {
        val identity = presentationIdentity(AppSettings(authenticated = true, localDemoSession = true), 4)
        val state = profileUiState(profiles.first(), false, null, identity, matches, profiles, swipes)
        assertFalse(state.backendProfile)
        assertEquals(2, state.matchCount)
        assertEquals(2, state.activeChatCount)
        assertEquals(profiles.count { it.likedYou && it.id != "p01" }, state.incomingLikes)
        assertEquals(85, state.bestCompatibility)
        assertEquals(listOf(true, false, true, true, true), state.achievements.map { it.unlocked })
    }

    @Test fun backendLoadFailureNeverFallsBackToDemoProfileOrStats() {
        val identity = presentationIdentity(AppSettings(authenticated = true, email = "a@example.test"), 5)
        val state = profileUiState(null, true, "offline", identity, matches, profiles, swipes)
        assertNull(state.me)
        assertEquals("offline", state.error)
        assertEquals(0, state.matchCount)
        assertTrue(state.achievements.isEmpty())
    }
}
