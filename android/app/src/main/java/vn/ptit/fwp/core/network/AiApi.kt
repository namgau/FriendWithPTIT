package vn.ptit.fwp.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class SafetyMessageDto(
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("timestamp_ms") val timestampMs: Long,
    @SerializedName("text") val text: String? = null
)

data class SafetyScoreRequest(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("context") val context: List<SafetyMessageDto> = emptyList(),
    @SerializedName("draft") val draft: SafetyMessageDto
)

data class SafetyScoreResponse(
    @SerializedName("toxicity") val toxicity: Float = 0f,
    @SerializedName("derail_risk") val derailRisk: Float = 0f,
    @SerializedName("should_nudge") val shouldNudge: Boolean = false,
    @SerializedName("model_version") val modelVersion: String = ""
)

interface AiApi {
    @POST("safety/score")
    suspend fun safetyScore(@Body request: SafetyScoreRequest): SafetyScoreResponse
}
