package vn.ptit.fwp.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import vn.ptit.fwp.core.data.remote.CampusApi
import vn.ptit.fwp.core.data.remote.CampusApiFactory
import vn.ptit.fwp.core.data.remote.CampusCommentDto
import vn.ptit.fwp.core.data.remote.CampusReportRequest
import vn.ptit.fwp.core.data.remote.CampusSwipeRequest
import vn.ptit.fwp.core.data.remote.CreateCampusCommentRequest
import vn.ptit.fwp.core.data.remote.CreateCampusImageRequest
import vn.ptit.fwp.core.data.remote.CreateCampusPostRequest
import vn.ptit.fwp.core.data.remote.OtpRequest
import vn.ptit.fwp.core.data.remote.UpdateRemoteProfileRequest
import vn.ptit.fwp.core.data.remote.UpdateCampusSettingsRequest
import vn.ptit.fwp.core.data.remote.UpdateCampusPostRequest
import vn.ptit.fwp.core.model.CampusAuthorProfile
import vn.ptit.fwp.core.model.CampusComment
import vn.ptit.fwp.core.model.CampusCommentPage
import vn.ptit.fwp.core.model.CampusFeedPage
import vn.ptit.fwp.core.model.CampusPost
import vn.ptit.fwp.core.model.CampusSwipeResult
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.PickedCampusImage
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.toDomain

