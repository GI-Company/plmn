package com.example.plmn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "node_endpoints")
data class NodeEndpoint(
    @PrimaryKey val id: String,
    val name: String,
    val endpointType: String, // PLMH, OLLAMA, LM_STUDIO, LLAMA_CPP, OPENAI_COMPATIBLE, ON_DEVICE, CUSTOM
    val url: String,
    val selectedModel: String,
    val credentialRef: String? = null, // Opaque reference to Keystore-backed SecretStore
    val isActive: Boolean = false,
    val latencyMs: Long = 0L,
    val isReachable: Boolean = true,
    val systemPrompt: String = "You are PLMN (Private Language Model Node), a self-sovereign, privacy-first reasoning engine. You operate with strict confidentiality, client-side sanitization, and direct, concise analysis.",
    val hubCapabilities: String = "INTENT,MLX,LLAMA_CPP,OLLAMA",
    val verificationLevel: String = "CONFIGURED",
    val lastVerifiedAt: Long = 0L,
    val lastHttpStatus: Int? = null,
    val lastFailureCode: String? = null,
    val lastStreamVerifiedAt: Long? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String, // Clean user prompt or assistant response
    val sanitizedContent: String, // Sanitized prompt transmitted over wire
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val generationMs: Long = 0L,
    val nodeName: String = "Local Node",
    val redactedItemsCount: Int = 0
)

@Entity(tableName = "vault_documents")
data class VaultDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val encryptedContent: String, // AES-256 GCM encrypted at rest in SQLite
    val category: String = "Note", // "Secret", "Code", "Note", "Context", "Credentials", "Specs"
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isContextActive: Boolean = true,
    // Transient decrypted content helper for in-memory use only
    @androidx.room.Ignore val decryptedContent: String = ""
) {
    // Secondary constructor for Room
    constructor(
        id: Long,
        title: String,
        encryptedContent: String,
        category: String,
        tags: String,
        createdAt: Long,
        isContextActive: Boolean
    ) : this(id, title, encryptedContent, category, tags, createdAt, isContextActive, "")
}

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // "PROMPT_SCRUB", "AIRGAP_ENGAGED", "INFERENCE_EXEC", "VAULT_INSERT", etc.
    val targetNode: String,
    val tokensProcessed: Int,
    val securityStatus: String, // "SECURE", "SCRUBBED", "AIRGAP_LOCAL"
    val previousHash: String,
    val currentHash: String, // Tamper-evident hash chain: SHA-256(prevHash + action + timestamp + details)
    val details: String
)
