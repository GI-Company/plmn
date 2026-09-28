package com.example.plmn.inference

import com.example.plmn.data.model.NodeEndpoint
import kotlinx.coroutines.flow.Flow

enum class NodeVerificationLevel {
    CONFIGURED,     // Endpoint saved locally
    DISCOVERED,     // Node network address resolved (DNS lookup succeeded)
    REACHABLE,      // Transport connection succeeded (TCP socket connected)
    PROTOCOL_OK,    // Application endpoint returned accepted protocol response
    AUTHENTICATED,  // Signed request / credential accepted (200 OK on auth endpoint)
    MODEL_READY,    // Selected model verified present in model discovery list
    STREAM_READY;   // Real inference stream produced valid protocol event

    val isVerified: Boolean get() = this == PROTOCOL_OK || this == AUTHENTICATED || this == MODEL_READY || this == STREAM_READY
}

data class HealthResult(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val verification: NodeVerification,
    val resolvedUrl: String = "",
    val message: String = ""
)

data class CapabilitiesResult(
    val capabilities: ProviderCapabilities,
    val models: List<ModelDescriptor>
)

/**
 * Universal transport abstraction for PLMN control and data planes.
 *
 * Guaranteed single transport path: Ping/probe and inference streams share
 * one OkHttpClient, one NetworkDestinationGuard, one ProviderUrlResolver, and one TLS policy.
 */
interface PlmTransport {
    suspend fun health(node: NodeEndpoint): HealthResult
    suspend fun capabilities(node: NodeEndpoint): CapabilitiesResult
    fun stream(node: NodeEndpoint, request: ChatRequest): Flow<InferenceEvent>
}
