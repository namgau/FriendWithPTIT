package vn.ptit.fwp.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val fullName: String,
    val age: Int,
    val gender: String,
    val school: String,
    val campus: String,
    val faculty: String,
    val major: String,
    val academicYear: String,
    val alumni: Boolean,
    val gpa: Double,
    val gpaTier: String,
    val bio: String,
    val interests: String,
    val avatarSeed: Int,
    val distanceKm: Double,
    val verified: Boolean,
    val isDemo: Boolean,
    val showGpa: Boolean,
    val showLocation: Boolean,
    val mode: String,
    val likedYou: Boolean = false,
    val blocked: Boolean = false,
    val photoUrl: String? = null
)

@Entity(tableName = "swipes")
data class SwipeEntity(
    @PrimaryKey val targetId: String,
    val action: String,
    val createdAt: Long
)

@Entity(
    tableName = "matches",
    indices = [Index("profileId"), Index("lastMessageAt")]
)
data class MatchEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val mode: String,
    val matchedAt: Long,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val groupTitle: String? = null,
    val compatibility: Int = 0,
    val revealOptInMe: Boolean = false,
    val revealOptInThem: Boolean = false,
    val blocked: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index("matchId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val matchId: String,
    val senderId: String,
    val text: String,
    val type: String,
    val createdAt: Long,
    val isRead: Boolean
)

@Entity(tableName = "notifications", indices = [Index("createdAt")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean
)
