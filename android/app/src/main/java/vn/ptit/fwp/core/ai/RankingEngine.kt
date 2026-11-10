package vn.ptit.fwp.core.ai

import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.GraduationFilter
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile
import kotlin.math.abs
import kotlin.math.roundToInt

object RankingEngine {
    private fun normalise(s: String): String = s.trim().lowercase(java.util.Locale.ROOT)
        .replace("ß", "ss").replace("ς", "σ").replace(Regex("\\s+"), " ")
    private fun knownDistance(p: Profile) = p.showLocation && p.distanceKm >= 0

    fun rank(
        me: Profile?,
        candidates: List<Profile>,
        filters: DiscoveryFilters
    ): List<RankedProfile> {
        val filtered = candidates.filter { candidate ->
            val modeOk = filters.modes.isEmpty() || candidate.mode in filters.modes
            val ageOk = candidate.age in filters.ageRange
            val gpaOk = filters.gpaTiers.isEmpty() || candidate.gpaTier in filters.gpaTiers
            val schoolOk = !filters.sameSchoolOnly || (me != null && me.school.isNotBlank() && candidate.school == me.school)
            val distanceOk = filters.maxDistanceKm >= 50 || (knownDistance(candidate) && candidate.distanceKm <= filters.maxDistanceKm)
            val verifyOk = !filters.onlyVerified || candidate.verified
            val graduationOk = when (filters.graduation) {
                GraduationFilter.ALL -> true
                GraduationFilter.STUDYING -> !candidate.alumni
                GraduationFilter.ALUMNI -> candidate.alumni
            }
            candidate.id != me?.id && !candidate.blocked && modeOk && ageOk && gpaOk && schoolOk && distanceOk && verifyOk && graduationOk
        }
        return filtered
            .map { candidate -> score(me, candidate) }
            .sortedWith(compareByDescending<RankedProfile> { sharedInterests(me, it.profile).size }
                .thenByDescending { me != null && me.mode == it.profile.mode }
                .thenByDescending { if (me != null && me.campus.isNotBlank() && me.campus.equals(it.profile.campus, true)) 2 else if (me != null && me.school.isNotBlank() && me.school.equals(it.profile.school, true)) 1 else 0 }
                .thenByDescending { me != null && me.faculty.isNotBlank() && me.faculty.equals(it.profile.faculty, true) }
                .thenBy { abs((me?.gpaTier?.ordinal ?: 1) - it.profile.gpaTier.ordinal) }
                .thenBy { if (knownDistance(it.profile)) it.profile.distanceKm else Double.POSITIVE_INFINITY }
                .thenBy { abs((me?.age ?: it.profile.age) - it.profile.age) }
                .thenBy { it.profile.id })
    }

    fun score(me: Profile?, candidate: Profile): RankedProfile {
        if (me == null) {
            return RankedProfile(candidate, 50, listOf("Hồ sơ mới"), "Quy tắc FwP (fallback)")
        }
        val shared = sharedInterests(me, candidate)
        val tierDiff = abs(me.gpaTier.ordinal - candidate.gpaTier.ordinal)
        val sameSchool = if (me.school.isNotBlank() && me.school.equals(candidate.school, true)) 1f else 0f
        val sameCampus = if (me.campus.isNotBlank() && me.campus.equals(candidate.campus, true)) 1f else 0f
        val sameFaculty = if (me.faculty.isNotBlank() && me.faculty.equals(candidate.faculty, true)) 1f else 0f
        val count = me.interests.map(::normalise).filter { it.isNotEmpty() }.toSet().size
        val score = (100f * shared.size / count.coerceAtLeast(1)).coerceIn(0f, 100f).roundToInt()
        val reasons = buildList {
            if (shared.isNotEmpty()) add("${shared.size} sở thích chung")
            if (sameCampus == 1f) add("Cùng campus")
            else if (sameSchool == 1f) add("Cùng trường")
            when (tierDiff) {
                0 -> add("Cùng tầng GPA")
                1 -> add("Gần tầng GPA")
            }
            if (sameFaculty == 1f) add("Cùng khoa")
            if (knownDistance(candidate) && candidate.distanceKm <= 3f) add("Cách bạn ${String.format(java.util.Locale.US, "%.1f", candidate.distanceKm)} km")
            if (me.mode != candidate.mode) add("Khác chế độ kết nối")
        }
        return RankedProfile(
            profile = candidate,
            score = score,
            reasons = reasons.ifEmpty { listOf("Hồ sơ phù hợp để bắt đầu trò chuyện") },
            source = "Quy tắc FwP (fallback)"
        )
    }

    fun sharedInterests(me: Profile?, other: Profile): List<String> =
        me?.interests?.distinctBy(::normalise)?.filter { normalise(it).isNotEmpty() && normalise(it) in other.interests.map(::normalise).toSet() }?.map { it.trim() } ?: emptyList()

    fun gpaTierLabel(tier: GpaTier): String = "Tầng ${tier.label}"

    fun modeLabel(mode: ConnectionMode): String = mode.label
}
