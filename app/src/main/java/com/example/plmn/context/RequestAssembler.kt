package com.example.plmn.context

import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.inference.ChatMessageItem
import com.example.plmn.inference.ChatRequest
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ToolDefinition

/**
 * Universal Request Assembler for PLMN.
 *
 * Assembles normalized, provider-agnostic ChatRequests:
 * 1. Vault knowledge context injection.
 * 2. Strict context budget invariant enforcement.
 * 3. Complete turn history preservation.
 */
object RequestAssembler {

    data class AssembledContext(
        val chatRequest: ChatRequest,
        val includedVaultDocTitles: List<String>,
        val estimatedTotalTokens: TokenCount,
        val clampedMaxTokens: Int,
        val omittedHistoryTurns: Int,
        val provenance: Map<String, String>
    )

    fun assemble(
        model: ModelDescriptor,
 baseSystemPrompt: String,
        sanitizedPrompt: String,
        vaultDocs: List<VaultDocument>,
        chatHistory: List<ChatMessage>,
        temperature: Float? = 0.7f,
        maxTokens: Int? = 512,
        tools: List<ToolDefinition> = emptyList(),
        isSanitized: Boolean = true
    ): AssembledContext {
        val budget = ContextBudget.calculateBudget(
            baseSystemPrompt = baseSystemPrompt,
            currentPrompt = sanitizedPrompt,
            activeVaultDocs = vaultDocs,
            chatHistory = chatHistory,
            modelContextLength = model.contextLength,
            requestedMaxTokens = maxTokens
        )

        val messagesList = mutableListOf<ChatMessageItem>()
        messagesList.add(ChatMessageItem(role = "system", content = budget.systemPromptWithContext))
        messagesList.addAll(budget.historyMessages)
        messagesList.add(ChatMessageItem(role = "user", content = sanitizedPrompt))

        val provenance = mapOf(
            "modelId" to model.id,
            "providerId" to model.providerId,
            "endpointId" to model.endpointId,
            "vaultDocsCount" to budget.includedVaultDocs.size.toString(),
            "vaultDocTitles" to budget.includedVaultDocs.joinToString(", ") { it.title },
            "sanitizationApplied" to isSanitized.toString(),
            "temperature" to (temperature?.toString() ?: "default"),
            "estimatedInputTokens" to budget.estimatedInputTokens.displayString,
            "clampedMaxTokens" to budget.clampedMaxTokens.toString(),
            "omittedHistoryTurns" to budget.omittedTurnCount.toString()
        )

        val chatRequest = ChatRequest(
            modelId = model.id,
            messages = messagesList,
            temperature = temperature,
            maxTokens = budget.clampedMaxTokens,
            tools = tools,
            metadata = provenance
        )

        return AssembledContext(
            chatRequest = chatRequest,
            includedVaultDocTitles = budget.includedVaultDocs.map { it.title },
            estimatedTotalTokens = budget.estimatedInputTokens,
            clampedMaxTokens = budget.clampedMaxTokens,
            omittedHistoryTurns = budget.omittedTurnCount,
            provenance = provenance
        )
    }
}
