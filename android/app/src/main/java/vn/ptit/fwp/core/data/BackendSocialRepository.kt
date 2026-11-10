package vn.ptit.fwp.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import vn.ptit.fwp.core.data.remote.*
import vn.ptit.fwp.core.model.*
import java.time.Instant

internal val backendUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal fun requireBackendId(id: String): String = id.also { require(backendUuid.matches(it)) { "Định danh backend không hợp lệ." } }

/** Every operation is bound to the UI's original identity, including at request entry. */
class BackendSocialRepository(private val sessions: BackendSessionManager, private val settings: Flow<AppSettings>) {
    fun forIdentity(identity: PresentationIdentity) = BackendSocialSession(sessions, settings, identity)
}

class BackendSocialSession internal constructor(
    private val sessions: BackendSessionManager, private val settings: Flow<AppSettings>, val identity: PresentationIdentity
) {
    private suspend fun checkIdentity() {
        val current = settings.first()
        if (!identity.authenticated || identity.demo || presentationIdentity(current, sessions.sessionGeneration.value) != identity)
            throw SessionRequiredException()
    }
    private suspend fun <T> call(block: suspend (CampusApi, String) -> T): T {
        checkIdentity()
        return sessions.withAuthLease { api, auth, lease ->
            if (lease.generation != identity.generation || !lease.session.email.equals(identity.email, true)) throw SessionRequiredException()
            checkIdentity()
            val value = block(api, auth)
            checkIdentity()
            value
        }
    }
    suspend fun discover(offset: Int, filters: DiscoveryFilters) = call { api, auth ->
        val school = if (filters.sameSchoolOnly) {
            val owner = api.authMe(auth)
            check(owner.user.email.equals(identity.email, true)) { "Danh tính backend không khớp." }
            owner.profile?.school?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Hồ sơ chưa có trường học.")
        } else null
        api.discover(auth, offset, modes = filters.modes.joinToString(",") { it.name },
            tiers = filters.gpaTiers.takeIf { it.size != GpaTier.entries.size }?.joinToString(",") { it.name },
            minAge = filters.ageRange.first, maxAge = filters.ageRange.last,
            distance = filters.maxDistanceKm, verified = filters.onlyVerified, school = school, graduation = filters.graduation.name)
    }
    suspend fun incoming() = call { api, auth -> api.incomingLikes(auth) }
    suspend fun notifications() = call { api, auth -> api.notifications(auth) }
    suspend fun readNotifications() = call { api, auth -> api.readNotifications(auth, SocialReadRequest()) }
    suspend fun profile(id: String) = call { api, auth -> api.socialProfile(auth, requireBackendId(id)).also { requireBackendId(it.userId); check(it.id == id || it.userId == id) } }
    suspend fun swipe(userId: String, action: SwipeAction) = call { api, auth ->
        api.swipe(auth, SocialSwipeRequest(requireBackendId(userId), action.name)).let {
            check(!it.matched || it.match != null) { "Backend chưa trả match hợp lệ." }
            it.copy(match = it.match?.validated())
        }
    }
    suspend fun matches() = call { api, auth -> api.matches(auth).let { it.copy(items = it.items.map(SocialMatchDto::validated)) } }
    suspend fun match(id: String) = call { api, auth -> api.match(auth, requireBackendId(id)).validated().also { check(it.id == id) } }
    suspend fun reveal(id: String, enabled: Boolean) = call { api, auth -> api.reveal(auth, requireBackendId(id), SocialRevealRequest(enabled)).validated().also { check(it.id == id) } }
    suspend fun unmatch(id: String) = call { api, auth -> check(api.unmatch(auth, requireBackendId(id)).unmatched) }
    suspend fun report(id: String, reason: String) = call { api, auth -> check(api.reportMatch(auth, requireBackendId(id), SocialReportRequest(reason.take(64))).reported) }
    suspend fun block(match: SocialMatchDto) = blockHandle(match.blockHandle())
    suspend fun blockUser(userId: String) = blockHandle(requireBackendId(userId))
    private suspend fun blockHandle(handle: String) = call { api, auth -> check(api.blockUser(auth, handle).blocked) }
    suspend fun messages(id: String, before: String? = null) = call { api, auth ->
        api.messages(auth, requireBackendId(id), before).also { page ->
            check(!page.hasMore || !page.nextBefore.isNullOrEmpty()) { "Backend thiếu cursor lịch sử." }
            check(page.items.all { it.matchId == id }) { "Hội thoại không khớp." }
        }
    }
    suspend fun send(id: String, request: SocialSendRequest) = call { api, auth ->
        api.sendMessage(auth, requireBackendId(id), request).also {
            check(it.matchId == id && it.isMine && it.clientMessageId == request.clientMessageId) { "Không xác nhận được tin nhắn." }
        }
    }
}

internal fun SocialMatchDto.validated(): SocialMatchDto = also {
    requireBackendId(id)
    if (!anonymous && !other.anonymous) requireBackendId(other.userId)
}
internal fun SocialMatchDto.blockHandle(): String = if (anonymous || other.anonymous) "anon:${requireBackendId(id)}" else requireBackendId(other.userId)

internal fun SocialProfileDto.displayProfile(masked: Boolean = anonymous): Profile {
    // Defense in depth: even a contradictory response cannot put hidden fields in UI state.
    val hidden = masked || anonymous || userId.startsWith("anon:")
    return Profile(
        id = if (hidden) "masked" else id, nickname = if (hidden) "Ẩn danh" else nickname,
        fullName = if (hidden) "Người ẩn danh" else fullName, age = if (hidden) 0 else age,
        gender = if (hidden) Gender.OTHER else runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.OTHER),
        school = if (hidden) "" else school, campus = if (hidden) "" else campus, faculty = if (hidden) "" else faculty,
        major = if (hidden) "" else major, academicYear = if (hidden) "" else academicYear, alumni = !hidden && alumni,
        gpa = if (hidden || !showGpaPublic) 0.0 else gpa ?: 0.0,
        gpaTier = if (hidden || !showGpaPublic) GpaTier.AVERAGE else runCatching { GpaTier.valueOf(gpaTier.orEmpty()) }.getOrDefault(GpaTier.AVERAGE),
        bio = if (hidden) "" else bio, interests = if (hidden) emptyList() else interests,
        avatarSeed = avatarSeed, distanceKm = if (!hidden && showLocationPublic) distanceKm else 0.0,
        verified = !hidden && verified, isDemo = false, showGpa = !hidden && showGpaPublic && gpa != null,
        showLocation = !hidden && showLocationPublic,
        mode = runCatching { ConnectionMode.valueOf(mode) }.getOrDefault(ConnectionMode.FRIENDS)
    )
}
internal fun SocialMatchDto.displayProfile(): Profile = other.displayProfile(anonymous)
internal fun SocialMatchDto.displayMatch() = FwpMatch(id, other.id, runCatching { ConnectionMode.valueOf(other.mode) }.getOrDefault(ConnectionMode.FRIENDS),
    Instant.parse(createdAt).toEpochMilli(), lastMessage?.text, lastMessageAt?.let { Instant.parse(it).toEpochMilli() },
    compatibility = if (anonymous || other.anonymous || other.userId.startsWith("anon:")) 0 else compatibilityScore, revealOptInMe = revealOptInMe, revealOptInThem = revealOptInThem)
internal fun SocialMessageDto.displayMessage() = ChatMessage(id = id, senderId = if (isMine) "me" else "other", text = text,
    matchId = matchId, type = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT), createdAt = Instant.parse(createdAt).toEpochMilli())
