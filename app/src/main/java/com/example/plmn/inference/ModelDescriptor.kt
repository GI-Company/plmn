package com.example.plmn.inference

data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val providerId: String,
    val endpointId: String,
    val contextLength: Int? = null, // Unknown if null
    val supportsStreaming: CapabilityState = CapabilityState.SUPPORTED,
    val supportsTools: CapabilityState = CapabilityState.UNKNOWN,
    val supportsVision: CapabilityState = CapabilityState.UNKNOWN,
    val supportsStructuredOutput: CapabilityState = CapabilityState.UNKNOWN,
    val supportsVaultContext: CapabilityState = CapabilityState.UNKNOWN,
    val supportsPersistentMemory: CapabilityState = CapabilityState.UNKNOWN
)
