package com.example.plmn.inference.providers

import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.inference.CapabilityState
import com.example.plmn.inference.ChatMessageItem
import com.example.plmn.inference.ChatRequest
import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ModelProvider
import com.example.plmn.inference.ProviderCapabilities
import com.example.plmn.inference.ProviderHealth
import com.example.plmn.inference.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * On-Device Local Reasoning Provider: PLMN Local Rules Engine
 *
 * Runs locally on Android without external network sockets.
 * Operates as a deterministic local rules processor for offline queries, privacy checks,
 * and security analysis.
 */
class OnDeviceProvider(
    override val id: String = "on-device-rules",
    private val displayName: String = "PLMN Local Rules Engine"
) : ModelProvider {

    override val type: ProviderType = ProviderType.ON_DEVICE

    override suspend fun healthCheck(): ProviderHealth {
        return ProviderHealth(
            isHealthy = true,
            latencyMs = 2L,
            message = "PLMN Local Rules Engine active (Offline local processor)"
        )
    }

    override suspend fun listModels(): List<ModelDescriptor> {
        return listOf(
            ModelDescriptor(
                id = "plmn-local-rules-v1",
                displayName = "PLMN Local Rules Engine",
                providerId = id,
                endpointId = id,
                contextLength = 2048,
                supportsStreaming = CapabilityState.SUPPORTED,
                supportsTools = CapabilityState.UNSUPPORTED,
                supportsVision = CapabilityState.UNSUPPORTED,
                supportsStructuredOutput = CapabilityState.UNSUPPORTED,
                supportsVaultContext = CapabilityState.SUPPORTED,
                supportsPersistentMemory = CapabilityState.UNSUPPORTED
            )
        )
    }

    override suspend fun capabilities(): ProviderCapabilities {
        return ProviderCapabilities(
            chat = CapabilityState.SUPPORTED,
            streaming = CapabilityState.SUPPORTED,
            tools = CapabilityState.UNSUPPORTED,
            structuredOutput = CapabilityState.UNSUPPORTED,
            vision = CapabilityState.UNSUPPORTED,
            modelDiscovery = CapabilityState.UNSUPPORTED,
            vaultContext = CapabilityState.SUPPORTED,
            persistentMemory = CapabilityState.UNSUPPORTED
        )
    }

    override fun streamChat(request: ChatRequest): Flow<InferenceEvent> = flow {
        emit(InferenceEvent.Started(providerId = id, modelId = request.modelId))

        val userMessage = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val systemMessage = request.messages.firstOrNull { it.role == "system" }?.content.orEmpty()
        val queryLower = userMessage.lowercase()

        val responseText = buildString {
            when {
                queryLower.contains("hello") || queryLower.contains("hi") || queryLower.contains("status") -> {
                    append("● PLMN Local Rules Engine operational.\n\n")
                    append("• Runtime: Deterministic Local Rules Engine (Offline)\n")
                    append("• Airgap Compatibility: Local execution (zero network sockets opened)\n")
                    append("• Context Budget: ${request.messages.size} messages assembled in input budget\n\n")
                    append("Ready for offline queries, privacy sanitization verification, and local Vault notes analysis.")
                }
                queryLower.contains("privacy") || queryLower.contains("security") || queryLower.contains("airgap") -> {
                    append("### PLMN Local Security Architecture\n\n")
                    append("1. **Local-First Execution**: Prompts stay on-device in airgap mode.\n")
                    append("2. **Keystore Vault Protection**: Document secrets & API keys encrypted at rest with AES-256 GCM.\n")
                    append("3. **Client-Side Sanitization**: Outbound network prompts scrubbed before transmission.\n")
                    append("4. **Tamper-Evident Ledger**: SHA-256 hash chaining verifies audit immutability.")
                }
                else -> {
                    append("PLMN Local Rules Engine evaluated your query:\n\n")
                    append("• User Query: \"${userMessage.take(100)}\"\n")
                    append("• Local Verification: Digest [${CryptoVaultHelper.sha256Fingerprint(userMessage).take(12)}]\n\n")
                    append("Processed locally on-device.")
                }
            }
        }

        val words = responseText.split(" ")
        var outputTokens = 0
        for (word in words) {
            emit(InferenceEvent.TextDelta("$word "))
            outputTokens++
            delay(10)
        }

        val inputTokens = CryptoVaultHelper.estimateTokenCount(userMessage) +
                CryptoVaultHelper.estimateTokenCount(systemMessage)

        emit(InferenceEvent.Usage(inputTokens = inputTokens, outputTokens = outputTokens))
        emit(InferenceEvent.Completed(reason = "stop"))
    }.flowOn(Dispatchers.Default)
}
