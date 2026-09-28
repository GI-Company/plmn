package com.example.plmn.context

import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.inference.ChatMessageItem

class ContextExceededException(message: String) : Exception(message)

/**
 * Deterministic context budgeting engine enforcing strict context invariants.
 *
 * Invariant:
 * serialized_input_estimate + reserved_generation_budget + safety_allowance <= model_context_capacity
 *
 * Rules:
 * 1. If base system prompt + current prompt exceeds available input capacity, REJECT explicitly.
 * 2. Complete-turn history trimming: preserves user+assistant pairs together; never leaves orphaned assistant turns.
 * 3. Does NOT perform string-based history filtering (a user may ask the same question twice in different turns).
 */
object ContextBudget {

    data class BudgetAllocation(
        val systemPromptWithContext: String,
        val historyMessages: List<ChatMessageItem>,
        val currentPrompt: String,
        val includedVaultDocs: List<VaultDocument>,
        val estimatedInputTokens: TokenCount,
        val clampedMaxTokens: Int,
        val contextCapacity: Int,
        val omittedTurnCount: Int
    )

    fun calculateBudget(
        baseSystemPrompt: String,
        currentPrompt: String,
        activeVaultDocs: List<VaultDocument>,
        chatHistory: List<ChatMessage>,
        modelContextLength: Int?,
        requestedMaxTokens: Int? = 512,
        safetyMarginTokens: Int = 128
    ): BudgetAllocation {
        val contextCapacity = (modelContextLength ?: 4096).coerceAtLeast(256)

        // Clamp requested generation maxTokens to ensure at least 128 tokens for input
        val maxAllowedGeneration = (contextCapacity - 128).coerceAtLeast(64)
        val clampedMaxTokens = (requestedMaxTokens ?: 512)
            .coerceAtMost(maxAllowedGeneration)
            .coerceAtLeast(64)

        val availableInputBudget = contextCapacity - clampedMaxTokens - safetyMarginTokens

        val promptTokens = CryptoVaultHelper.estimateTokenCount(currentPrompt)
        val baseSystemTokens = CryptoVaultHelper.estimateTokenCount(baseSystemPrompt)
        val mandatoryTokens = promptTokens + baseSystemTokens

        if (mandatoryTokens > availableInputBudget) {
            throw ContextExceededException(
                "Context budget exceeded: system prompt + current query ($mandatoryTokens tokens) exceeds available input budget ($availableInputBudget tokens) for model capacity ($contextCapacity tokens)."
            )
        }

        var remainingInputBudget = availableInputBudget - mandatoryTokens

        // 1. Rank & select Vault documents (up to 35% of remaining input budget)
        val queryWords = currentPrompt.lowercase().split("\\W+".toRegex()).filter { it.length > 2 }
        val scoredDocs = activeVaultDocs.filter { it.isContextActive }.map { doc ->
            val searchCorpus = (doc.title + " " + doc.tags + " " + doc.decryptedContent).lowercase()
            var score = 0
            for (word in queryWords) {
                if (searchCorpus.contains(word)) score += 3
            }
            if (doc.category.equals("Context", ignoreCase = true) || doc.category.equals("Specs", ignoreCase = true)) {
                score += 1
            }
            doc to score
        }.sortedByDescending { it.second }

        val vaultBudget = (availableInputBudget * 0.35).toInt().coerceAtMost(remainingInputBudget)
        val selectedVaultDocs = mutableListOf<VaultDocument>()
        var usedVaultTokens = 0

        for ((doc, _) in scoredDocs) {
            val docTokens = CryptoVaultHelper.estimateTokenCount(doc.title + " " + doc.decryptedContent)
            if (usedVaultTokens + docTokens <= vaultBudget) {
                selectedVaultDocs.add(doc)
                usedVaultTokens += docTokens
            }
        }
        remainingInputBudget -= usedVaultTokens

        // 2. System prompt assembly with injected vault context
        val fullSystemPrompt = buildString {
            append(baseSystemPrompt.trim())
            if (selectedVaultDocs.isNotEmpty()) {
                append("\n\n[PRIVATE VAULT CONTEXT - LOCALLY DECRYPTED ON-DEVICE]\n")
                append("The following knowledge documents from the user's private vault are injected for this turn:\n")
                selectedVaultDocs.forEachIndexed { idx, doc ->
                    append("--- Ref #${idx + 1}: ${doc.title} (${doc.category}) ---\n")
                    append(doc.decryptedContent.trim())
                    append("\n")
                }
                append("--- END VAULT CONTEXT ---")
            }
        }

        // 3. Complete-turn backfill of PREVIOUS conversation history
        // Group history into pairs/turns (user + assistant) to avoid orphaned assistant messages
        val validHistoryItems = mutableListOf<ChatMessageItem>()
        var usedHistoryTokens = 0
        var omittedTurnCount = 0

        // Parse previous history into complete turns from newest to oldest
        val turns = mutableListOf<Pair<ChatMessage?, ChatMessage>>() // Pair(user, assistant) or single user
        var idx = chatHistory.size - 1
        while (idx >= 0) {
            val msg = chatHistory[idx]
            if (msg.role == "assistant") {
                val prevMsg = if (idx - 1 >= 0 && chatHistory[idx - 1].role == "user") chatHistory[idx - 1] else null
                turns.add(prevMsg to msg)
                if (prevMsg != null) idx -= 2 else idx -= 1
            } else {
                turns.add(msg to msg) // single user message turn
                idx -= 1
            }
        }

        for (turn in turns) {
            val userMsg = turn.first
            val assistantMsg = turn.second

            val userText = userMsg?.let { if (it.role == "user") it.sanitizedContent.ifBlank { it.content } else "" }.orEmpty()
            val assistantText = if (assistantMsg.role == "assistant") assistantMsg.content else ""

            val turnTokens = CryptoVaultHelper.estimateTokenCount(userText) +
                    CryptoVaultHelper.estimateTokenCount(assistantText) + 8

            if (usedHistoryTokens + turnTokens <= remainingInputBudget) {
                if (userText.isNotBlank() && userMsg != assistantMsg) {
                    validHistoryItems.add(0, ChatMessageItem(role = "user", content = userText))
                }
                if (assistantText.isNotBlank()) {
                    val insertPos = if (userText.isNotBlank() && userMsg != assistantMsg) 1 else 0
                    validHistoryItems.add(insertPos, ChatMessageItem(role = "assistant", content = assistantText))
                }
                usedHistoryTokens += turnTokens
            } else {
                omittedTurnCount++
            }
        }

        val totalInputTokensEstimate = CryptoVaultHelper.estimateTokenCount(fullSystemPrompt) +
                usedHistoryTokens + promptTokens

        return BudgetAllocation(
            systemPromptWithContext = fullSystemPrompt,
            historyMessages = validHistoryItems,
            currentPrompt = currentPrompt,
            includedVaultDocs = selectedVaultDocs,
            estimatedInputTokens = TokenCount(totalInputTokensEstimate, TokenAccountingMethod.ESTIMATED),
            clampedMaxTokens = clampedMaxTokens,
            contextCapacity = contextCapacity,
            omittedTurnCount = omittedTurnCount
        )
    }
}
