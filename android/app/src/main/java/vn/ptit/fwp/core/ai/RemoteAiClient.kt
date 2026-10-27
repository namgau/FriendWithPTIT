package vn.ptit.fwp.core.ai

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.CompatibilityReport
import vn.ptit.fwp.core.model.HealthLevel
import vn.ptit.fwp.core.model.HealthReport
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.RankedProfile
import vn.ptit.fwp.core.model.FwpMatch
import java.util.concurrent.TimeUnit

class RemoteAiClient(
    baseUrl: String = BuildConfig.AI_BASE_URL,
    private val enabled: Boolean = BuildConfig.AI_BASE_URL.isNotBlank()
) {
    private val api: FwpAiApi? = if (enabled) {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FwpAiApi::class.java)
    } else null

    val isConfigured: Boolean get() = api != null

    suspend fun rank(me: Profile, candidates: List<Profile>): List<RankedProfile>? {
        val api = api ?: return null
        return runCatching {
            val response = api.rank(
                RankRequest(
                    viewer = me.toDto(),
                    candidates = candidates.map { it.toDto() }
                )
            )
            val byId = candidates.associateBy { it.id }
            response.items.mapNotNull { item ->
                val profile = byId[item.profileId] ?: return@mapNotNull null
                RankedProfile(
                    profile = profile,
                    score = item.score,
                    reasons = item.reasons,
                    source = if (response.modelSource == "trained_model") "AI Service (M1)" else "AI Service · fallback"
                )
            }.ifEmpty { null }
        }.getOrNull()
    }

    suspend fun compatibility(
        me: Profile,
        other: Profile,
        match: FwpMatch,
        messages: List<ChatMessage>,
        fallback: CompatibilityReport
    ): CompatibilityReport {
        val api = api ?: return fallback
        return runCatching {
            val response = api.compatibility(
                CompatRequest(
                    viewerInterests = me.interests,
                    partnerInterests = other.interests,
                    messages = messages.map { it.toDto() },
                    viewerOptedIn = match.revealOptInMe,
                    partnerOptedIn = match.revealOptInThem
                )
            )
            fallback.copy(
                score = response.score,
                blurLevel = response.blurLevel,
                personality = response.personality,
                emotion = response.emotion,
                interest = response.interest,
                messageCount = response.messageCount,
                revealEligible = response.revealEligible,
                revealed = response.revealEligible,
                source = if (response.modelSource == "trained_model") "AI Service (M2)" else "AI Service · fallback"
            )
        }.getOrDefault(fallback)
    }

    suspend fun health(
        me: Profile,
        other: Profile,
        messages: List<ChatMessage>,
        fallback: HealthReport
    ): HealthReport {
        val api = api ?: return fallback
        return runCatching {
            val response = api.health(
                HealthRequest(
                    messages = messages.map { it.toDto() },
                    viewerInterests = me.interests,
                    partnerInterests = other.interests
                )
            )
            fallback.copy(
                level = runCatching { HealthLevel.valueOf(response.level) }.getOrDefault(fallback.level),
                score = response.score,
                reasons = response.reasons,
                suggestions = response.suggestions,
                messageCount = response.messageCount,
                source = if (response.modelSource == "trained_model") "AI Service (M3)" else "AI Service · fallback"
            )
        }.getOrDefault(fallback)
    }

    private fun Profile.toDto() = AiProfileDto(
        id = id,
        age = age,
        gender = gender.name,
        school = school,
        campus = campus,
        faculty = faculty,
        gpaTier = gpaTier.name,
        interests = interests,
        mode = mode.name,
        distanceKm = distanceKm,
        alumni = alumni
    )

    private fun ChatMessage.toDto() = AiMessageDto(
        sender = if (isMine) "viewer" else "partner",
        text = text,
        createdAt = createdAt
    )
}
