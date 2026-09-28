package com.example.plmn.inference

data class ProviderCapabilities(
    val chat: CapabilityState = CapabilityState.SUPPORTED,
    val streaming: CapabilityState = CapabilityState.SUPPORTED,
    val tools: CapabilityState = CapabilityState.UNKNOWN,
    val structuredOutput: CapabilityState = CapabilityState.UNKNOWN,
    val vision: CapabilityState = CapabilityState.UNKNOWN,
    val modelDiscovery: CapabilityState = CapabilityState.UNKNOWN,
    val vaultContext: CapabilityState = CapabilityState.UNKNOWN,
    val persistentMemory: CapabilityState = CapabilityState.UNKNOWN
)
