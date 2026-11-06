package vn.ptit.fwp.core.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import vn.ptit.fwp.BuildConfig
import java.util.concurrent.TimeUnit

data class OtpRequest(val email: String, val code: String)

data class AuthSessionDto(
    val accessToken: String,
    val tokenType: String? = null,
    val expiresIn: String? = null,
    val user: RemoteUserDto? = null,
    val profileComplete: Boolean = false
)

data class RemoteUserDto(
    val id: String,
    val email: String,
    val isDemo: Boolean = false
)

data class RemoteProfileDto(
    val id: String,
    val userId: String,
    val nickname: String = "",
    val fullName: String = "",
    val age: Int = 20,
    val gender: String = "OTHER",
    val school: String = "",
    val campus: String = "",
    val faculty: String = "",
    val major: String = "",
    val academicYear: String = "",
    val alumni: Boolean = false,
    val gpa: Double = 0.0,
    val gpaTier: String = "GOOD",
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val avatarSeed: Int = 1,
    val mode: String = "DATING",
    val verified: Boolean = false,
    val profileComplete: Boolean = false
)

data class UpdateRemoteProfileRequest(
    val nickname: String? = null,
    val fullName: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val school: String? = null,
    val campus: String? = null,
    val faculty: String? = null,
    val major: String? = null,
    val academicYear: String? = null,
    val alumni: Boolean? = null,
    val gpa: Double? = null,
    val gpaTier: String? = null,
    val bio: String? = null,
    val interests: List<String>? = null,
    val avatarSeed: Int? = null,
    val mode: String? = null
)

data class CampusAuthorBadgeDto(
    val alias: String,
    val avatarSeed: Int,
    val discoverable: Boolean,
    val authorToken: String? = null
)

data class CampusMediaDto(
    val id: String,
    val url: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
)

data class CampusCountsDto(
    val likes: Int = 0,
    val comments: Int = 0,
    val bookmarks: Int = 0
)

data class CampusViewerDto(
    val liked: Boolean = false,
    val bookmarked: Boolean = false,
    val isAuthor: Boolean = false
)

data class CampusPostDto(
    val id: String,
    val content: String,
    val category: String,
    val anonymousMode: String,
    val campus: String? = null,
    val createdAt: String,
    val editedAt: String? = null,
    val author: CampusAuthorBadgeDto,
    val media: List<CampusMediaDto> = emptyList(),
    val counts: CampusCountsDto = CampusCountsDto(),
    val viewer: CampusViewerDto = CampusViewerDto()
)

data class CampusFeedDto(
    val items: List<CampusPostDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null
)

data class CreateCampusImageRequest(
    val dataUrl: String,
    val width: Int? = null,
    val height: Int? = null
)

data class CreateCampusPostRequest(
    val content: String?,
    val category: String,
    val anonymousMode: String,
    val images: List<CreateCampusImageRequest> = emptyList()
)
data class UpdateCampusPostRequest(val content: String, val category: String)

data class CampusLikeDto(val liked: Boolean, val likeCount: Int)
data class CampusBookmarkDto(val bookmarked: Boolean, val bookmarkCount: Int)

data class CampusCommentAuthorDto(
    val alias: String,
    val avatarSeed: Int
)

data class CampusCommentDto(
    val id: String,
    val content: String,
    val createdAt: String,
    val author: CampusCommentAuthorDto,
    val isMine: Boolean = false
)

data class CampusCommentsDto(
    val items: List<CampusCommentDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null
)

data class CreateCampusCommentRequest(val content: String)
data class CampusReportRequest(val reason: String, val details: String? = null)
data class CampusReportDto(val reported: Boolean)
data class CampusSwipeRequest(val action: String)
data class CampusSwipeDto(
    val anonymous: Boolean = true,
    val matched: Boolean = false,
    val matchId: String? = null
)

data class CampusSettingsDto(
    val discoveryEnabled: Boolean,
    val showFaculty: Boolean,
    val showCampus: Boolean,
    val showInterests: Boolean,
    val discoverablePosts: Int,
    val revokedTokens: Int? = null,
    val issuedTokens: Int? = null
)

