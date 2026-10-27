package vn.ptit.fwp.data.model

import com.google.gson.annotations.SerializedName

// Khớp với serving/schemas.py và serving/demo_backend.py.
// Gson đổi tên tự động: camelCase (Kotlin) <-> snake_case (JSON).

const val ME_ID = "u_me"

data class Message(
    val senderId: String,
    val timestampMs: Long,
    val text: String?,
)

data class PublicProfile(
    val userId: String,
    val nickname: String,
    val schoolName: String,
    val facultyName: String,
    val gpaTier: Int,
    val graduationStatus: String,
    val age: Int,
    val interestTags: List<String>,
    val bio: String,
)

data class CandidateCard(
    val userId: String,
    val nickname: String,
    val schoolName: String,
    val facultyName: String,
    val gpaTier: Int,
    val graduationStatus: String,
    val age: Int,
    val interestTags: List<String>,
    val bio: String,
    val distanceKm: Double,
    val commonTags: List<String>,
    val score: Double,
)

data class MeResponse(@SerializedName("public") val publicProfile: PublicProfile)

data class SwipeRequest(val targetId: String, val like: Boolean)

data class SwipeResponse(val matched: Boolean, val matchId: String?)

data class SendRequest(val text: String)

data class BlurState(
    val blurLevel: Double,
    val blurRadiusPx: Double,
    val revealed: Boolean,
    val modelVersion: String,
)

data class HealthScore(
    val pDisengage: Double,
    val level: String, // "healthy" | "shallow" | "at_risk"
    val topFactors: List<String>,
    val suggestions: List<String>,
    val modelVersion: String,
)

data class MatchState(
    val blur: BlurState,
    val health: HealthScore,
    val compat: Double,
)

data class MatchSummary(
    val matchId: String,
    val other: PublicProfile,
    val lastMessage: Message?,
    val blur: BlurState,
)

data class SafetyRequest(
    val conversationId: String,
    val context: List<Message>,
    val draft: Message,
)

data class SafetyScore(
    val toxicity: Double,
    val derailRisk: Double,
    val shouldNudge: Boolean,
    val modelVersion: String,
)

fun gpaTierLabel(tier: Int): String = when (tier) {
    0 -> "Tầng GPA 1"
    1 -> "Tầng GPA 2"
    2 -> "Tầng GPA 3"
    else -> "Tầng GPA 4"
}

fun graduationLabel(status: String): String =
    if (status == "alumni") "Đã tốt nghiệp" else "Đang học"
