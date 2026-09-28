package com.example.plmn.inference

sealed interface InferenceEvent {
    data class Started(
        val providerId: String,
        val modelId: String
    ) : InferenceEvent

    data class TextDelta(
        val text: String
    ) : InferenceEvent

    data class ToolRequest(
        val id: String,
        val name: String,
        val arguments: String
    ) : InferenceEvent

    data class Usage(
        val inputTokens: Int?,
        val outputTokens: Int?
    ) : InferenceEvent

    data class Completed(
        val reason: String? = null
    ) : InferenceEvent

    object Cancelled : InferenceEvent

    data class Failure(
        val message: String,
        val recoverable: Boolean = false
    ) : InferenceEvent
}
