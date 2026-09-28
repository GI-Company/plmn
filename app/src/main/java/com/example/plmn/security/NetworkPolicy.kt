package com.example.plmn.security

import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * NetworkPolicy: Enforces strict airgap and LAN boundaries.
 *
 * Rules:
 * 1. If isAirgapEnabled == true, ONLY ProviderType.ON_DEVICE is permitted to execute.
 * 2. Every network provider (PLMH, OLLAMA, LM_STUDIO, LLAMA_CPP, OPENAI_COMPATIBLE, CUSTOM)
 *    is unconditionally rejected with an explicit Airgap event.
 * 3. Never silently fall back to cloud providers.
 */
object NetworkPolicy {

    class AirgapViolationException(message: String) : SecurityException(message)

    fun validateExecution(
        providerType: ProviderType,
        isAirgapEnabled: Boolean
    ): Boolean {
        if (isAirgapEnabled && providerType.isRemote) {
            return false
        }
        return true
    }

    fun emitAirgapRejection(providerType: ProviderType): Flow<InferenceEvent> = flow {
        emit(
            InferenceEvent.Failure(
                message = "Airgap isolation active: external network connection to ${providerType.displayName} blocked.",
                recoverable = false
            )
        )
    }
}
