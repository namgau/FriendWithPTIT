package vn.ptit.fwp.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ptit.fwp.core.model.*

class DiscoverRankingTest {
    private fun profile(id: String) = Profile(
        id = id, nickname = id, fullName = id, age = 20, gender = Gender.OTHER,
        school = "PTIT", campus = "Hà Nội", faculty = "CNTT", major = "CNTT", academicYear = "K",
        alumni = false, gpa = 3.5, gpaTier = GpaTier.GOOD, bio = "", interests = listOf("Sách"),
        avatarSeed = 1, distanceKm = 1.0, verified = true, isDemo = true,
        showGpa = true, showLocation = true, mode = ConnectionMode.FRIENDS, blocked = false
    )

    @Test fun serverOrderAndFilterAreUsedOnlyForTheExactRequest() {
        val me = profile("me")
        val a = profile("a")
        val b = profile("b")
        val filters = DiscoveryFilters()
        val local = listOf(RankedProfile(a, 1, listOf("a"), "local"), RankedProfile(b, 2, listOf("b"), "local"))
        val remote = RemoteDeck(me, filters, mapOf(a.id to a, b.id to b),
            listOf(RankedProfile(b, 99, listOf("server"), "remote"), RankedProfile(a, 98, emptyList(), "remote")))
        assertEquals(listOf("b", "a"), orderedDeck(me, filters, local, remote).map { it.profile.id })
        assertEquals(local, orderedDeck(me, filters.copy(onlyVerified = !filters.onlyVerified), local, remote))
        assertEquals(local.take(1), orderedDeck(me, filters, local.take(1), remote))
        assertEquals(local, orderedDeck(me.copy(age = 21), filters, local, remote))
        assertEquals(listOf("b"), orderedDeck(me, filters, local, remote.copy(ranks = listOf(remote.ranks.first()))).map { it.profile.id })
    }
}
