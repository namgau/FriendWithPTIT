package vn.ptit.fwp.core.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import vn.ptit.fwp.core.data.remote.AuthMeDto
import vn.ptit.fwp.core.data.remote.CampusApi
import vn.ptit.fwp.core.data.remote.CampusApiFactory
import vn.ptit.fwp.core.data.remote.CredentialsRequest

class SessionRequiredException : IllegalStateException("Phiên backend đã hết hạn hoặc chưa đăng nhập. Hãy đăng nhập lại.")
class IdentityMismatchException : IllegalStateException("Danh tính backend không khớp. Hãy đăng nhập lại.")

class BackendSessionManager(
    val apiFactory: CampusApiFactory,
    private val storage: SessionStorage,
    private val settings: AuthStateStorage,
    private val apiProvider: () -> CampusApi? = { apiFactory.api }
) {
    private val mutex = Mutex()
    private val generationState = MutableStateFlow(0L)
    val sessionGeneration = generationState.asStateFlow()
    private var generation: Long
        get() = generationState.value
        set(value) { generationState.value = value }

    internal data class SessionLease(val session: BackendSession, val generation: Long)

    internal suspend fun requireCurrent(lease: SessionLease) {
        mutex.withLock {
            if (generation != lease.generation || storage.read() != lease.session) throw SessionRequiredException()
        }
    }

    private fun api(): CampusApi {
        val root = apiFactory.baseUrl
        if (!vn.ptit.fwp.BuildConfig.DEBUG && !root.startsWith("https://", ignoreCase = true)) {
            throw IllegalStateException("Bản release yêu cầu backend HTTPS.")
        }
        return apiProvider() ?: throw IllegalStateException("Chưa cấu hình FWP_BACKEND_BASE_URL.")
    }

    suspend fun signIn(email: String, password: String, register: Boolean): AuthMeDto {
        val attempt = mutex.withLock { ++generation }
        val request = CredentialsRequest(email.trim(), password)
        val response = if (register) api().register(request) else api().login(request)
        mutex.withLock { if (generation != attempt) throw SessionRequiredException() }
        val user = response.user ?: throw IdentityMismatchException()
        val candidate = BackendSession(response.accessToken, user.id, user.email, apiFactory.baseUrl)
        val me = api().authMe("Bearer ${candidate.token}")
        verifyIdentity(candidate, me)
        mutex.withLock {
            if (generation != attempt) throw SessionRequiredException()
            storage.write(candidate)
            try { settings.setAuthenticatedSession(candidate.email, me.profileComplete) }
            catch (e: Exception) { storage.clear(); throw e }
        }
        return me
    }

    suspend fun restore(): AuthMeDto = withAuth { api, auth, session ->
        api.authMe(auth).also { verifyIdentity(session, it) }
    }

    suspend fun <T> withAuth(block: suspend (CampusApi, String, BackendSession) -> T): T =
        withAuthLease { api, auth, lease -> block(api, auth, lease.session) }

    internal suspend fun <T> withAuthLease(block: suspend (CampusApi, String, SessionLease) -> T): T {
        val lease = mutex.withLock { SessionLease(storage.read() ?: throw SessionRequiredException(), generation) }
        val session = lease.session
        if (session.backendUrl != apiFactory.baseUrl) {
            mutex.withLock {
                if (generation == lease.generation && storage.read() == session) { generation++; storage.clear(); settings.signOut() }
            }
            throw SessionRequiredException()
        }
        return try {
            val result = block(api(), "Bearer ${session.token}", lease)
            requireCurrent(lease)
            result
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            requireCurrent(lease)
            if ((error is HttpException && error.code() == 401) || error is IdentityMismatchException) {
                mutex.withLock {
                    if (generation == lease.generation && storage.read() == session) {
                        generation++
                        storage.clear()
                        settings.signOut()
                    }
                }
            }
            throw error
        }
    }

    suspend fun logout() = mutex.withLock {
        generation++
        storage.clear()
        settings.signOut()
    }
}

internal fun verifyIdentity(session: BackendSession, me: AuthMeDto) {
    if (session.userId != me.user.id || !session.email.equals(me.user.email, ignoreCase = true) ||
        (me.profile != null && me.profile.userId != session.userId)) throw IdentityMismatchException()
}
