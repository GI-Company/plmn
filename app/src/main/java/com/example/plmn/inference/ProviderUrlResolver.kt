package com.example.plmn.inference

import com.example.plmn.data.model.NodeEndpoint
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface ProviderUrlResolver {
    fun healthUrl(node: NodeEndpoint): HttpUrl?
    fun modelsUrl(node: NodeEndpoint): HttpUrl?
    fun chatUrl(node: NodeEndpoint): HttpUrl?
}

object DefaultProviderUrlResolver : ProviderUrlResolver {

    override fun healthUrl(node: NodeEndpoint): HttpUrl? {
        val base = cleanUrl(node.url)
        return when (ProviderType.fromString(node.endpointType)) {
            ProviderType.PLMH -> "$base/v1/health".toHttpUrlOrNull()
            ProviderType.OLLAMA -> "$base/api/version".toHttpUrlOrNull()
            ProviderType.LM_STUDIO -> if (base.endsWith("/v1")) "$base/models".toHttpUrlOrNull() else "$base/v1/models".toHttpUrlOrNull()
            ProviderType.LLAMA_CPP -> "$base/health".toHttpUrlOrNull()
            ProviderType.OPENAI_COMPATIBLE -> if (base.endsWith("/v1")) "$base/models".toHttpUrlOrNull() else "$base/v1/models".toHttpUrlOrNull()
            ProviderType.CUSTOM -> base.toHttpUrlOrNull()
            ProviderType.ON_DEVICE -> null
        }
    }

    override fun modelsUrl(node: NodeEndpoint): HttpUrl? {
        val base = cleanUrl(node.url)
        return when (ProviderType.fromString(node.endpointType)) {
            ProviderType.PLMH -> "$base/v1/models".toHttpUrlOrNull()
            ProviderType.OLLAMA -> "$base/api/tags".toHttpUrlOrNull()
            ProviderType.LM_STUDIO, ProviderType.OPENAI_COMPATIBLE -> if (base.endsWith("/v1")) "$base/models".toHttpUrlOrNull() else "$base/v1/models".toHttpUrlOrNull()
            ProviderType.LLAMA_CPP -> if (base.endsWith("/v1")) "$base/models".toHttpUrlOrNull() else "$base/v1/models".toHttpUrlOrNull()
            ProviderType.CUSTOM -> "$base/models".toHttpUrlOrNull()
            ProviderType.ON_DEVICE -> null
        }
    }

    override fun chatUrl(node: NodeEndpoint): HttpUrl? {
        val base = cleanUrl(node.url)
        return when (ProviderType.fromString(node.endpointType)) {
            ProviderType.PLMH -> "$base/v1/chat/completions".toHttpUrlOrNull()
            ProviderType.OLLAMA -> "$base/api/chat".toHttpUrlOrNull()
            ProviderType.LM_STUDIO, ProviderType.OPENAI_COMPATIBLE -> if (base.endsWith("/v1")) "$base/chat/completions".toHttpUrlOrNull() else "$base/v1/chat/completions".toHttpUrlOrNull()
            ProviderType.LLAMA_CPP -> if (base.endsWith("/v1")) "$base/chat/completions".toHttpUrlOrNull() else "$base/v1/chat/completions".toHttpUrlOrNull()
            ProviderType.CUSTOM -> base.toHttpUrlOrNull()
            ProviderType.ON_DEVICE -> null
        }
    }

    private fun cleanUrl(url: String): String {
        return url.trim().trimEnd('/')
    }
}
