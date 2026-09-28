package com.example.plmn.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class DecryptionResult {
    data class Success(val plaintext: String) : DecryptionResult()
    data class Failure(val cause: String) : DecryptionResult()
}

object KeystoreCryptoVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "plmn_master_vault_aes_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    // In-memory fallback key for testing environments (e.g. Robolectric JVM)
    private var fallbackKey: SecretKey? = null

    @Synchronized
    fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } catch (e: Exception) {
            fallbackKey ?: run {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                val key = SecretKeySpec(randomBytes, "AES")
                fallbackKey = key
                key
            }
        }
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv // 12 bytes
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
        buffer.put(iv)
        buffer.put(cipherBytes)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    fun decryptResult(encryptedBase64: String): DecryptionResult {
        if (encryptedBase64.isEmpty()) return DecryptionResult.Success("")
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) {
                return DecryptionResult.Failure("Invalid ciphertext payload size (IV missing)")
            }

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)

            val buffer = ByteBuffer.wrap(combined)
            buffer.get(iv)
            buffer.get(cipherBytes)

            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            DecryptionResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            DecryptionResult.Failure(e.localizedMessage ?: "Decryption failed")
        }
    }

    fun decrypt(encryptedBase64: String): String {
        return when (val res = decryptResult(encryptedBase64)) {
            is DecryptionResult.Success -> res.plaintext
            is DecryptionResult.Failure -> throw SecurityException("Decryption fail-closed: ${res.cause}")
        }
    }

    fun deleteMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
        fallbackKey = null
    }
}
