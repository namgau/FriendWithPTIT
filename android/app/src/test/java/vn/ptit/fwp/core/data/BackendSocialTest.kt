package vn.ptit.fwp.core.data

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Buffer
import vn.ptit.fwp.core.data.remote.*
import vn.ptit.fwp.core.model.*
import vn.ptit.fwp.navigation.Routes
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class BackendSocialTest {
    private val userId = "10000000-0000-4000-8000-000000000001"
    private val otherId = "10000000-0000-4000-8000-000000000002"
    private val matchId = "20000000-0000-4000-8000-000000000001"
    private val profileId = "30000000-0000-4000-8000-000000000001"
    private fun profile() = SocialProfileDto(profileId, otherId, nickname = "Person", fullName = "Name", age = 22,
        school = "PTIT", faculty = "IT", gpa = 3.8, gpaTier = "EXCELLENT", showGpaPublic = true, compatibility = 90)
    private fun match(masked: Boolean = false) = SocialMatchDto(matchId, masked, profile(), 90, "FADING", false, false, false, 0, createdAt = "2026-11-01T00:00:00.000Z")
    private fun fake(handler: (String, Array<out Any?>) -> Any?) = Proxy.newProxyInstance(CampusApi::class.java.classLoader, arrayOf(CampusApi::class.java)) { _, method, args -> handler(method.name, args ?: emptyArray()) } as CampusApi
    private class Store(var value: BackendSession?) : SessionStorage {
        override fun read() = value
        override fun write(session: BackendSession) { value = session }
        override fun clear() { value = null }
    }
    private fun environment(handler: (String, Array<out Any?>) -> Any?): Triple<BackendSessionManager, MutableStateFlow<AppSettings>, BackendSocialSession> {
        val settings = MutableStateFlow(AppSettings(authenticated = true, email = "a@test.invalid"))
        val storage = Store(BackendSession("test-token", userId, settings.value.email, "https://example.test"))
        val manager = BackendSessionManager(CampusApiFactory("https://example.test"), storage, object : AuthStateStorage {
            override suspend fun setAuthenticatedSession(email: String, profileComplete: Boolean) { settings.value = settings.value.copy(authenticated = true, localDemoSession = false, email = email) }
            override suspend fun signOut() { settings.value = settings.value.copy(authenticated = false, localDemoSession = false) }
        }) { fake(handler) }
        return Triple(manager, settings, BackendSocialRepository(manager, settings).forIdentity(presentationIdentity(settings.value, 0)))
    }

    @Test fun socialDtoMappingKeepsProfileAndUserIdsDistinctAndPrivateFieldsHidden() {
        val gson = Gson()
        val dto = gson.fromJson(gson.toJson(profile().copy(showGpaPublic = false, showLocationPublic = false, distanceKm = 42.0)), SocialProfileDto::class.java)
        assertEquals(otherId, dto.userId)
        val display = dto.displayProfile()
        assertEquals(profileId, display.id); assertFalse(display.isDemo)
        assertEquals(0.0, display.gpa, 0.0); assertFalse(display.showGpa)
        assertEquals(0.0, display.distanceKm, 0.0)
    }

    @Test fun anonymousMappingStripsEvenContradictoryHiddenProfileFields() {
        val masked = match(true)
        val display = masked.displayProfile()
        assertEquals("Ẩn danh", display.nickname); assertEquals("", display.school)
        assertTrue(display.interests.isEmpty()); assertFalse(display.showGpa); assertFalse(display.verified)
        assertEquals("masked", display.id); assertEquals(0, masked.displayMatch().compatibility)
        assertEquals("anon:$matchId", masked.blockHandle())
        assertEquals(otherId, match(false).blockHandle())
    }

    @Test fun swipeUsesUserUuidAndReturnsMutualServerMatchWithoutRoomFallback() = runBlocking {
        var count = 0
        val (_, _, api) = environment { name, args ->
            assertEquals("swipe", name); assertEquals("Bearer test-token", args[0])
            assertEquals(otherId, (args[1] as SocialSwipeRequest).targetUserId)
            count++; SocialSwipeDto(count > 1, if (count > 1) match() else null)
        }
        assertFalse(api.swipe(otherId, SwipeAction.LIKE).matched)
        assertEquals(matchId, api.swipe(otherId, SwipeAction.LIKE).match?.id)
        assertEquals(matchId, api.swipe(otherId, SwipeAction.LIKE).match?.id)
        assertEquals(3, count)
    }

    @Test fun maskedHandlesNeverEnterProfileOrOrdinarySwipeEndpoints() = runBlocking {
        var calls = 0
        val (_, _, api) = environment { _, _ -> calls++; error("must not call") }
        for (id in listOf("anon:$matchId", "p01", "author-token")) {
            assertTrue(runCatching { api.profile(id) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { api.swipe(id, SwipeAction.LIKE) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, calls)
    }

    @Test fun opaqueHistoryCursorIsPassedUnchangedAndMessageOwnershipUsesServerFlag() = runBlocking {
        val cursor = "v1.opaque_with-ids+=/?"
        val message = SocialMessageDto(profileId, matchId, "anon:$matchId", "hi", "TEXT", "2026-11-01T00:00:00Z", false)
        val (_, _, api) = environment { name, args ->
            assertEquals("messages", name); assertEquals(cursor, args[2]); SocialMessagesDto(1, false, null, listOf(message))
        }
        assertEquals(message, api.messages(matchId, cursor).items.single())
        assertFalse(message.displayMessage().isMine)
        assertTrue(message.copy(senderId = "anon:$matchId", isMine = true).displayMessage().isMine)
    }

    @Test fun blockReportUnmatchAndRevealUseServerEndpointsAndMaskedBlockHandle() = runBlocking {
        val calls = mutableListOf<String>()
        val (_, _, api) = environment { name, args -> calls += name; when (name) {
            "blockUser" -> { assertEquals("anon:$matchId", args[1]); SocialActionDto(blocked = true) }
            "reportMatch" -> { assertEquals(matchId, args[1]); assertEquals("SPAM", (args[2] as SocialReportRequest).reason); SocialActionDto(reported = true) }
            "unmatch" -> SocialActionDto(unmatched = true)
            "reveal" -> { assertTrue((args[2] as SocialRevealRequest).enabled); match(true).copy(revealOptInMe = true) }
            else -> error(name)
        } }
        api.block(match(true)); api.report(matchId, "SPAM"); api.unmatch(matchId)
        val revealed = api.reveal(matchId, true)
        assertTrue(revealed.anonymous); assertFalse(revealed.revealEligible); assertTrue(revealed.revealOptInMe)
        assertEquals(listOf("blockUser", "reportMatch", "unmatch", "reveal"), calls)
    }

    @Test fun campusMatchResolvesServerMatchBeforeBackendNavigation() = runBlocking {
        val (_, settings, api) = environment { name, args -> assertEquals("match", name); assertEquals(matchId, args[1]); match(true) }
        val campus = CampusSwipeDto(matched = true, matchId = matchId)
        val resolved = api.match(campus.matchId!!)
        assertTrue(resolved.anonymous)
        val identity = presentationIdentity(settings.value, 0)
        assertTrue(allowsBackendRoute(identity, resolved.id))
        assertEquals("chat/$matchId", Routes.chat(resolved.id))
        assertFalse(allowsDemoRoute(true, false, Routes.CHAT, resolved.id))
        assertFalse(allowsBackendRoute(identity, "anon:$matchId"))
        assertFalse(allowsBackendRoute(identity.copy(demo = true), resolved.id))
    }

    @Test fun requestsCannotStartAfterAccountModeOrGenerationChanged() = runBlocking {
        var calls = 0
        val (manager, settings, api) = environment { _, _ -> calls++; error("must not call") }
        val original = settings.value
        settings.value = original.copy(email = "b@test.invalid")
        assertTrue(runCatching { api.matches() }.exceptionOrNull() is SessionRequiredException)
        settings.value = original.copy(localDemoSession = true)
        assertTrue(runCatching { api.matches() }.exceptionOrNull() is SessionRequiredException)
        settings.value = original
        manager.logout()
        assertTrue(runCatching { api.matches() }.exceptionOrNull() is SessionRequiredException)
        assertEquals(0, calls)
    }

    @Test fun logoutDuringRequestDiscardsOldSocialResponse() = runBlocking {
        val started = CompletableDeferred<Unit>(); val reply = CompletableDeferred<SocialMatchesDto>()
        val (manager, _, api) = environment { name, args ->
            assertEquals("matches", name); started.complete(Unit)
            @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<SocialMatchesDto>
            reply.invokeOnCompletion { continuation.resumeWith(Result.success(reply.getCompleted())) }
            COROUTINE_SUSPENDED
        }
        val result = async { runCatching { api.matches() } }
        started.await(); manager.logout(); reply.complete(SocialMatchesDto(1, listOf(match())))
        assertTrue(result.await().exceptionOrNull() is SessionRequiredException)
    }

    @Test fun accountSwitchDuringRequestDiscardsResponseEvenBeforeNewGeneration() = runBlocking {
        val started = CompletableDeferred<Unit>(); val reply = CompletableDeferred<SocialMatchesDto>()
        val (_, settings, api) = environment { _, args ->
            started.complete(Unit)
            @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<SocialMatchesDto>
            reply.invokeOnCompletion { continuation.resumeWith(Result.success(reply.getCompleted())) }; COROUTINE_SUSPENDED
        }
        val result = async { runCatching { api.matches() } }
        started.await(); settings.value = settings.value.copy(email = "b@test.invalid"); reply.complete(SocialMatchesDto(1, listOf(match())))
        assertTrue(result.await().exceptionOrNull() is SessionRequiredException)
    }

    @Test fun unavailableMatchAndConflictArePropagatedWithoutFakeMatchOrNewMessage() = runBlocking {
        val (_, _, api) = environment { name, _ -> throw HttpException(Response.error<Any>(if (name == "match") 404 else 409, "".toResponseBody())) }
        assertEquals(404, (runCatching { api.match(matchId) }.exceptionOrNull() as HttpException).code())
        val request = SocialSendRequest(profileId, "hi")
        assertEquals(409, (runCatching { api.send(matchId, request) }.exceptionOrNull() as HttpException).code())
    }

    @Test fun notificationsAndReadStayOnBackendAndUseMatchUuidNotHiddenProfileId() = runBlocking {
        val calls = mutableListOf<String>()
        val (_, settings, api) = environment { name, _ -> calls += name; when (name) {
            "notifications" -> SocialNotificationsDto(1, listOf(SocialNotificationDto(profileId, "MATCH", "Match ẩn danh", "", false,
                "2026-11-01T00:00:00Z", SocialNotificationData(matchId))))
            "readNotifications" -> SocialReadDto(true, 0)
            else -> error(name)
        } }
        val notification = api.notifications().items.single()
        assertTrue(allowsBackendRoute(presentationIdentity(settings.value, 0), notification.data!!.matchId!!))
        assertEquals(0, api.readNotifications().unread)
        assertEquals(listOf("notifications", "readNotifications"), calls)
    }

    @Test fun retrofitEncodesOpaqueCursorAndSendsSameUuidPayloadAcrossUncertainRetry() = runBlocking {
        val cursor = "v1.ABC_opaque-with/+=?"
        val key = "40000000-0000-4000-8000-000000000001"
        val payloads = mutableListOf<String>(); var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer test-token", request.header("Authorization"))
            val json = if (request.method == "GET") {
                assertEquals("/matches/$matchId/messages", request.url.encodedPath)
                assertEquals(cursor, request.url.queryParameter("before"))
                """{"total":0,"hasMore":false,"nextBefore":null,"items":[]}"""
            } else {
                val buffer = Buffer(); request.body!!.writeTo(buffer); payloads += buffer.readUtf8()
                attempts++
                if (attempts == 1) throw java.io.IOException("simulated lost response")
                Gson().toJson(SocialMessageDto(profileId, matchId, userId, "hello", "TEXT", "2026-11-01T00:00:00Z", true, key))
            }
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val retrofit = Retrofit.Builder().baseUrl("https://example.test/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(CampusApi::class.java)
        val settings = MutableStateFlow(AppSettings(authenticated = true, email = "a@test.invalid"))
        val storage = Store(BackendSession("test-token", userId, settings.value.email, "https://example.test"))
        val manager = BackendSessionManager(CampusApiFactory("https://example.test"), storage, object : AuthStateStorage {
            override suspend fun setAuthenticatedSession(email: String, profileComplete: Boolean) {}
            override suspend fun signOut() {}
        }) { retrofit }
        val api = BackendSocialRepository(manager, settings).forIdentity(presentationIdentity(settings.value, 0))
        api.messages(matchId, cursor)
        val request = SocialSendRequest(key, "hello")
        assertTrue(runCatching { api.send(matchId, request) }.exceptionOrNull() is java.io.IOException)
        assertEquals(profileId, api.send(matchId, request).id)
        assertEquals(2, payloads.size); assertEquals(payloads[0], payloads[1])
        assertEquals(key, Gson().fromJson(payloads[1], SocialSendRequest::class.java).clientMessageId)
        client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
}
