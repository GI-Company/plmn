package com.example.plmn.inference

data class ProviderHealth(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val message: String? = null
)
