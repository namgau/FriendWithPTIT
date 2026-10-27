package vn.ptit.fwp.core.data

import vn.ptit.fwp.core.data.local.MatchEntity
import vn.ptit.fwp.core.data.local.MessageEntity
import vn.ptit.fwp.core.data.local.NotificationEntity
import vn.ptit.fwp.core.data.local.ProfileEntity
import vn.ptit.fwp.core.data.local.SwipeEntity
import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.FwpMatch
import vn.ptit.fwp.core.model.FwpNotification
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.NotificationType
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.Swipe
import vn.ptit.fwp.core.model.SwipeAction

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    nickname = nickname,
    fullName = fullName,
    age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.OTHER),
    school = school,
    campus = campus,
    faculty = faculty,
    major = major,
    academicYear = academicYear,
    alumni = alumni,
    gpa = gpa,
    gpaTier = runCatching { GpaTier.valueOf(gpaTier) }.getOrDefault(GpaTier.fromGpa(gpa)),
    bio = bio,
    interests = interests.split("|").filter { it.isNotBlank() },
    avatarSeed = avatarSeed,
    distanceKm = distanceKm,
    verified = verified,
    isDemo = isDemo,
    showGpa = showGpa,
    showLocation = showLocation,
    mode = runCatching { ConnectionMode.valueOf(mode) }.getOrDefault(ConnectionMode.DATING),
    likedYou = likedYou,
    blocked = blocked,
    photoUrl = photoUrl
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    nickname = nickname,
    fullName = fullName,
    age = age,
    gender = gender.name,
    school = school,
    campus = campus,
    faculty = faculty,
    major = major,
    academicYear = academicYear,
    alumni = alumni,
    gpa = gpa,
    gpaTier = gpaTier.name,
    bio = bio,
    interests = interests.joinToString("|"),
    avatarSeed = avatarSeed,
    distanceKm = distanceKm,
    verified = verified,
    isDemo = isDemo,
    showGpa = showGpa,
    showLocation = showLocation,
    mode = mode.name,
    likedYou = likedYou,
    blocked = blocked,
    photoUrl = photoUrl
)

fun SwipeEntity.toDomain(): Swipe = Swipe(
    targetId = targetId,
    action = runCatching { SwipeAction.valueOf(action) }.getOrDefault(SwipeAction.PASS),
    createdAt = createdAt
)

fun MatchEntity.toDomain(): FwpMatch = FwpMatch(
    id = id,
    profileId = profileId,
    mode = runCatching { ConnectionMode.valueOf(mode) }.getOrDefault(ConnectionMode.DATING),
    matchedAt = matchedAt,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
    isGroup = isGroup,
    groupTitle = groupTitle,
    compatibility = compatibility,
    revealOptInMe = revealOptInMe,
    revealOptInThem = revealOptInThem,
    blocked = blocked
)

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    matchId = matchId,
    senderId = senderId,
    text = text,
    type = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT),
    createdAt = createdAt,
    isRead = isRead
)

fun NotificationEntity.toDomain(): FwpNotification = FwpNotification(
    id = id,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.SYSTEM),
    title = title,
    body = body,
    createdAt = createdAt,
    read = read
)
