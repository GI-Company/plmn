package com.example.plmn.data.security

import java.util.regex.Pattern

data class RedactedItem(
    val type: String, // EMAIL, PHONE, IP_ADDRESS, API_KEY, PAYMENT_CARD, IDENTITY
    val originalSnippet: String,
    val replacement: String
)

data class SanitizationResult(
    val originalText: String,
    val sanitizedText: String,
    val redactedItems: List<RedactedItem>,
    val privacyScore: Int // 0-100 (100 = 100% clean / safe)
)

object PromptSanitizer {

    private val EMAIL_REGEX = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_REGEX = Pattern.compile("(\\+?[0-9]{1,3}[-.\\s]?)?\\(?[0-9]{3}\\)?[-.\\s]?[0-9]{3}[-.\\s]?[0-9]{4}")
    private val IPV4_REGEX = Pattern.compile("\\b(?!127\\.0\\.0\\.1|0\\.0\\.0\\.0)(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    private val API_KEY_REGEX = Pattern.compile("(?:sk-[a-zA-Z0-9_]{16,}|AIza[0-9A-Za-z-_]{35}|ghp_[a-zA-Z0-9]{36}|Bearer\\s+[a-zA-Z0-9._\\-]{20,})")
    private val CARD_REGEX = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b")
    private val SSN_REGEX = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")

    fun sanitize(text: String, enabled: Boolean = true): SanitizationResult {
        if (!enabled || text.isBlank()) {
            return SanitizationResult(
                originalText = text,
                sanitizedText = text,
                redactedItems = emptyList(),
                privacyScore = 100
            )
        }

        var result = text
        val items = mutableListOf<RedactedItem>()
        var penalty = 0

        // 1. API Keys & Secrets (High risk)
        val apiKeyMatcher = API_KEY_REGEX.matcher(result)
        var keyCount = 1
        val keyReplacements = mutableListOf<Pair<String, String>>()
        while (apiKeyMatcher.find()) {
            val original = apiKeyMatcher.group()
            val placeholder = "[REDACTED_SECRET_${keyCount++}]"
            keyReplacements.add(original to placeholder)
            items.add(RedactedItem("API_KEY", original.take(6) + "...", placeholder))
            penalty += 30
        }
        for ((orig, rep) in keyReplacements) {
            result = result.replace(orig, rep)
        }

        // 2. Payment Cards
        val cardMatcher = CARD_REGEX.matcher(result)
        var cardCount = 1
        val cardReplacements = mutableListOf<Pair<String, String>>()
        while (cardMatcher.find()) {
            val original = cardMatcher.group()
            val placeholder = "[REDACTED_CARD_${cardCount++}]"
            cardReplacements.add(original to placeholder)
            items.add(RedactedItem("PAYMENT_CARD", "****" + original.takeLast(4), placeholder))
            penalty += 35
        }
        for ((orig, rep) in cardReplacements) {
            result = result.replace(orig, rep)
        }

        // 3. Social Security / National IDs
        val ssnMatcher = SSN_REGEX.matcher(result)
        var ssnCount = 1
        val ssnReplacements = mutableListOf<Pair<String, String>>()
        while (ssnMatcher.find()) {
            val original = ssnMatcher.group()
            val placeholder = "[REDACTED_ID_${ssnCount++}]"
            ssnReplacements.add(original to placeholder)
            items.add(RedactedItem("IDENTITY", "***-**-" + original.takeLast(4), placeholder))
            penalty += 40
        }
        for ((orig, rep) in ssnReplacements) {
            result = result.replace(orig, rep)
        }

        // 4. Emails
        val emailMatcher = EMAIL_REGEX.matcher(result)
        var emailCount = 1
        val emailReplacements = mutableListOf<Pair<String, String>>()
        while (emailMatcher.find()) {
            val original = emailMatcher.group()
            val placeholder = "[REDACTED_EMAIL_${emailCount++}]"
            emailReplacements.add(original to placeholder)
            items.add(RedactedItem("EMAIL", original, placeholder))
            penalty += 15
        }
        for ((orig, rep) in emailReplacements) {
            result = result.replace(orig, rep)
        }

        // 5. Phone Numbers
        val phoneMatcher = PHONE_REGEX.matcher(result)
        var phoneCount = 1
        val phoneReplacements = mutableListOf<Pair<String, String>>()
        while (phoneMatcher.find()) {
            val original = phoneMatcher.group()
            // Ensure it has at least 7 digits to avoid scrubbing random numbers
            val digitCount = original.count { it.isDigit() }
            if (digitCount in 7..15) {
                val placeholder = "[REDACTED_PHONE_${phoneCount++}]"
                phoneReplacements.add(original to placeholder)
                items.add(RedactedItem("PHONE", original, placeholder))
                penalty += 15
            }
        }
        for ((orig, rep) in phoneReplacements) {
            result = result.replace(orig, rep)
        }

        // 6. IP Addresses
        val ipMatcher = IPV4_REGEX.matcher(result)
        var ipCount = 1
        val ipReplacements = mutableListOf<Pair<String, String>>()
        while (ipMatcher.find()) {
            val original = ipMatcher.group()
            val placeholder = "[REDACTED_IP_${ipCount++}]"
            ipReplacements.add(original to placeholder)
            items.add(RedactedItem("IP_ADDRESS", original, placeholder))
            penalty += 10
        }
        for ((orig, rep) in ipReplacements) {
            result = result.replace(orig, rep)
        }

        val finalScore = (100 - penalty).coerceIn(10, 100)

        return SanitizationResult(
            originalText = text,
            sanitizedText = result,
            redactedItems = items,
            privacyScore = finalScore
        )
    }
}
