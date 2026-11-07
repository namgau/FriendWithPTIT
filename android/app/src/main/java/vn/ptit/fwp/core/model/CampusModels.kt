package vn.ptit.fwp.core.model

import vn.ptit.fwp.core.data.remote.CampusApiFactory
import vn.ptit.fwp.core.data.remote.CampusAuthorProfileDto
import vn.ptit.fwp.core.data.remote.CampusCommentDto
import vn.ptit.fwp.core.data.remote.CampusPostDto
import java.time.Instant
import java.time.OffsetDateTime

enum class CampusCategory(val api: String, val label: String) {
    GENERAL("GENERAL", "Chung"),
    STUDY("STUDY", "Học tập"),
    EVENT("EVENT", "Sự kiện"),
    CONFESSION("CONFESSION", "Tâm sự"),
    MARKET("MARKET", "Mua bán"),
    QUESTION("QUESTION", "Hỏi đáp");

    companion object {
        fun fromApi(value: String): CampusCategory =
            entries.firstOrNull { it.api == value } ?: GENERAL
    }
}

enum class CampusAnonymousMode(
    val api: String,
    val label: String,
    val description: String
) {
    FULLY_ANONYMOUS(
        "FULLY_ANONYMOUS",
        "Ẩn danh hoàn toàn",
        "Không hiện hồ sơ, không thể mở Discover Author."
    ),
    DISCOVERABLE(
        "DISCOVERABLE",
        "Ẩn danh có thể khám phá",
        "Chỉ hiện hồ sơ tối giản; Like/Swipe đi vào luồng match hiện có."
    );

    companion object {
        fun fromApi(value: String): CampusAnonymousMode =
            entries.firstOrNull { it.api == value } ?: FULLY_ANONYMOUS
    }
}

data class CampusAuthorBadge(
    val alias: String,
    val avatarSeed: Int,
    val discoverable: Boolean,
    val authorToken: String? = null
)

data class CampusMedia(
    val id: String,
    val url: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
)

data class CampusCounts(
    val likes: Int = 0,
    val comments: Int = 0,
    val bookmarks: Int = 0
)

data class CampusViewerState(
    val liked: Boolean = false,
    val bookmarked: Boolean = false,
    val isAuthor: Boolean = false
)

data class CampusPost(
    val id: String,
    val content: String,
    val category: CampusCategory,
    val anonymousMode: CampusAnonymousMode,
    val campus: String,
    val createdAt: Long,
    val editedAt: Long? = null,
    val author: CampusAuthorBadge,
    val media: List<CampusMedia> = emptyList(),
    val counts: CampusCounts = CampusCounts(),
    val viewer: CampusViewerState = CampusViewerState()
)

data class CampusFeedPage(
    val items: List<CampusPost>,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class CampusComment(
    val id: String,
    val content: String,
    val createdAt: Long,
    val alias: String,
    val avatarSeed: Int,
    val isMine: Boolean
)

data class CampusCommentPage(
    val items: List<CampusComment>,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class CampusAuthorProfile(
    val token: String,
    val alias: String,
    val avatarSeed: Int,
    val bio: String,
    val faculty: String? = null,
    val campus: String? = null,
    val interests: List<String> = emptyList()
)

data class CampusSwipeResult(
    val matched: Boolean,
    val matchId: String? = null
)

data class PickedCampusImage(
    val dataUrl: String,
    val width: Int? = null,
    val height: Int? = null
)

fun CampusPostDto.toDomain(api: CampusApiFactory): CampusPost = CampusPost(
    id = id,
    content = content,
    category = CampusCategory.fromApi(category),
    anonymousMode = CampusAnonymousMode.fromApi(anonymousMode),
    campus = campus.orEmpty(),
    createdAt = parseCampusTime(createdAt),
    editedAt = editedAt?.let(::parseCampusTime),
    author = CampusAuthorBadge(
        alias = author.alias,
        avatarSeed = author.avatarSeed,
        discoverable = author.discoverable,
        authorToken = author.authorToken
    ),
    media = media.map {
        CampusMedia(
            id = it.id,
            url = api.absoluteUrl(it.url),
            mimeType = it.mimeType,
            width = it.width,
            height = it.height
        )
    },
    counts = CampusCounts(counts.likes, counts.comments, counts.bookmarks),
    viewer = CampusViewerState(
        liked = viewer.liked,
        bookmarked = viewer.bookmarked,
        isAuthor = viewer.isAuthor
    )
)

fun CampusCommentDto.toDomain(): CampusComment = CampusComment(
    id = id,
    content = content,
    createdAt = parseCampusTime(createdAt),
    alias = author.alias,
    avatarSeed = author.avatarSeed,
    isMine = isMine
)

fun CampusAuthorProfileDto.toDomain(): CampusAuthorProfile = CampusAuthorProfile(
    token = token,
    alias = alias,
    avatarSeed = avatarSeed,
    bio = bio,
    faculty = faculty,
    campus = campus,
    interests = interests.orEmpty()
)

private fun parseCampusTime(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilli() }
        .getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
        }
