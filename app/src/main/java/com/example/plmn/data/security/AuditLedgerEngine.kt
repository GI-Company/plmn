package com.example.plmn.data.security

import com.example.plmn.data.model.AuditLog
import java.security.MessageDigest

data class LedgerVerificationResult(
    val isValid: Boolean,
    val totalEntries: Int,
    val compromisedIndex: Int? = null,
    val genesisHash: String,
    val latestHash: String
)

object AuditLedgerEngine {

    const val GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    fun computeBlockHash(
        previousHash: String,
        timestamp: Long,
        action: String,
        targetNode: String,
        tokens: Int,
        status: String,
        details: String
    ): String {
        val payload = "$previousHash|$timestamp|$action|$targetNode|$tokens|$status|$details"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun buildNextLog(
        latestLog: AuditLog?,
        action: String,
        targetNode: String,
        tokens: Int,
        status: String,
        details: String,
        timestamp: Long = System.currentTimeMillis()
    ): AuditLog {
        val prevHash = latestLog?.currentHash ?: GENESIS_PREV_HASH
        val currentHash = computeBlockHash(
            previousHash = prevHash,
            timestamp = timestamp,
            action = action,
            targetNode = targetNode,
            tokens = tokens,
            status = status,
            details = details
        )
        return AuditLog(
            timestamp = timestamp,
            action = action,
            targetNode = targetNode,
            tokensProcessed = tokens,
            securityStatus = status,
            details = details,
            previousHash = prevHash,
            currentHash = currentHash
        )
    }

    fun verifyChainIntegrity(chainOldestToNewest: List<AuditLog>): LedgerVerificationResult {
        if (chainOldestToNewest.isEmpty()) {
            return LedgerVerificationResult(
                isValid = true,
                totalEntries = 0,
                compromisedIndex = null,
                genesisHash = GENESIS_PREV_HASH,
                latestHash = GENESIS_PREV_HASH
            )
        }

        var expectedPrevHash = GENESIS_PREV_HASH

        for ((index, entry) in chainOldestToNewest.withIndex()) {
            if (entry.previousHash != expectedPrevHash) {
                return LedgerVerificationResult(
                    isValid = false,
                    totalEntries = chainOldestToNewest.size,
                    compromisedIndex = index,
                    genesisHash = chainOldestToNewest.first().previousHash,
                    latestHash = chainOldestToNewest.last().currentHash
                )
            }

            val recomputed = computeBlockHash(
                previousHash = entry.previousHash,
                timestamp = entry.timestamp,
                action = entry.action,
                targetNode = entry.targetNode,
                tokens = entry.tokensProcessed,
                status = entry.securityStatus,
                details = entry.details
            )

            if (entry.currentHash != recomputed) {
                return LedgerVerificationResult(
                    isValid = false,
                    totalEntries = chainOldestToNewest.size,
                    compromisedIndex = index,
                    genesisHash = chainOldestToNewest.first().previousHash,
                    latestHash = chainOldestToNewest.last().currentHash
                )
            }

            expectedPrevHash = entry.currentHash
        }

        return LedgerVerificationResult(
            isValid = true,
            totalEntries = chainOldestToNewest.size,
            compromisedIndex = null,
            genesisHash = chainOldestToNewest.first().previousHash,
            latestHash = chainOldestToNewest.last().currentHash
        )
    }
}