class CampusRepository(
    private val apiFactory: CampusApiFactory,
    private val settingsStore: SettingsStore,
    private val localRepository: FwpRepository
) {
    private val sessionMutex = Mutex()

    data class Session(val api: CampusApi, val authorization: String)

    val isConfigured: Boolean get() = apiFactory.isConfigured

    suspend fun settings() = withAuth { api, authorization -> api.settings(authorization) }

    suspend fun updateSettings(request: UpdateCampusSettingsRequest) =
        withAuth { api, authorization -> api.updateSettings(authorization, request) }

    suspend fun feed(tab: String, cursor: String? = null): CampusFeedPage =
        withAuth { api, authorization ->
            val response = if (tab == "mine") api.mine(authorization, cursor) else api.feed(authorization, tab, cursor)
            CampusFeedPage(
                items = response.items.map { it.toDomain(apiFactory) },
                hasMore = response.hasMore,
                nextCursor = response.nextCursor
            )
        }

    suspend fun createPost(
        content: String,
        category: String,
        anonymousMode: String,
        images: List<PickedCampusImage>
    ): CampusPost = withAuth { api, authorization ->
        val response = api.createPost(
            authorization,
            CreateCampusPostRequest(
                content = content.trim().ifBlank { null },
                category = category,
                anonymousMode = anonymousMode,
                images = images.map {
                    CreateCampusImageRequest(
                        dataUrl = it.dataUrl,
                        width = it.width,
                        height = it.height
                    )
                }
            )
        )
        response.toDomain(apiFactory)
    }

    suspend fun deletePost(postId: String) = withAuth { api, authorization ->
        api.deletePost(authorization, postId)
    }

    suspend fun updatePost(postId: String, content: String, category: String): CampusPost =
        withAuth { api, authorization -> api.updatePost(authorization, postId, UpdateCampusPostRequest(content.trim(), category)).toDomain(apiFactory) }

    suspend fun reportComment(postId: String, commentId: String, reason: String) =
        withAuth { api, authorization -> api.reportComment(authorization, postId, commentId, CampusReportRequest(reason)) }

    suspend fun setLiked(postId: String, liked: Boolean): Pair<Boolean, Int> =
        withAuth { api, authorization ->
            val response = if (liked) {
                api.like(authorization, postId)
            } else {
                api.unlike(authorization, postId)
            }
            response.liked to response.likeCount
        }

    suspend fun setBookmarked(postId: String, bookmarked: Boolean): Pair<Boolean, Int> =
        withAuth { api, authorization ->
            val response = if (bookmarked) {
                api.bookmark(authorization, postId)
            } else {
                api.unbookmark(authorization, postId)
            }
            response.bookmarked to response.bookmarkCount
        }

    suspend fun comments(postId: String, cursor: String? = null): CampusCommentPage =
        withAuth { api, authorization ->
            val response = api.comments(authorization, postId, cursor)
            CampusCommentPage(
                items = response.items.map(CampusCommentDto::toDomain),
                hasMore = response.hasMore,
                nextCursor = response.nextCursor
            )
        }

    suspend fun addComment(postId: String, content: String): CampusComment =
        withAuth { api, authorization ->
            api.addComment(
                authorization,
                postId,
                CreateCampusCommentRequest(content.trim())
            ).toDomain()
        }

    suspend fun deleteComment(postId: String, commentId: String) =
        withAuth { api, authorization ->
            api.deleteComment(authorization, postId, commentId)
        }

    suspend fun reportPost(postId: String, reason: String, details: String? = null) =
        withAuth { api, authorization ->
            api.report(
                authorization,
                postId,
                CampusReportRequest(reason = reason, details = details)
            )
        }

    suspend fun author(token: String): CampusAuthorProfile =
        withAuth { api, authorization ->
            api.author(authorization, token).toDomain()
        }

    suspend fun swipeAuthor(token: String, action: String): CampusSwipeResult =
        withAuth { api, authorization ->
            val response = api.swipeAuthor(
                authorization,
                token,
                CampusSwipeRequest(action = action)
            )
            CampusSwipeResult(
                matched = response.matched,
                matchId = response.matchId
            )
        }

    suspend fun clearSession() = settingsStore.clearBackendSession()

    private suspend fun <T> withAuth(
        block: suspend (CampusApi, String) -> T
    ): T {
        var session = session()
        return try {
            block(session.api, session.authorization)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            settingsStore.clearBackendSession()
            session = session(force = true)
            block(session.api, session.authorization)
        }
    }

    private suspend fun session(force: Boolean = false): Session =
        sessionMutex.withLock {
            val api = apiFactory.api
                ?: throw IllegalStateException(
                    "Campus Feed cần FWP_BACKEND_BASE_URL trỏ tới NestJS backend."
                )
            val settings = settingsStore.settings.first()
            val email = settings.email.ifBlank { DEMO_EMAIL }
            if (!force && settings.backendToken.isNotBlank() &&
                settings.backendEmail.equals(email, ignoreCase = true)
            ) {
                return@withLock Session(api, "Bearer ${settings.backendToken}")
            }
            val auth = api.verifyOtp(OtpRequest(email = email, code = DEMO_OTP))
            settingsStore.setBackendSession(auth.accessToken, email)
            val session = Session(api, "Bearer ${auth.accessToken}")
            syncLocalProfileIfNeeded(api, session.authorization)
            session
        }

    private suspend fun syncLocalProfileIfNeeded(api: CampusApi, authorization: String) {
        val local = localRepository.myProfile.first() ?: return
        val remote = runCatching { api.myProfile(authorization) }.getOrNull()
        if (remote != null && remote.profileComplete && remote.nickname.isNotBlank()) {
            return
        }
        runCatching {
            api.updateProfile(authorization, local.toRemoteProfileRequest())
        }
    }

    private fun Profile.toRemoteProfileRequest() = UpdateRemoteProfileRequest(
        nickname = nickname.ifBlank { "Sinh viên FwP" },
        fullName = fullName.ifBlank { nickname.ifBlank { "Sinh viên FwP" } },
        age = age,
        gender = when (gender) {
            Gender.FEMALE -> "FEMALE"
            Gender.MALE -> "MALE"
            else -> "OTHER"
        },
        school = school,
        campus = campus,
        faculty = faculty,
        major = major,
        academicYear = academicYear,
        alumni = alumni,
        gpa = gpa,
        gpaTier = gpaTier.name,
        bio = bio,
        interests = interests,
        avatarSeed = avatarSeed,
        mode = mode.name
    )

    companion object {
        const val DEMO_EMAIL = "demo@ptit.edu.vn"
        const val DEMO_OTP = "123456"
    }
}
