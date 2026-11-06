package vn.ptit.fwp.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import vn.ptit.fwp.core.data.remote.RemoteProfileDto
import vn.ptit.fwp.core.data.remote.UpdateRemoteProfileRequest
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.MyProfileDraft
import vn.ptit.fwp.core.model.Profile

class MissingBackendProfileException : IllegalStateException("Không tìm thấy hồ sơ backend. Hãy thử lại hoặc liên hệ hỗ trợ.")
class ProfileConflictException : IllegalStateException("Hồ sơ trên server khác với bản đang sửa. Hãy kiểm tra lại trước khi tiếp tục; bản sửa vẫn được giữ trên màn hình.")
class ProfileSavePendingException : IllegalStateException("Chưa xác nhận được kết quả lưu. Bản sửa vẫn được giữ; hãy kiểm tra lại khi có mạng.")

class BackendProfileRepository(private val sessions: BackendSessionManager) {
    private data class PendingSave(
        val session: BackendSession,
        val generation: Long,
        val original: RemoteProfileDto,
        val request: UpdateRemoteProfileRequest
    )
    private val saveMutex = Mutex()
    private var pending: PendingSave? = null

    suspend fun load(): RemoteProfileDto = sessions.restore().profile?.also {
        if (it.gpa == null || it.updatedAt == null) throw IllegalStateException("Hồ sơ backend thiếu dữ liệu cần thiết. Hãy thử lại.")
    } ?: throw MissingBackendProfileException()

    suspend fun save(original: RemoteProfileDto, draft: MyProfileDraft): RemoteProfileDto = saveMutex.withLock {
        sessions.withAuthLease { api, authorization, lease ->
            val session = lease.session
            if (original.userId != session.userId || !draft.email.equals(session.email, ignoreCase = true))
                throw ProfileConflictException()
            val me = api.authMe(authorization)
            sessions.requireCurrent(lease)
            verifyIdentity(session, me)
            val latest = me.profile ?: throw MissingBackendProfileException()
            val previous = pending?.takeIf { it.session == session && it.generation == lease.generation && it.original.id == original.id && it.original.updatedAt == original.updatedAt }
            val acknowledged = previous != null && matchesRequested(latest, previous.request)
            if (acknowledged) {
                // Preserve any edits made since the uncertain request; only the
                // acknowledged fields advance to the server's current version.
                pending = null
            } else {
                if (profileVersionChanged(original, latest)) throw ProfileConflictException()
                pending = null
            }
            val desired = draft.toBackendUpdate()
            val request = draft.changedBackendUpdate(original)
                .includePreviouslySubmitted(desired, if (acknowledged) previous?.request else null)
                .excludingMatched(latest)
            if (request == UpdateRemoteProfileRequest()) return@withAuthLease latest
            sessions.requireCurrent(lease)
            pending = PendingSave(session, lease.generation, original, request)
            try {
                api.updateProfile(authorization, request)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                sessions.requireCurrent(lease)
                if (error is java.io.IOException || (error is HttpException && error.code() >= 500)) {
                    throw ProfileSavePendingException()
                }
                pending = null
                throw error
            }
            sessions.requireCurrent(lease)
            val saved = try { api.authMe(authorization) } catch (error: Exception) {
                if (error is CancellationException || (error is HttpException && error.code() == 401)) throw error
                sessions.requireCurrent(lease)
                throw ProfileSavePendingException()
            }
            sessions.requireCurrent(lease)
            verifyIdentity(session, saved)
            val current = saved.profile ?: throw MissingBackendProfileException()
            if (!matchesRequested(current, request)) throw ProfileConflictException()
            pending = null
            current
        }
    }

    suspend fun reconcile(original: RemoteProfileDto, draft: MyProfileDraft): RemoteProfileDto =
        sessions.withAuthLease { api, authorization, lease ->
            val session = lease.session
            if (original.userId != session.userId || !draft.email.equals(session.email, ignoreCase = true)) throw ProfileConflictException()
            val me = api.authMe(authorization)
            sessions.requireCurrent(lease)
            verifyIdentity(session, me)
            val current = me.profile ?: throw MissingBackendProfileException()
            if (matchesRequested(current, draft.changedBackendUpdate(original))) current
            else if (profileVersionChanged(original, current)) throw ProfileConflictException()
            else throw IllegalStateException("Chưa ghi nhận thay đổi. Bạn có thể thử lưu lại.")
        }
}

