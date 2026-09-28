package com.example.plmn.context

enum class TokenAccountingMethod {
    EXACT,
    ESTIMATED,
    UNKNOWN
}

data class TokenCount(
    val count: Int?,
    val method: TokenAccountingMethod
) {
    val displayString: String
        get() = when (method) {
            TokenAccountingMethod.EXACT -> "${count ?: 0} tokens (Exact)"
            TokenAccountingMethod.ESTIMATED -> "~${count ?: 0} tokens (Estimated)"
            TokenAccountingMethod.UNKNOWN -> "Unknown tokens"
        }
}
