package vn.ptit.fwp.core.ai

import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.DiscoveryFilters
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile
import kotlin.math.abs
import kotlin.math.roundToInt

object RankingEngine {

    fun rank(
        me: Profile?,
        candidates: List<Profile>,
        filters: DiscoveryFilters
    ): List<RankedProfile> {
        val filtered = candidates.filter { candidate ->
            val modeOk = filters.modes.isEmpty() || candidate.mode in filters.modes
            val ageOk = candidate.age in filters.ageRange
            val gpaOk = filters.gpaTiers.isEmpty() || candidate.gpaTier in filters.gpaTiers
            val schoolOk = !filters.sameSchoolOnly || me == null || candidate.school == me.school
            val distanceOk = !filters.useApproximateLocation || candidate.distanceKm <= filters.maxDistanceKm
            val verifyOk = !filters.onlyVerified || candidate.verified
            modeOk && ageOk && gpaOk && schoolOk && distanceOk && verifyOk
        }
        return filtered
            .map { candidate -> score(me, candidate) }
            .sortedByDescending { it.score }
    }

    fun score(me: Profile?, candidate: Profile): RankedProfile {
        if (me == null) {
            return RankedProfile(candidate, 50, listOf("Hồ sơ mới"), "Quy tắc FwP (fallback)")
        }
        val shared = me.interests.intersect(candidate.interests.toSet())
        val union = (me.interests + candidate.interests).toSet()
        val jaccard = if (union.isEmpty()) 0f else shared.size.toFloat() / union.size
        val tierDiff = abs(me.gpaTier.ordinal - candidate.gpaTier.ordinal)
        val gpaScore = when (tierDiff) {
            0 -> 1f
            1 -> 0.72f
            2 -> 0.44f
            else -> 0.2f
        }
        val ageDiff = abs(me.age - candidate.age).toFloat()
        val ageScore = (1f - ageDiff / 6f).coerceIn(0f, 1f)
        val distanceScore = (1.0 - candidate.distanceKm / 50.0).coerceIn(0.0, 1.0).toFloat()
        val sameSchool = if (me.school == candidate.school) 1f else 0f
        val sameCampus = if (me.campus == candidate.campus) 1f else 0f
        val sameFaculty = if (me.faculty == candidate.faculty) 1f else 0f
        val modeScore = if (me.mode == candidate.mode) 1f else 0.35f
        val raw: Float =
            jaccard * 32f +
                gpaScore * 20f +
                sameSchool * 9f +
                sameCampus * 4f +
                sameFaculty * 5f +
                ageScore * 10f +
                distanceScore * 10f +
                modeScore * 10f
        val score = (raw + 5f).coerceIn(12f, 99f).roundToInt()
        val reasons = buildList {
            if (shared.isNotEmpty()) add("${shared.size} sở thích chung")
            if (sameCampus == 1f) add("Cùng campus")
            else if (sameSchool == 1f) add("Cùng PTIT")
            when (tierDiff) {
                0 -> add("Cùng tầng GPA")
                1 -> add("Gần tầng GPA")
            }
            if (sameFaculty == 1f) add("Cùng khoa")
            if (candidate.distanceKm <= 3f) add("Cách bạn ${"%.1f".format(candidate.distanceKm)} km")
            if (me.mode != candidate.mode) add("Khác chế độ kết nối")
        }
        return RankedProfile(
            profile = candidate,
            score = score,
            reasons = reasons.take(3).ifEmpty { listOf("Hồ sơ nổi bật") },
            source = "Quy tắc FwP (fallback)"
        )
    }

    fun sharedInterests(me: Profile?, other: Profile): List<String> =
        me?.interests?.intersect(other.interests.toSet())?.toList() ?: emptyList()

    fun gpaTierLabel(tier: GpaTier): String = "Tầng ${tier.label}"

    fun modeLabel(mode: ConnectionMode): String = mode.label
}
