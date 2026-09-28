package com.example.plmn.inference

import kotlinx.coroutines.flow.Flow

interface ModelProvider {
    val id: String
    val type: ProviderType

    suspend fun healthCheck(): ProviderHealth

    suspend fun listModels(): List<ModelDescriptor>

    suspend fun capabilities(): ProviderCapabilities

    fun streamChat(
        request: ChatRequest
    ): Flow<InferenceEvent>
}