data class UpdateCampusSettingsRequest(
    val discoveryEnabled: Boolean? = null,
    val showFaculty: Boolean? = null,
    val showCampus: Boolean? = null,
    val showInterests: Boolean? = null
)

data class CampusAuthorProfileDto(
    val token: String,
    val alias: String,
    val avatarSeed: Int,
    val bio: String = "",
    val faculty: String? = null,
    val campus: String? = null,
    val interests: List<String>? = null,
    val discoverable: Boolean = true
)

interface CampusApi {
    @GET("campus/settings")
    suspend fun settings(@Header("Authorization") authorization: String): CampusSettingsDto

    @PATCH("campus/settings")
    suspend fun updateSettings(@Header("Authorization") authorization: String, @Body request: UpdateCampusSettingsRequest): CampusSettingsDto
    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpRequest): AuthSessionDto

    @GET("profiles/me")
    suspend fun myProfile(@Header("Authorization") authorization: String): RemoteProfileDto

    @PUT("profiles/me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: UpdateRemoteProfileRequest
    ): RemoteProfileDto

    @GET("campus/posts")
    suspend fun feed(
        @Header("Authorization") authorization: String,
        @Query("tab") tab: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 10
    ): CampusFeedDto

    @GET("campus/posts/mine")
    suspend fun mine(@Header("Authorization") authorization: String, @Query("cursor") cursor: String? = null, @Query("limit") limit: Int = 10): CampusFeedDto

    @PATCH("campus/posts/{id}")
    suspend fun updatePost(@Header("Authorization") authorization: String, @Path("id") id: String, @Body request: UpdateCampusPostRequest): CampusPostDto

    @POST("campus/posts")
    suspend fun createPost(
        @Header("Authorization") authorization: String,
        @Body request: CreateCampusPostRequest
    ): CampusPostDto

    @GET("campus/posts/{id}")
    suspend fun post(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): CampusPostDto

    @DELETE("campus/posts/{id}")
    suspend fun deletePost(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): Map<String, Boolean>

    @POST("campus/posts/{id}/like")
    suspend fun like(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): CampusLikeDto

    @DELETE("campus/posts/{id}/like")
    suspend fun unlike(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): CampusLikeDto

    @POST("campus/posts/{id}/bookmark")
    suspend fun bookmark(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): CampusBookmarkDto

    @DELETE("campus/posts/{id}/bookmark")
    suspend fun unbookmark(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): CampusBookmarkDto

    @GET("campus/posts/{id}/comments")
    suspend fun comments(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null
    ): CampusCommentsDto

    @POST("campus/posts/{id}/comments")
    suspend fun addComment(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: CreateCampusCommentRequest
    ): CampusCommentDto

    @DELETE("campus/posts/{id}/comments/{commentId}")
    suspend fun deleteComment(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("commentId") commentId: String
    ): Map<String, Boolean>

    @POST("campus/posts/{id}/report")
    suspend fun report(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: CampusReportRequest
    ): CampusReportDto

    @POST("campus/posts/{id}/comments/{commentId}/report")
    suspend fun reportComment(@Header("Authorization") authorization: String, @Path("id") id: String, @Path("commentId") commentId: String, @Body request: CampusReportRequest): CampusReportDto

    @GET("campus/authors/{token}")
    suspend fun author(
        @Header("Authorization") authorization: String,
        @Path("token") token: String
    ): CampusAuthorProfileDto

    @POST("campus/authors/{token}/swipes")
    suspend fun swipeAuthor(
        @Header("Authorization") authorization: String,
        @Path("token") token: String,
        @Body request: CampusSwipeRequest
    ): CampusSwipeDto
}

class CampusApiFactory(
    baseUrl: String = BuildConfig.BACKEND_BASE_URL
) {
    private val root = baseUrl.trim().trimEnd('/')
    val isConfigured: Boolean = root.isNotBlank()

    val api: CampusApi? = if (isConfigured) {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("$root/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampusApi::class.java)
    } else null

    fun absoluteUrl(path: String): String = when {
        path.isBlank() -> ""
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> root + path
        else -> "$root/$path"
    }
}