private fun UpdateRemoteProfileRequest.includePreviouslySubmitted(
    desired: UpdateRemoteProfileRequest, previous: UpdateRemoteProfileRequest?
): UpdateRemoteProfileRequest = if (previous == null) this else copy(
    nickname = nickname ?: desired.nickname.takeIf { previous.nickname != null },
    fullName = fullName ?: desired.fullName.takeIf { previous.fullName != null },
    age = age ?: desired.age.takeIf { previous.age != null }, gender = gender ?: desired.gender.takeIf { previous.gender != null },
    school = school ?: desired.school.takeIf { previous.school != null }, campus = campus ?: desired.campus.takeIf { previous.campus != null },
    faculty = faculty ?: desired.faculty.takeIf { previous.faculty != null }, major = major ?: desired.major.takeIf { previous.major != null },
    academicYear = academicYear ?: desired.academicYear.takeIf { previous.academicYear != null },
    alumni = alumni ?: desired.alumni.takeIf { previous.alumni != null }, gpa = gpa ?: desired.gpa.takeIf { previous.gpa != null },
    bio = bio ?: desired.bio.takeIf { previous.bio != null }, interests = interests ?: desired.interests.takeIf { previous.interests != null },
    avatarSeed = avatarSeed ?: desired.avatarSeed.takeIf { previous.avatarSeed != null }, mode = mode ?: desired.mode.takeIf { previous.mode != null },
    showGpaPublic = showGpaPublic ?: desired.showGpaPublic.takeIf { previous.showGpaPublic != null },
    showLocationPublic = showLocationPublic ?: desired.showLocationPublic.takeIf { previous.showLocationPublic != null }
)

private fun UpdateRemoteProfileRequest.excludingMatched(profile: RemoteProfileDto) = copy(
    nickname = nickname?.takeIf { it != profile.nickname }, fullName = fullName?.takeIf { it != profile.fullName },
    age = age?.takeIf { it != profile.age }, gender = gender?.takeIf { it != profile.gender },
    school = school?.takeIf { it != profile.school }, campus = campus?.takeIf { it != profile.campus },
    faculty = faculty?.takeIf { it != profile.faculty }, major = major?.takeIf { it != profile.major },
    academicYear = academicYear?.takeIf { it != profile.academicYear }, alumni = alumni?.takeIf { it != profile.alumni },
    gpa = gpa?.takeIf { it != profile.gpa }, bio = bio?.takeIf { it != profile.bio },
    interests = interests?.takeIf { it.toSet() != profile.interests.orEmpty().toSet() },
    avatarSeed = avatarSeed?.takeIf { it != profile.avatarSeed }, mode = mode?.takeIf { it != profile.mode },
    showGpaPublic = showGpaPublic?.takeIf { it != profile.showGpaPublic },
    showLocationPublic = showLocationPublic?.takeIf { it != profile.showLocationPublic }
)

private fun matchesRequested(profile: RemoteProfileDto, request: UpdateRemoteProfileRequest): Boolean =
    (request.nickname == null || profile.nickname == request.nickname) && (request.fullName == null || profile.fullName == request.fullName) &&
        (request.age == null || profile.age == request.age) && (request.gender == null || profile.gender == request.gender) &&
        (request.school == null || profile.school == request.school) && (request.campus == null || profile.campus == request.campus) &&
        (request.faculty == null || profile.faculty == request.faculty) && (request.major == null || profile.major == request.major) &&
        (request.academicYear == null || profile.academicYear == request.academicYear) && (request.alumni == null || profile.alumni == request.alumni) &&
        (request.gpa == null || profile.gpa == request.gpa) && (request.bio == null || profile.bio == request.bio) &&
        (request.interests == null || profile.interests?.toSet() == request.interests.toSet()) &&
        (request.avatarSeed == null || profile.avatarSeed == request.avatarSeed) && (request.mode == null || profile.mode == request.mode) &&
        (request.showGpaPublic == null || profile.showGpaPublic == request.showGpaPublic) &&
        (request.showLocationPublic == null || profile.showLocationPublic == request.showLocationPublic)

