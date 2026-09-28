package com.example.plmn.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.plmn.data.model.AuditLog
import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.data.security.KeystoreCryptoVault
import com.example.plmn.data.security.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SeedVaultDoc(
    val title: String,
    val content: String,
    val category: String,
    val tags: String
)

@Database(
    entities = [
        NodeEndpoint::class,
        ChatMessage::class,
        VaultDocument::class,
        AuditLog::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun chatDao(): ChatDao
    abstract fun vaultDao(): VaultDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 schema migration
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate node_endpoints table (encryptedApiKey & authToken -> credentialRef)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS node_endpoints_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        endpointType TEXT NOT NULL,
                        url TEXT NOT NULL,
                        selectedModel TEXT NOT NULL,
                        credentialRef TEXT,
                        isActive INTEGER NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        isReachable INTEGER NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        hubCapabilities TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO node_endpoints_new (id, name, endpointType, url, selectedModel, credentialRef, isActive, latencyMs, isReachable, systemPrompt, hubCapabilities)
                    SELECT id, name, endpointType, url, selectedModel, NULL, isActive, latencyMs, isReachable, systemPrompt, hubCapabilities FROM node_endpoints
                """.trimIndent())

                db.execSQL("DROP TABLE node_endpoints")
                db.execSQL("ALTER TABLE node_endpoints_new RENAME TO node_endpoints")

                // 2. Migrate audit_logs table (tokens -> tokensProcessed, status -> securityStatus)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_logs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        targetNode TEXT NOT NULL,
                        tokensProcessed INTEGER NOT NULL,
                        securityStatus TEXT NOT NULL,
                        previousHash TEXT NOT NULL,
                        currentHash TEXT NOT NULL,
                        details TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO audit_logs_new (id, timestamp, action, targetNode, tokensProcessed, securityStatus, previousHash, currentHash, details)
                    SELECT id, timestamp, action, targetNode, tokens, status, previousHash, currentHash, details FROM audit_logs
                """.trimIndent())

                db.execSQL("DROP TABLE audit_logs")
                db.execSQL("ALTER TABLE audit_logs_new RENAME TO audit_logs")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE node_endpoints ADD COLUMN verificationLevel TEXT NOT NULL DEFAULT 'CONFIGURED'")
                db.execSQL("ALTER TABLE node_endpoints ADD COLUMN lastVerifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE node_endpoints ADD COLUMN lastHttpStatus INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE node_endpoints ADD COLUMN lastFailureCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE node_endpoints ADD COLUMN lastStreamVerifiedAt INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            SecretStore.init(context)
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plmn_secure_vault.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val nodeDao = database.nodeDao()
            val vaultDao = database.vaultDao()

            // 1. Seed Nodes (No plaintext passwords or tokens stored in DB)
            val defaultNodes = listOf(
                NodeEndpoint(
                    id = "node-on-device-rules",
                    name = "PLMN Local Rules Engine",
                    endpointType = "ON_DEVICE",
                    url = "local://on-device-processor",
                    selectedModel = "plmn-local-rules-v1",
                    credentialRef = null,
                    isActive = true,
                    latencyMs = 2,
                    isReachable = true,
                    systemPrompt = "You are PLMN Local Rules Engine. You run locally in complete isolation."
                ),
                NodeEndpoint(
                    id = "node-plmh-hub",
                    name = "PLMH Desktop Hub (Primary)",
                    endpointType = "PLMH",
                    url = "http://192.168.1.100:8080",
                    selectedModel = "intent-1m",
                    credentialRef = null,
                    isActive = false,
                    latencyMs = 18,
                    isReachable = true,
                    systemPrompt = "You are the INTENT Private Language Model running on the user's desktop PLMH hub.",
                    hubCapabilities = "INTENT,MLX,LLAMA_CPP,OLLAMA"
                ),
                NodeEndpoint(
                    id = "node-lan-ollama",
                    name = "LAN Ollama Node",
                    endpointType = "OLLAMA",
                    url = "http://10.0.2.2:11434",
                    selectedModel = "llama3.2:latest",
                    credentialRef = null,
                    isActive = false,
                    latencyMs = 38,
                    isReachable = true,
                    systemPrompt = "You are a private LLM running on the user's home LAN node."
                ),
                NodeEndpoint(
                    id = "node-lm-studio",
                    name = "Desktop LM Studio",
                    endpointType = "LM_STUDIO",
                    url = "http://192.168.1.100:1234/v1",
                    selectedModel = "loaded-model",
                    credentialRef = null,
                    isActive = false,
                    latencyMs = 50,
                    isReachable = true,
                    systemPrompt = "You are LM Studio private workstation node."
                ),
                NodeEndpoint(
                    id = "node-llama-server",
                    name = "Local llama.cpp Server",
                    endpointType = "LLAMA_CPP",
                    url = "http://192.168.1.100:8080",
                    selectedModel = "gguf-model",
                    credentialRef = null,
                    isActive = false,
                    latencyMs = 28,
                    isReachable = true,
                    systemPrompt = "You are llama.cpp local quantized model node."
                ),
                NodeEndpoint(
                    id = "node-openai-compat",
                    name = "OpenAI-Compatible Server",
                    endpointType = "OPENAI_COMPATIBLE",
                    url = "http://192.168.1.100:8000/v1",
                    selectedModel = "default-model",
                    credentialRef = null,
                    isActive = false,
                    latencyMs = 35,
                    isReachable = true,
                    systemPrompt = "You are an OpenAI-compatible private inference endpoint."
                )
            )
            nodeDao.insertNodes(defaultNodes)

            // 2. Seed initial private vault documents with Keystore AES-256 GCM encryption
            val defaultVaultDocs = listOf(
                SeedVaultDoc(
                    title = "PLMN Architecture & Security Spec",
                    content = "Local-first node architecture with client-side sanitization, Keystore AES-256 GCM encryption, ECDSA device identity authentication, and tamper-evident SHA-256 audit ledger.",
                    category = "Specs",
                    tags = "security, specs, architecture"
                ),
                SeedVaultDoc(
                    title = "Home Lab Topology",
                    content = "Gateway: 192.168.1.1. PLMH Desktop Hub: 192.168.1.100:8080. Ollama Rig: 192.168.1.120:11434. LM Studio: 192.168.1.150:1234.",
                    category = "Secret",
                    tags = "lan, network, endpoints, plmh"
                )
            )

            for (seed in defaultVaultDocs) {
                val encrypted = KeystoreCryptoVault.encrypt(seed.content)
                vaultDao.insertDocument(
                    VaultDocument(
                        title = seed.title,
                        encryptedContent = encrypted,
                        category = seed.category,
                        tags = seed.tags,
                        isContextActive = true
                    )
                )
            }
        }
    }
}
