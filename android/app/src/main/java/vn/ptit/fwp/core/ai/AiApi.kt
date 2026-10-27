package vn.ptit.fwp.core.ai

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class AiProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("age") val age: Int,
    @SerializedName("gender") val gender: String,
    @SerializedName("school") val school: String,
    @SerializedName("campus") val campus: String,
    @SerializedName("faculty") val faculty: String,
    @SerializedName("gpa_tier") val gpaTier: String,
    @SerializedName("interests") val interests: List<String>,
    @SerializedName("mode") val mode: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("alumni") val alumni: Boolean
)

data class AiMessageDto(
    @SerializedName("sender") val sender: String,
    @SerializedName("text") val text: String,
    @SerializedName("created_at") val createdAt: Long
)

data class RankRequest(
    @SerializedName("viewer") val viewer: AiProfileDto,
    @SerializedName("candidates") val candidates: List<AiProfileDto>
)

data class RankItemDto(
    @SerializedName("profile_id") val profileId: String,
    @SerializedName("score") val score: Int,
    @SerializedName("reasons") val reasons: List<String>,
    @SerializedName("model_source") val modelSource: String? = null
)

data class RankResponse(
    @SerializedName("items") val items: List<RankItemDto>,
    @SerializedName("model_source") val modelSource: String? = null
)

data class CompatRequest(
    @SerializedName("viewer_interests") val viewerInterests: List<String>,
    @SerializedName("partner_interests") val partnerInterests: List<String>,
    @SerializedName("messages") val messages: List<AiMessageDto>,
    @SerializedName("viewer_opted_in") val viewerOptedIn: Boolean,
    @SerializedName("partner_opted_in") val partnerOptedIn: Boolean,
    @SerializedName("min_messages") val minMessages: Int = 8
)

data class CompatResponse(
    @SerializedName("score") val score: Int,
    @SerializedName("blur_level") val blurLevel: Float,
    @SerializedName("personality") val personality: Int,
    @SerializedName("emotion") val emotion: Int,
    @SerializedName("interest") val interest: Int,
    @SerializedName("message_count") val messageCount: Int,
    @SerializedName("reveal_eligible") val revealEligible: Boolean,
    @SerializedName("model_source") val modelSource: String? = null
)

data class HealthRequest(
    @SerializedName("messages") val messages: List<AiMessageDto>,
    @SerializedName("viewer_interests") val viewerInterests: List<String>,
    @SerializedName("partner_interests") val partnerInterests: List<String>,
    @SerializedName("now") val now: Long? = null
)

data class HealthResponse(
    @SerializedName("level") val level: String,
    @SerializedName("score") val score: Int,
    @SerializedName("reasons") val reasons: List<String>,
    @SerializedName("suggestions") val suggestions: List<String>,
    @SerializedName("message_count") val messageCount: Int,
    @SerializedName("model_source") val modelSource: String? = null
)

interface FwpAiApi {
    @POST("m1/rank")
    suspend fun rank(@Body request: RankRequest): RankResponse

    @POST("m2/compat")
    suspend fun compatibility(@Body request: CompatRequest): CompatResponse

    @POST("m3/health")
    suspend fun health(@Body request: HealthRequest): HealthResponse
}
