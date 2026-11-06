package vn.ptit.fwp.feature.campus

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ptit.fwp.core.data.remote.CampusAuthorProfileDto
import vn.ptit.fwp.core.data.remote.CampusApiFactory
import vn.ptit.fwp.core.data.remote.CampusPostDto
import vn.ptit.fwp.core.data.remote.UpdateCampusSettingsRequest
import vn.ptit.fwp.core.model.toDomain
import vn.ptit.fwp.core.model.CampusPost
import vn.ptit.fwp.core.model.CampusCategory
import vn.ptit.fwp.core.model.CampusAnonymousMode
import vn.ptit.fwp.core.model.CampusAuthorBadge
import vn.ptit.fwp.core.model.CampusCounts
import vn.ptit.fwp.core.model.CampusViewerState

class CampusContractTest {
    @Test fun authorCardMayOmitPrivateFields() {
        val dto = Gson().fromJson(
            """{"token":"post-token","alias":"Ẩn danh","avatarSeed":4,"bio":"","discoverable":true}""",
            CampusAuthorProfileDto::class.java
        )
        val card = dto.toDomain()
        assertEquals("post-token", card.token)
        assertEquals(emptyList<String>(), card.interests)
        assertNull(card.faculty)
        assertNull(card.campus)
    }

    @Test fun signedMediaQueryIsPreserved() {
        val factory = CampusApiFactory("https://example.test")
        assertEquals("https://example.test/campus/media/a.jpg?exp=123&sig=a_b", factory.absoluteUrl("/campus/media/a.jpg?exp=123&sig=a_b"))
    }

    @Test fun retryAfterSupportsSecondsAndHttpDates() {
        assertEquals(12L, retryAfterSeconds("12"))
        assertEquals(10L, retryAfterSeconds("Wed, 21 Oct 2015 07:28:10 GMT", java.time.Instant.parse("2015-10-21T07:28:00Z").toEpochMilli()))
        assertNull(retryAfterSeconds("invalid"))
    }

    @Test fun oldRefreshAndPaginationCannotReplaceNewTab() {
        val requests = RequestGeneration()
        val firstTab = requests.next()
        val firstTabPage = requests.snapshot()
        val secondTab = requests.next()
        assertFalse(requests.isCurrent(firstTab))
        assertFalse(requests.isCurrent(firstTabPage))
        assertTrue(requests.isCurrent(secondTab))
        requests.next() // a newer refresh or sheet dismissal
        assertFalse(requests.isCurrent(secondTab))
    }

    @Test fun eachPostKeepsItsOwnAuthorTokenAndSignedMedia() {
        val gson = Gson()
        val factory = CampusApiFactory("https://example.test")
        fun post(token: String) = gson.fromJson(
            """{"id":"1","content":"test","category":"GENERAL","anonymousMode":"DISCOVERABLE","createdAt":"2026-01-01T00:00:00Z","author":{"alias":"Ẩn danh","avatarSeed":2,"discoverable":true,"authorToken":"$token"},"media":[{"id":"m","url":"/campus/media/a.jpg?exp=123&sig=abc","mimeType":"image/jpeg"}],"counts":{"likes":0,"comments":0,"bookmarks":0},"viewer":{"liked":false,"bookmarked":false,"isAuthor":false}}""",
            CampusPostDto::class.java
        ).toDomain(factory)
        assertEquals("token-a", post("token-a").author.authorToken)
        assertEquals("token-b", post("token-b").author.authorToken)
        assertEquals("https://example.test/campus/media/a.jpg?exp=123&sig=abc", post("token-a").media.single().url)
    }

    @Test fun settingsPatchSendsOnlyRequestedConsent() {
        val json = Gson().toJsonTree(UpdateCampusSettingsRequest(showFaculty = false)).asJsonObject
        assertEquals(false, json.get("showFaculty").asBoolean)
        assertFalse(json.has("discoveryEnabled"))
        assertFalse(json.has("showCampus"))
        assertFalse(json.has("showInterests"))
    }

    @Test fun rollbackDoesNotOverwriteUnrelatedChanges() {
        val before = CampusPost("p", "x", CampusCategory.GENERAL, CampusAnonymousMode.FULLY_ANONYMOUS,
            "", 0L, author = CampusAuthorBadge("Ẩn danh", 1, false), counts = CampusCounts(2, 3, 4),
            viewer = CampusViewerState(false, false, false))
        val changed = before.copy(counts = CampusCounts(3, 9, 8), viewer = CampusViewerState(true, true, false))
        val like = rollbackLike(changed, before)
        assertEquals(CampusCounts(2, 9, 8), like.counts)
        assertEquals(CampusViewerState(false, true, false), like.viewer)
        val bookmark = rollbackBookmark(changed, before)
        assertEquals(CampusCounts(3, 9, 4), bookmark.counts)
        assertEquals(CampusViewerState(true, false, false), bookmark.viewer)
    }
}
