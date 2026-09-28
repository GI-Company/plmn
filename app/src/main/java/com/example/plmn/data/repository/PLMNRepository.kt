package com.example.plmn.data.repository

import com.example.plmn.data.local.AuditDao
import com.example.plmn.data.local.ChatDao
import com.example.plmn.data.local.NodeDao
import com.example.plmn.data.local.VaultDao
import com.example.plmn.data.model.AuditLog
import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.data.security.AuditLedgerEngine
import com.example.plmn.data.security.KeystoreCryptoVault
import com.example.plmn.data.security.LedgerVerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PLMNRepository(
    private val nodeDao: NodeDao,
    private val chatDao: ChatDao,
    private val vaultDao: VaultDao,
    private val auditDao: AuditDao
) {
    // Node Operations
    val allNodes: Flow<List<NodeEndpoint>> = nodeDao.getAllNodes()
    val activeNode: Flow<NodeEndpoint?> = nodeDao.getActiveNode()

    suspend fun switchActiveNode(id: String) = nodeDao.switchActiveNode(id)
    suspend fun insertNode(node: NodeEndpoint) {
        nodeDao.insertNode(node)
    }
    suspend fun updateNode(node: NodeEndpoint) = nodeDao.updateNode(node)
    suspend fun deleteNode(node: NodeEndpoint) = nodeDao.deleteNode(node)

    // Chat Operations
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getMessagesListForSession(sessionId: String): List<ChatMessage> =
        chatDao.getMessagesListForSession(sessionId)

    suspend fun insertMessage(msg: ChatMessage): Long = chatDao.insertMessage(msg)
    suspend fun clearSession(sessionId: String) = chatDao.deleteSession(sessionId)
    suspend fun clearAllMessages() = chatDao.clearAllMessages()

    // Vault Operations with AES-256 GCM Encryption / Decryption
    val allVaultDocs: Flow<List<VaultDocument>> = vaultDao.getAllDocuments().map { list ->
        list.map { doc ->
            val decrypted = KeystoreCryptoVault.decrypt(doc.encryptedContent)
            doc.copy(decryptedContent = decrypted)
        }
    }

    val activeContextDocs: Flow<List<VaultDocument>> = vaultDao.getActiveContextDocuments().map { list ->
        list.map { doc ->
            val decrypted = KeystoreCryptoVault.decrypt(doc.encryptedContent)
            doc.copy(decryptedContent = decrypted)
        }
    }

    suspend fun getActiveContextList(): List<VaultDocument> {
        return vaultDao.getActiveContextList().map { doc ->
            val decrypted = KeystoreCryptoVault.decrypt(doc.encryptedContent)
            doc.copy(decryptedContent = decrypted)
        }
    }

    suspend fun insertVaultDoc(title: String, plainContent: String, category: String, tags: String): Long {
        val encrypted = KeystoreCryptoVault.encrypt(plainContent)
        val doc = VaultDocument(
            title = title,
            encryptedContent = encrypted,
            category = category,
            tags = tags,
            isContextActive = true
        )
        return vaultDao.insertDocument(doc)
    }

    suspend fun updateVaultDoc(doc: VaultDocument, newPlainContent: String? = null) {
        val finalDoc = if (newPlainContent != null) {
            doc.copy(encryptedContent = KeystoreCryptoVault.encrypt(newPlainContent))
        } else {
            doc
        }
        vaultDao.updateDocument(finalDoc)
    }

    suspend fun deleteVaultDoc(doc: VaultDocument) = vaultDao.deleteDocument(doc)
    suspend fun toggleContextActive(id: Long, isActive: Boolean) =
        vaultDao.setContextActive(id, isActive)
    suspend fun clearVault() = vaultDao.clearAll()

    // Tamper-Evident SHA-256 Audit Operations
    val recentAuditLogs: Flow<List<AuditLog>> = auditDao.getRecentLogs()

    suspend fun recordAudit(
        action: String,
        targetNode: String,
        tokens: Int,
        status: String,
        details: String
    ): AuditLog {
        val latestLog = auditDao.getLatestLog()
        val nextLog = AuditLedgerEngine.buildNextLog(
            latestLog = latestLog,
            action = action,
            targetNode = targetNode,
            tokens = tokens,
            status = status,
            details = details
        )
        auditDao.insertLog(nextLog)
        return nextLog
    }

    suspend fun verifyLedgerIntegrity(): LedgerVerificationResult {
        val allLogs = auditDao.getAllLogsAsc()
        return AuditLedgerEngine.verifyChainIntegrity(allLogs)
    }

    suspend fun clearAuditLogs() = auditDao.clearAllLogs()

    // Cryptographic Nuclear Wipe
    suspend fun cryptographicNuclearWipe() {
        // 1. Delete Keystore Master Key (cryptographically rendering all existing ciphertext unrecoverable)
        KeystoreCryptoVault.deleteMasterKey()

        // 2. Wipe database tables
        chatDao.clearAllMessages()
        vaultDao.clearAll()
        auditDao.clearAllLogs()
        nodeDao.clearAllNodes()

        // 3. Re-initialize a fresh Genesis block
        val genesisLog = AuditLedgerEngine.buildNextLog(
            latestLog = null,
            action = "GENESIS_POST_WIPE",
            targetNode = "node-micro-local",
            tokens = 0,
            status = "SECURE",
            details = "Cryptographic master key rotated & storage purged."
        )
        auditDao.insertLog(genesisLog)

        // 4. Restore default local node
        nodeDao.insertNode(
            NodeEndpoint(
                id = "node-micro-local",
                name = "On-Device Micro Engine",
                endpointType = "EMBEDDED",
                url = "local://micro-quant-v1",
                selectedModel = "plmn-micro-reasoner-q4",
                isActive = true,
                latencyMs = 4,
                isReachable = true
            )
        )
    }
}
