package com.example.plmn.inference

enum class ProviderType {
    ON_DEVICE,
    PLMH,
    OLLAMA,
    LM_STUDIO,
    LLAMA_CPP,
    OPENAI_COMPATIBLE,
    CUSTOM;

    val displayName: String
        get() = when (this) {
            ON_DEVICE -> "On-Device Processor"
            PLMH -> "PLMH Hub"
            OLLAMA -> "Ollama"
            LM_STUDIO -> "LM Studio"
            LLAMA_CPP -> "llama.cpp Server"
            OPENAI_COMPATIBLE -> "OpenAI-Compatible"
            CUSTOM -> "Custom Bridge"
        }

    val isRemote: Boolean
        get() = this != ON_DEVICE

    companion object {
        fun fromString(typeStr: String): ProviderType {
            return when (typeStr.uppercase().trim()) {
                "ON_DEVICE", "EMBEDDED", "LOCAL" -> ON_DEVICE
                "PLMH", "HUB" -> PLMH
                "OLLAMA" -> OLLAMA
                "LM_STUDIO", "LMSTUDIO" -> LM_STUDIO
                "LLAMA_CPP", "LLAMACPP" -> LLAMA_CPP
                "OPENAI_COMPATIBLE", "CUSTOM_OPENAI", "OPENAI", "GEMINI_BRIDGE" -> OPENAI_COMPATIBLE
                else -> CUSTOM
            }
        }
    }
}