internal fun profileVersionChanged(original: RemoteProfileDto, latest: RemoteProfileDto): Boolean =
    original.id != latest.id || original.userId != latest.userId || original.updatedAt == null ||
        latest.updatedAt == null || original.updatedAt != latest.updatedAt

fun RemoteProfileDto.toLocalProfile(): Profile = Profile(
    id = "me", nickname = nickname, fullName = fullName, age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.OTHER),
    school = school, campus = campus, faculty = faculty, major = major, academicYear = academicYear,
    alumni = alumni, gpa = gpa ?: 0.0,
    gpaTier = gpaTier?.let { runCatching { GpaTier.valueOf(it) }.getOrNull() } ?: GpaTier.fromGpa(gpa ?: 0.0),
    bio = bio, interests = interests.orEmpty(), avatarSeed = avatarSeed, distanceKm = 0.0,
    verified = verified, isDemo = false, showGpa = showGpaPublic, showLocation = showLocationPublic,
    mode = runCatching { ConnectionMode.valueOf(mode) }.getOrDefault(ConnectionMode.FRIENDS)
)

fun RemoteProfileDto.toDraft(email: String): MyProfileDraft = MyProfileDraft(
    nickname = nickname, fullName = fullName, age = age, gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.OTHER),
    school = school, campus = campus, faculty = faculty, major = major, academicYear = academicYear,
    alumni = alumni, gpa = gpa ?: 0.0, bio = bio, interests = interests.orEmpty().toSet(), avatarSeed = avatarSeed,
    showGpa = showGpaPublic, showLocation = showLocationPublic,
    mode = runCatching { ConnectionMode.valueOf(mode) }.getOrDefault(ConnectionMode.FRIENDS), email = email
)

fun MyProfileDraft.toBackendUpdate() = UpdateRemoteProfileRequest(
    nickname = nickname.trim(), fullName = fullName.trim(), age = age, gender = if (gender == Gender.ANY) "OTHER" else gender.name,
    school = school, campus = campus, faculty = faculty, major = major, academicYear = academicYear,
    alumni = alumni, gpa = gpa, bio = bio.trim(), interests = interests.toList(), avatarSeed = avatarSeed,
    mode = mode.name, showGpaPublic = showGpa, showLocationPublic = showLocation
)

internal fun MyProfileDraft.changedBackendUpdate(original: RemoteProfileDto): UpdateRemoteProfileRequest {
    val all = toBackendUpdate()
    return UpdateRemoteProfileRequest(
        nickname = all.nickname?.takeIf { it != original.nickname }, fullName = all.fullName?.takeIf { it != original.fullName },
        age = all.age?.takeIf { it != original.age }, gender = all.gender?.takeIf { it != original.gender },
        school = all.school?.takeIf { it != original.school }, campus = all.campus?.takeIf { it != original.campus },
        faculty = all.faculty?.takeIf { it != original.faculty }, major = all.major?.takeIf { it != original.major },
        academicYear = all.academicYear?.takeIf { it != original.academicYear }, alumni = all.alumni?.takeIf { it != original.alumni },
        gpa = all.gpa?.takeIf { it != original.gpa }, bio = all.bio?.takeIf { it != original.bio },
        interests = all.interests?.takeIf { it.toSet() != original.interests.orEmpty().toSet() },
        avatarSeed = all.avatarSeed?.takeIf { it != original.avatarSeed }, mode = all.mode?.takeIf { it != original.mode },
        showGpaPublic = all.showGpaPublic?.takeIf { it != original.showGpaPublic },
        showLocationPublic = all.showLocationPublic?.takeIf { it != original.showLocationPublic }
    )
}
