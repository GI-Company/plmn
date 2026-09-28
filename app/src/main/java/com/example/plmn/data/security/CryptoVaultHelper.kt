package com.example.plmn.data.security

import java.security.MessageDigest
import java.util.UUID

object CryptoVaultHelper {

    const val EMPTY_BODY_SHA256_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    fun generateNodeSessionId(): String {
        return "sess-" + UUID.randomUUID().toString().take(8)
    }

    /**
     * Computes full 32-byte / 64-character lowercase hexadecimal SHA-256 digest.
     */
    fun sha256Hex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return EMPTY_BODY_SHA256_HEX
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(text: String): String {
        if (text.isEmpty()) return EMPTY_BODY_SHA256_HEX
        return sha256Hex(text.toByteArray(Charsets.UTF_8))
    }

    fun sha256Fingerprint(input: String): String {
        return sha256Hex(input).take(16)
    }

    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.trim().split("\\s+".toRegex()).size
        val charTokens = (text.length / 3.8).toInt()
        return maxOf(words, charTokens).coerceAtLeast(1)
    }

    fun generateNodeFingerprint(nodeId: String, url: String): String {
        return "NODE#" + sha256Fingerprint("$nodeId:$url:${System.currentTimeMillis() / 86400000}").uppercase()
    }
}
