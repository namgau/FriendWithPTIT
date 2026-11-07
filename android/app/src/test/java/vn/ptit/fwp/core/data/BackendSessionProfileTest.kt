package vn.ptit.fwp.core.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import vn.ptit.fwp.core.data.remote.*
import vn.ptit.fwp.core.model.Gender
import java.io.IOException
import java.lang.reflect.Proxy
import java.lang.reflect.UndeclaredThrowableException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException

class BackendSessionProfileTest {
    private class Storage(var value: BackendSession? = null) : SessionStorage {
        override fun read() = value
        override fun write(session: BackendSession) { value = session }
        override fun clear() { value = null }
    }
    private class Settings : AuthStateStorage {
        var signedIn = false
        override suspend fun setAuthenticatedSession(email: String, profileComplete: Boolean) { signedIn = true }
        override suspend fun signOut() { signedIn = false }
    }
    private val user = RemoteUserDto("user-1", "a@example.test")
    private fun profile(version: String = "v1", userId: String = user.id) = RemoteProfileDto(
        id = "profile-1", userId = userId, nickname = "N", fullName = "Name", gender = "FEMALE",
        age = 23, gpa = 3.7, showGpaPublic = false, showLocationPublic = false,
        interests = listOf("A", "B", "C"), updatedAt = version, profileComplete = true
    )
    private fun api(handler: (String, Array<out Any?>) -> Any?): CampusApi = Proxy.newProxyInstance(
        CampusApi::class.java.classLoader, arrayOf(CampusApi::class.java)
    ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as CampusApi
    private fun manager(api: CampusApi, storage: Storage, settings: Settings) =
        BackendSessionManager(CampusApiFactory("https://example.test"), storage, settings) { api }
    private fun unauthorized() = HttpException(Response.error<Any>(401, "".toResponseBody()))
    @Suppress("UNCHECKED_CAST")
    private fun suspendFailure(args: Array<out Any?>, error: Throwable): Any {
        (args.last() as Continuation<Any?>).resumeWithException(error)
        return COROUTINE_SUSPENDED
    }

    @Test fun createsRestoresAndLogsOutTheSameIdentity() = runBlocking {
        val storage = Storage(); val settings = Settings(); var login = 0
        val fake = api { name, _ -> when (name) {
            "login" -> { login++; AuthSessionDto("token", user = user) }
            "authMe" -> AuthMeDto(user, true, profile())
            else -> error(name)
        } }
        val sessions = manager(fake, storage, settings)
        sessions.signIn(user.email, "password", false)
        assertEquals(BackendSession("token", user.id, user.email, "https://example.test"), storage.value)
        assertTrue(settings.signedIn)
        assertEquals(user.id, manager(fake, storage, settings).restore().user.id)
        assertEquals(1, login)
        sessions.logout()
        assertNull(storage.value); assertFalse(settings.signedIn)
    }

    @Test fun unauthorizedAndExpiredSessionsAreClearedWithoutDemoFallback() = runBlocking {
        val storage = Storage(BackendSession("expired", user.id, user.email, "https://example.test")); val settings = Settings().apply { signedIn = true }
        val calls = mutableListOf<String>()
        val sessions = manager(api { name, _ -> calls += name; throw unauthorized() }, storage, settings)
        try { sessions.restore(); fail("expected 401") } catch (_: HttpException) { }
        assertNull(storage.value); assertFalse(settings.signedIn)
        try { sessions.restore(); fail("expected no session") } catch (_: SessionRequiredException) { }
        assertEquals(listOf("authMe"), calls)
    }

    @Test fun networkFailurePreservesSessionAndLoginFailureNeverCreatesOne() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings().apply { signedIn = true }
        val sessions = manager(api { _, _ -> throw IOException("offline") }, storage, settings)
        try { sessions.restore(); fail() } catch (e: UndeclaredThrowableException) { assertTrue(e.cause is IOException) }
        assertNotNull(storage.value); assertTrue(settings.signedIn)
        sessions.logout()
        try { sessions.signIn(user.email, "wrong", false); fail() } catch (e: UndeclaredThrowableException) { assertTrue(e.cause is IOException) }
        assertNull(storage.value); assertFalse(settings.signedIn)
    }

