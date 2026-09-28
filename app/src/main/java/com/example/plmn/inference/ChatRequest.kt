package com.example.plmn.inference

data class ChatMessageItem(
    val role: String, // "system", "user", "assistant"
    val content: String,
    val name: String? = null
)

data class ChatRequest(
    val modelId: String,
    val messages: List<ChatMessageItem>,
    val temperature: Float?,
    val maxTokens: Int?,
    val tools: List<ToolDefinition> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)
