package vn.ptit.fwp.core.network

import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.ai.RemoteAiClient as CoreRemoteAiClient

class RemoteAiClient(
    private val delegate: CoreRemoteAiClient = CoreRemoteAiClient()
) {
    suspend fun safetyScore(
        conversationId: String,
        context: List<ChatMessage>,
        draftText: String
    ): SafetyScoreResponse? {
        val res = delegate.safetyScore(conversationId, context, draftText) ?: return null
        return SafetyScoreResponse(
            toxicity = res.toxicity,
            derailRisk = res.derailRisk,
            shouldNudge = res.shouldNudge,
            modelVersion = res.modelVersion
        )
    }
}