    @Test fun wrongIdentityCannotBeRestoredOrWritten() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings().apply { signedIn = true }
        val sessions = manager(api { _, _ -> AuthMeDto(user, true, profile(userId = "different")) }, storage, settings)
        try { sessions.restore(); fail() } catch (_: IdentityMismatchException) { }
        assertNull(storage.value); assertFalse(settings.signedIn)
    }

    @Test fun tokenIsNeverSentToAnotherBackend() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://other.test"))
        val settings = Settings().apply { signedIn = true }; var calls = 0
        val sessions = manager(api { _, _ -> calls++; error("must not call") }, storage, settings)
        try { sessions.restore(); fail() } catch (_: SessionRequiredException) { }
        assertEquals(0, calls); assertNull(storage.value); assertFalse(settings.signedIn)
    }

    @Test fun unauthorizedLoginDoesNotCreateSession() = runBlocking {
        val storage = Storage(); val settings = Settings(); val calls = mutableListOf<String>()
        val sessions = manager(api { name, _ -> calls += name; throw unauthorized() }, storage, settings)
        try { sessions.signIn(user.email, "wrong", false); fail() } catch (_: HttpException) { }
        assertNull(storage.value); assertFalse(settings.signedIn); assertEquals(listOf("login"), calls)
    }

    @Test fun profileMappingPreservesPrivateGpaAndDoesNotInventIdentity() {
        val source = profile()
        val draft = source.toDraft(user.email)
        assertEquals(3.7, draft.gpa, 0.001); assertFalse(draft.showGpa)
        assertEquals("Name", draft.fullName); assertEquals(Gender.FEMALE, draft.gender)
        val request = draft.toBackendUpdate()
        assertEquals(false, request.showGpaPublic); assertEquals(3.7, request.gpa!!, 0.001)
        assertEquals("Name", request.fullName)
    }

    @Test fun syncChecksVersionAndRefetchesAfterSave() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings()
        var version = "v1"; var writes = 0; var nickname = "N"
        val fake = api { name, _ -> when (name) {
            "authMe" -> AuthMeDto(user, true, profile(version).copy(nickname = nickname))
            "updateProfile" -> { writes++; version = "v2"; nickname = "Edited"; profile(version).copy(nickname = nickname) }
            else -> error(name)
        } }
        val repo = BackendProfileRepository(manager(fake, storage, settings))
        val original = repo.load()
        assertEquals("v2", repo.save(original, original.toDraft(user.email).copy(nickname = "Edited")).updatedAt)
        assertEquals(1, writes)
        try { repo.save(original, original.toDraft(user.email)); fail() } catch (_: ProfileConflictException) { }
        assertEquals(1, writes)
    }

    @Test fun acknowledgedPutWithFailedReadIsReconciledWithoutRepeatingWrite() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings()
        var current = profile(); var writes = 0; var reads = 0
        val fake = api { name, args -> when (name) {
            "authMe" -> { reads++; if (reads == 3) suspendFailure(args, IOException("offline after PUT")) else AuthMeDto(user, true, current) }
            "updateProfile" -> { writes++; current = current.copy(nickname = "Edited", updatedAt = "v2"); current }
            else -> error(name)
        } }
        val repo = BackendProfileRepository(manager(fake, storage, settings))
        val original = repo.load(); val draft = original.toDraft(user.email).copy(nickname = "Edited")
        try { repo.save(original, draft); fail() } catch (_: ProfileSavePendingException) { }
        assertEquals("Edited", repo.save(original, draft).nickname)
        assertEquals(1, writes)
    }

    @Test fun retryPreservesEditsMadeAfterUncertainWrite() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings()
        var current = profile(); var writes = 0; var reads = 0
        val fake = api { name, args -> when (name) {
            "authMe" -> { reads++; if (reads == 3) suspendFailure(args, IOException("offline")) else AuthMeDto(user, true, current) }
            "updateProfile" -> {
                val patch = args[1] as UpdateRemoteProfileRequest
                writes++
                current = current.copy(nickname = patch.nickname ?: current.nickname, bio = patch.bio ?: current.bio, updatedAt = "v${writes + 1}")
                current
            }
            else -> error(name)
        } }
        val repo = BackendProfileRepository(manager(fake, storage, settings)); val original = repo.load()
        val draft = original.toDraft(user.email).copy(nickname = "Edited")
        try { repo.save(original, draft); fail() } catch (_: ProfileSavePendingException) { }
        val saved = repo.save(original, draft.copy(nickname = original.nickname, bio = "new bio"))
        assertEquals(original.nickname, saved.nickname); assertEquals("new bio", saved.bio); assertEquals(2, writes)
    }

    @Test fun logoutInvalidatesInFlightLogin() = runBlocking {
        val storage = Storage(); val settings = Settings(); val started = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<AuthSessionDto>()
        val fake = api { name, args -> when (name) {
            "login" -> { started.complete(Unit); reply.invokeOnCompletion { error ->
                @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<AuthSessionDto>
                if (error == null) continuation.resumeWith(Result.success(reply.getCompleted())) else continuation.resumeWith(Result.failure(error))
            }; COROUTINE_SUSPENDED }
            "authMe" -> AuthMeDto(user, true, profile())
            else -> error(name)
        } }
        val sessions = manager(fake, storage, settings)
        val result = async { runCatching { sessions.signIn(user.email, "password", false) } }
        started.await(); sessions.logout(); reply.complete(AuthSessionDto("token", user = user))
        assertTrue(result.await().exceptionOrNull() is SessionRequiredException)
        assertNull(storage.value); assertFalse(settings.signedIn)
    }

    @Test fun oldRequestFailureCannotAffectNewSessionWithSameToken() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings().apply { signedIn = true }
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val sessions = manager(api { name, _ -> when (name) {
            "login" -> AuthSessionDto("token", user = user)
            "authMe" -> AuthMeDto(user, true, profile())
            else -> error(name)
        } }, storage, settings)
        val old = async { runCatching { sessions.withAuth { _, _, _ -> started.complete(Unit); finish.await(); throw unauthorized() } } }
        started.await(); sessions.logout(); sessions.signIn(user.email, "password", false); finish.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is SessionRequiredException)
        assertNotNull(storage.value); assertTrue(settings.signedIn)
    }

    @Test fun oldAccountSuccessIsDiscardedAfterSwitchingBackendAccounts() = runBlocking {
        val storage = Storage(BackendSession("token-a", user.id, user.email, "https://example.test"))
        val settings = Settings().apply { signedIn = true }
        val other = RemoteUserDto("user-2", "b@example.test")
        val sessions = manager(api { name, _ -> when (name) {
            "login" -> AuthSessionDto("token-b", user = other)
            "authMe" -> AuthMeDto(other, true, profile(userId = other.id))
            else -> error(name)
        } }, storage, settings)
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val old = async { runCatching { sessions.withAuth { _, _, account ->
            started.complete(Unit); finish.await(); account.userId
        } } }
        started.await()
        val oldGeneration = sessions.sessionGeneration.value
        sessions.logout()
        assertTrue(sessions.sessionGeneration.value > oldGeneration)
        sessions.signIn(other.email, "password", false)
        finish.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is SessionRequiredException)
        assertEquals(other.id, storage.value?.userId)
        assertEquals(other.id, sessions.restore().user.id)
        assertTrue(settings.signedIn)
    }

    @Test fun sessionChangeDuringProfileReadPreventsStaleUpdate() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings().apply { signedIn = true }
        val started = CompletableDeferred<Unit>(); val reply = CompletableDeferred<AuthMeDto>(); var writes = 0
        val fake = api { name, args -> when (name) {
            "authMe" -> { started.complete(Unit); reply.invokeOnCompletion { error ->
                @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<AuthMeDto>
                if (error == null) continuation.resumeWith(Result.success(reply.getCompleted())) else continuation.resumeWith(Result.failure(error))
            }; COROUTINE_SUSPENDED }
            "updateProfile" -> { writes++; profile() }
            else -> error(name)
        } }
        val sessions = manager(fake, storage, settings); val repo = BackendProfileRepository(sessions); val original = profile()
        val result = async { runCatching { repo.save(original, original.toDraft(user.email).copy(nickname = "Changed")) } }
        started.await(); sessions.logout(); reply.complete(AuthMeDto(user, true, original))
        assertTrue(result.await().exceptionOrNull() is SessionRequiredException); assertEquals(0, writes)
    }

    @Test fun unchangedDraftDoesNotWriteProfile() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); var writes = 0
        val repo = BackendProfileRepository(manager(api { name, _ -> when (name) {
            "authMe" -> AuthMeDto(user, true, profile())
            "updateProfile" -> { writes++; profile() }
            else -> error(name)
        } }, storage, Settings()))
        val original = repo.load()
        assertEquals(original, repo.save(original, original.toDraft(user.email)))
        assertEquals(0, writes)
    }

    @Test fun foreignDraftAndMissingProfileAreNeverSaved() = runBlocking {
        val storage = Storage(BackendSession("token", user.id, user.email, "https://example.test")); val settings = Settings(); var writes = 0
        val fake = api { name, _ -> when (name) {
            "authMe" -> AuthMeDto(user, false, null)
            "updateProfile" -> { writes++; profile() }
            else -> error(name)
        } }
        val repo = BackendProfileRepository(manager(fake, storage, settings))
        try { repo.load(); fail() } catch (_: MissingBackendProfileException) { }
        try { repo.save(profile(userId = "other"), profile().toDraft(user.email)); fail() } catch (_: ProfileConflictException) { }
        assertEquals(0, writes)
    }
}
