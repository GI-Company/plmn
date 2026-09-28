package com.example.plmn.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.plmn.data.model.AuditLog
import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.model.VaultDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM node_endpoints ORDER BY CASE WHEN isActive = 1 THEN 0 ELSE 1 END, name ASC")
    fun getAllNodes(): Flow<List<NodeEndpoint>>

    @Query("SELECT * FROM node_endpoints WHERE isActive = 1 LIMIT 1")
    fun getActiveNode(): Flow<NodeEndpoint?>

    @Query("SELECT * FROM node_endpoints WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: String): NodeEndpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeEndpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEndpoint>)

    @Update
    suspend fun updateNode(node: NodeEndpoint)

    @Delete
    suspend fun deleteNode(node: NodeEndpoint)

    @Query("UPDATE node_endpoints SET isActive = 0")
    suspend fun clearActiveNode()

    @Query("UPDATE node_endpoints SET isActive = 1 WHERE id = :id")
    suspend fun setActiveNodeId(id: String)

    @Transaction
    suspend fun switchActiveNode(id: String) {
        clearActiveNode()
        setActiveNodeId(id)
    }

    @Query("SELECT COUNT(*) FROM node_endpoints")
    suspend fun getNodeCount(): Int

    @Query("DELETE FROM node_endpoints")
    suspend fun clearAllNodes()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesListForSession(sessionId: String): List<ChatMessage>

    @Query("SELECT DISTINCT sessionId FROM chat_messages ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<VaultDocument>>

    @Query("SELECT * FROM vault_documents WHERE isContextActive = 1 ORDER BY createdAt DESC")
    fun getActiveContextDocuments(): Flow<List<VaultDocument>>

    @Query("SELECT * FROM vault_documents")
    suspend fun getAllDocumentsList(): List<VaultDocument>

    @Query("SELECT * FROM vault_documents WHERE isContextActive = 1")
    suspend fun getActiveContextList(): List<VaultDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: VaultDocument): Long

    @Update
    suspend fun updateDocument(doc: VaultDocument)

    @Delete
    suspend fun deleteDocument(doc: VaultDocument)

    @Query("UPDATE vault_documents SET isContextActive = :isActive WHERE id = :id")
    suspend fun setContextActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM vault_documents")
    suspend fun clearAll()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs ORDER BY id ASC")
    suspend fun getAllLogsAsc(): List<AuditLog>

    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLatestLog(): AuditLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()
}
