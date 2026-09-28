package com.example.plmn.data.security

import android.content.Context
import com.example.plmn.data.security.KeystoreCryptoVault
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * SecretStore: Secure Keystore-backed credential subsystem for PLMN endpoints.
 *
 * - Database stores ONLY opaque credential references (`credentialRef`), e.g., "cred-ref-uuid".
 * - Raw API keys / tokens are NEVER persisted in Room tables or serialized JSON logs.
 * - Credential values are encrypted with Keystore AES-256 GCM at rest.
 * - Provides fail-closed decryption and masked display helpers.
 */
object SecretStore {

    private const val PREFS_NAME = "plmn_keystore_secrets_v1"
    private val memoryStore = ConcurrentHashMap<String, String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun storeSecret(rawSecret: String): String? {
        if (rawSecret.isBlank()) return null

        val refId = "cred-ref-${UUID.randomUUID()}"
        val encrypted = KeystoreCryptoVault.encrypt(rawSecret)

        memoryStore[refId] = encrypted

        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(refId, encrypted).apply()
        }

        return refId
    }

    @Synchronized
    fun retrieveSecret(credentialRef: String?): String {
        if (credentialRef.isNullOrBlank()) return ""

        val encrypted = memoryStore[credentialRef] ?: run {
            appContext?.let { ctx ->
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(credentialRef, null)
            }
        } ?: return ""

        return when (val res = KeystoreCryptoVault.decryptResult(encrypted)) {
            is DecryptionResult.Success -> res.plaintext
            is DecryptionResult.Failure -> ""
        }
    }

    @Synchronized
    fun removeSecret(credentialRef: String?) {
        if (credentialRef.isNullOrBlank()) return
        memoryStore.remove(credentialRef)
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(credentialRef).apply()
        }
    }

    fun maskSecret(credentialRef: String?): String {
        val secret = retrieveSecret(credentialRef)
        if (secret.isBlank()) return "None"
        if (secret.length <= 6) return "••••••"
        return "${secret.take(3)}••••${secret.takeLast(3)}"
    }

    @Synchronized
    fun purgeAllSecrets() {
        memoryStore.clear()
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }
}
