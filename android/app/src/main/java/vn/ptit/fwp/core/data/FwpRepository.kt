package vn.ptit.fwp.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import vn.ptit.fwp.core.data.local.FwpDatabase
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
import vn.ptit.fwp.core.model.MyProfileDraft
import vn.ptit.fwp.core.model.NotificationType
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.Swipe
import vn.ptit.fwp.core.model.SwipeAction
import vn.ptit.fwp.core.model.SwipeOutcome
import vn.ptit.fwp.core.model.UniversityCatalog
import java.util.UUID

class FwpRepository(
    private val db: FwpDatabase,
    private val settingsStore: SettingsStore
) {
    private val profileDao = db.profileDao()
    private val swipeDao = db.swipeDao()
    private val matchDao = db.matchDao()
    private val messageDao = db.messageDao()
    private val notificationDao = db.notificationDao()

    val myProfile: Flow<Profile?> = profileDao.observeMe().map { it?.toDomain() }.demoOnly(settingsStore.settings, null)
    val profiles: Flow<List<Profile>> = profileDao.observeProfiles().map { list -> list.map { it.toDomain() } }.demoOnly(settingsStore.settings, emptyList())
    val swipes: Flow<List<Swipe>> = swipeDao.observeSwipes().map { list -> list.map { it.toDomain() } }.demoOnly(settingsStore.settings, emptyList())
    val matches: Flow<List<FwpMatch>> = matchDao.observeAll().map { list -> list.map { it.toDomain() } }.demoOnly(settingsStore.settings, emptyList())
    val notifications: Flow<List<FwpNotification>> = notificationDao.observeAll().map { list -> list.map { it.toDomain() } }.demoOnly(settingsStore.settings, emptyList())
    val unreadNotifications: Flow<Int> = notificationDao.observeUnreadCount().demoOnly(settingsStore.settings, 0)

    fun messages(matchId: String): Flow<List<ChatMessage>> =
        if (isDemoMatchId(matchId))
            messageDao.observeByMatch(matchId).map { list -> list.map { it.toDomain() } }.demoOnly(settingsStore.settings, emptyList())
        else flowOf(emptyList())

    fun match(matchId: String): Flow<FwpMatch?> =
        if (isDemoMatchId(matchId))
            matchDao.observeById(matchId).map { it?.toDomain() }.demoOnly(settingsStore.settings, null)
        else flowOf(null)

    fun profile(id: String): Flow<Profile?> =
        if (id == "me" || isDemoProfileId(id))
            profileDao.observeById(id).map { it?.toDomain() }.demoOnly(settingsStore.settings, null)
        else flowOf(null)

    private suspend fun isDemoSession(): Boolean = settingsStore.settings.first().let {
        allowsLocalDemo(it.authenticated, it.localDemoSession)
    }

    suspend fun initialize() {
        val existing = profileDao.getAll().count { it.id != "me" }
        if (existing == 0) {
            profileDao.upsertAll(DemoSeed.profiles.map { it.toEntity() })
        }
        val my = profileDao.getById("me")
        if (my != null) {
            seedHistoryIfNeeded()
        }
    }

    suspend fun saveMyProfile(draft: MyProfileDraft): Profile {
        check(isDemoSession()) { "Hồ sơ Room chỉ được lưu trong phiên demo." }
        val tier = GpaTier.fromGpa(draft.gpa)
        val profile = Profile(
            id = "me",
            nickname = draft.nickname.trim(),
            fullName = draft.nickname.trim(),
            age = draft.age,
            gender = draft.gender,
            school = draft.school,
            campus = draft.campus,
            faculty = draft.faculty,
            major = draft.major,
            academicYear = draft.academicYear,
            alumni = draft.alumni,
            gpa = draft.gpa,
            gpaTier = tier,
            bio = draft.bio.trim(),
            interests = draft.interests.toList(),
            avatarSeed = draft.avatarSeed,
            distanceKm = 0.0,
            verified = UniversityCatalog.isVerifiedEmail(draft.email) || draft.email.isBlank(),
            isDemo = true,
            showGpa = draft.showGpa,
            showLocation = draft.showLocation,
            mode = draft.mode,
            photoUrl = draft.photoUrl
        )
        profileDao.upsert(profile.toEntity())
        settingsStore.completeProfile()
        settingsStore.setShowGpa(draft.showGpa)
        settingsStore.setShowLocation(draft.showLocation)
        seedHistoryIfNeeded()
        return profile
    }

    suspend fun updateMyProfile(profile: Profile) {
        if (!isDemoSession()) return
        profileDao.upsert(profile.copy(id = "me").toEntity())
    }

    private suspend fun seedHistoryIfNeeded() {
        if (matchDao.count() > 0 || swipeDao.getLast() != null || messageDao.getLast() != null) return
        val now = System.currentTimeMillis()
        DemoSeed.matches(now).forEach { matchDao.upsert(it) }
        DemoSeed.messages(now).forEach { messageDao.insert(it) }
        notificationDao.upsertAll(DemoSeed.notifications(now))
    }

    suspend fun swipe(targetId: String, action: SwipeAction): SwipeOutcome? {
        if (!isDemoSession() || !isDemoProfileId(targetId)) return null
        val profile = profileDao.getById(targetId)?.toDomain() ?: return null
        swipeDao.upsert(
            SwipeEntity(
                targetId = targetId,
                action = action.name,
                createdAt = System.currentTimeMillis()
            )
        )
        if (action == SwipeAction.PASS) {
            return SwipeOutcome(false, null, profile, action)
        }
        val shouldMatch = profile.likedYou || matchDao.getByProfile(targetId) != null
        if (!shouldMatch) {
            return SwipeOutcome(false, null, profile, action)
        }
        val existing = matchDao.getByProfile(targetId)
        val match = existing ?: MatchEntity(
            id = "match-$targetId",
            profileId = targetId,
            mode = profile.mode.name,
            matchedAt = System.currentTimeMillis(),
            lastMessage = null,
            lastMessageAt = null,
            unreadCount = 0,
            compatibility = 0,
            revealOptInMe = false,
            revealOptInThem = false
        ).also { matchDao.upsert(it) }
        addNotification(
            NotificationType.MATCH,
            "Bạn có match mới",
            "${profile.nickname} cũng đã thích bạn. Hãy mở lời thật tự nhiên nhé!"
        )
        return SwipeOutcome(true, match.id, profile, action)
    }

    suspend fun resetSwipes() {
        if (!isDemoSession()) return
        swipeDao.clear()
    }

    suspend fun rewind(): Boolean {
        if (!isDemoSession()) return false
        val last = swipeDao.getLast() ?: return false
        swipeDao.delete(last.targetId)
        val match = matchDao.getByProfile(last.targetId)
        if (match != null) {
            val count = messageDao.countForMatch(match.id)
            if (count == 0) {
                matchDao.delete(match.id)
            }
        }
        return true
    }

    suspend fun sendMessage(
        matchId: String,
        senderId: String,
        text: String,
        type: MessageType = MessageType.TEXT
    ) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        val now = System.currentTimeMillis()
        messageDao.insert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                matchId = matchId,
                senderId = senderId,
                text = text,
                type = type.name,
                createdAt = now,
                isRead = senderId == "me"
            )
        )
        val match = matchDao.getById(matchId) ?: return
        val unread = if (senderId == "me") 0 else match.unreadCount + 1
        matchDao.upsert(
            match.copy(
                lastMessage = if (type == MessageType.IMAGE) "Đã gửi một ảnh" else text,
                lastMessageAt = now,
                unreadCount = unread
            )
        )
        if (senderId == "me") {
            val count = messageDao.countForMatch(matchId)
            if (count >= 8 && !match.revealOptInThem) {
                matchDao.upsert(matchDao.getById(matchId)!!.copy(revealOptInThem = true))
            }
        }
    }

    suspend fun markRead(matchId: String) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        messageDao.markIncomingRead(matchId)
        matchDao.clearUnread(matchId)
    }

    suspend fun updateCompatibility(matchId: String, score: Int) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        val match = matchDao.getById(matchId) ?: return
        matchDao.upsert(match.copy(compatibility = score))
    }

    suspend fun setRevealOptIn(matchId: String, enabled: Boolean) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        val match = matchDao.getById(matchId) ?: return
        matchDao.upsert(match.copy(revealOptInMe = enabled))
    }

    suspend fun setPartnerRevealOptIn(matchId: String, enabled: Boolean) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        val match = matchDao.getById(matchId) ?: return
        matchDao.upsert(match.copy(revealOptInThem = enabled))
    }

    suspend fun unmatch(matchId: String) {
        if (!isDemoSession() || !isDemoMatchId(matchId)) return
        messageDao.deleteByMatch(matchId)
        matchDao.delete(matchId)
    }

    suspend fun blockProfile(profileId: String, reason: String? = null) {
        if (!isDemoSession() || !isDemoProfileId(profileId)) return
        profileDao.setBlocked(profileId, true)
        val match = matchDao.getByProfile(profileId)
        if (match != null) {
            messageDao.deleteByMatch(match.id)
            matchDao.delete(match.id)
        }
        addNotification(
            NotificationType.SAFETY,
            "Đã chặn người dùng",
            reason ?: "Người này sẽ không xuất hiện trong danh sách gợi ý của bạn nữa."
        )
    }

    suspend fun reportProfile(profileId: String, reason: String) {
        if (!isDemoSession() || !isDemoProfileId(profileId)) return
        profileDao.setBlocked(profileId, true)
        val match = matchDao.getByProfile(profileId)
        if (match != null) {
            messageDao.deleteByMatch(match.id)
            matchDao.delete(match.id)
        }
        addNotification(
            NotificationType.SAFETY,
            "Đã gửi báo cáo",
            "Cảm ơn bạn đã giúp FwP an toàn hơn. Lý do: $reason"
        )
    }

    suspend fun addNotification(type: NotificationType, title: String, body: String) {
        if (!isDemoSession()) return
        notificationDao.upsert(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                type = type.name,
                title = title,
                body = body,
                createdAt = System.currentTimeMillis(),
                read = false
            )
        )
    }

    suspend fun markNotificationsRead() {
        if (isDemoSession()) notificationDao.markAllRead()
    }

    suspend fun createDoubleDate(firstMatchId: String, secondMatchId: String): FwpMatch? {
        if (!isDemoSession() || !isDemoMatchId(firstMatchId) || !isDemoMatchId(secondMatchId)) return null
        val first = matchDao.getById(firstMatchId) ?: return null
        val second = matchDao.getById(secondMatchId) ?: return null
        if (first.id == second.id) return null
        val p1 = profileDao.getById(first.profileId)?.toDomain() ?: return null
        val p2 = profileDao.getById(second.profileId)?.toDomain() ?: return null
        val now = System.currentTimeMillis()
        val group = MatchEntity(
            id = "double-${now}",
            profileId = first.profileId,
            mode = ConnectionMode.FRIENDS.name,
            matchedAt = now,
            lastMessage = "Phòng Double Date đã được tạo",
            lastMessageAt = now,
            unreadCount = 0,
            isGroup = true,
            groupTitle = "Double Date · ${p1.nickname} & ${p2.nickname}",
            compatibility = (first.compatibility + second.compatibility) / 2,
            revealOptInMe = true,
            revealOptInThem = true
        )
        matchDao.upsert(group)
        messageDao.insert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                matchId = group.id,
                senderId = "system",
                text = "Double Date đã được tạo! ${p1.nickname} và ${p2.nickname} cùng tham gia. Hãy chọn một chủ đề chung để phá băng nhé.",
                type = MessageType.SYSTEM.name,
                createdAt = now,
                isRead = true
            )
        )
        addNotification(
            NotificationType.MATCH,
            "Double Date đã sẵn sàng",
            "Bạn và ${p1.nickname}, ${p2.nickname} đã có một phòng trò chuyện chung."
        )
        return group.toDomain()
    }

    suspend fun clearAll() {
        messageDao.clear()
        matchDao.clear()
        swipeDao.clear()
        notificationDao.clear()
        profileDao.deleteMe()
        profileDao.deleteDemoProfiles()
        settingsStore.reset()
    }
}
