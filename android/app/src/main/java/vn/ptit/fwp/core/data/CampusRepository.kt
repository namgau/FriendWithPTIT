package vn.ptit.fwp.core.data

import vn.ptit.fwp.core.data.remote.CampusApi
import vn.ptit.fwp.core.data.remote.CampusApiFactory
import vn.ptit.fwp.core.data.remote.CampusCommentDto
import vn.ptit.fwp.core.data.remote.CampusReportRequest
import vn.ptit.fwp.core.data.remote.CampusSwipeRequest
import vn.ptit.fwp.core.data.remote.CreateCampusCommentRequest
import vn.ptit.fwp.core.data.remote.CreateCampusImageRequest
import vn.ptit.fwp.core.data.remote.CreateCampusPostRequest
import vn.ptit.fwp.core.data.remote.UpdateCampusSettingsRequest
import vn.ptit.fwp.core.data.remote.UpdateCampusPostRequest
import vn.ptit.fwp.core.model.CampusAuthorProfile
import vn.ptit.fwp.core.model.CampusComment
import vn.ptit.fwp.core.model.CampusCommentPage
import vn.ptit.fwp.core.model.CampusFeedPage
import vn.ptit.fwp.core.model.CampusPost
import vn.ptit.fwp.core.model.CampusSwipeResult
import vn.ptit.fwp.core.model.PickedCampusImage
import vn.ptit.fwp.core.model.toDomain

class CampusRepository(
    private val sessions: BackendSessionManager
) {
    private val apiFactory get() = sessions.apiFactory

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

    private suspend fun <T> withAuth(
        block: suspend (CampusApi, String) -> T
    ): T = sessions.withAuth { api, authorization, _ -> block(api, authorization) }
}
