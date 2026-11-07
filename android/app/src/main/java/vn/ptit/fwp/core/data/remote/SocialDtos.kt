package vn.ptit.fwp.core.data.remote

/** Public/possibly masked profile view; deliberately separate from the owner's profile DTO. */
data class SocialProfileDto(
    val id: String, val userId: String, val anonymous: Boolean = false,
    val nickname: String = "", val fullName: String = "", val age: Int = 0, val gender: String = "",
    val school: String = "", val campus: String = "", val faculty: String = "", val major: String = "",
    val academicYear: String = "", val alumni: Boolean = false, val gpa: Double? = null, val gpaTier: String? = null,
    val bio: String = "", val interests: List<String> = emptyList(), val avatarSeed: Int = 1,
    val mode: String = "", val distanceKm: Double = 0.0, val verified: Boolean = false,
    val showGpaPublic: Boolean = false, val showLocationPublic: Boolean = false,
    val compatibility: Int? = null, val likedAt: String? = null, val superLike: Boolean = false
)
data class SocialProfilesDto(val total: Int, val items: List<SocialProfileDto>)
data class SocialSwipeRequest(val targetUserId: String, val action: String)
data class SocialSwipeDto(val matched: Boolean, val match: SocialMatchDto? = null)
data class SocialMatchesDto(val total: Int, val items: List<SocialMatchDto>)
data class SocialMatchDto(
    val id: String, val anonymous: Boolean, val other: SocialProfileDto,
    val compatibilityScore: Int, val healthLevel: String,
    val revealOptInMe: Boolean, val revealOptInThem: Boolean, val revealEligible: Boolean,
    val messageCount: Int, val lastMessage: SocialLastMessageDto? = null,
    val lastMessageAt: String? = null, val createdAt: String
)
data class SocialLastMessageDto(val id: String, val text: String, val senderId: String, val type: String, val createdAt: String)
data class SocialRevealRequest(val enabled: Boolean)
data class SocialReportRequest(val reason: String, val details: String? = null)
data class SocialActionDto(val unmatched: Boolean = false, val reported: Boolean = false, val blocked: Boolean = false)
data class SocialMessageDto(
    val id: String, val matchId: String, val senderId: String, val text: String, val type: String,
    val createdAt: String, val isMine: Boolean, val clientMessageId: String? = null
)
data class SocialMessagesDto(val total: Int, val hasMore: Boolean, val nextBefore: String?, val items: List<SocialMessageDto>)
data class SocialSendRequest(val clientMessageId: String, val text: String, val type: String = "TEXT")
data class SocialNotificationDto(val id: String, val kind: String, val title: String, val body: String, val read: Boolean,
    val createdAt: String, val data: SocialNotificationData? = null)
data class SocialNotificationData(val matchId: String? = null)
data class SocialNotificationsDto(val unread: Int, val items: List<SocialNotificationDto>)
data class SocialReadRequest(val ids: List<String>? = null)
data class SocialReadDto(val updated: Boolean, val unread: Int)
