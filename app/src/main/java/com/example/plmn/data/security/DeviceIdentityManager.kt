package com.example.plmn.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * DeviceIdentityManager: Manages hardware-backed ECDSA P-256 (ES256) identity keypair in Android Keystore.
 *
 * Rules:
 * - Production Keystore failure is FAIL-CLOSED (throws IllegalStateException, never silently creates software key).
 * - Test environment can explicitly set test mode via enableTestEnvironment().
 * - Key Algorithm: "ES256" (ECDSA P-256 / SHA-256).
 * - Canonical Payload:
 *   protocol_version + "\n" + device_id + "\n" + HTTP_METHOD + "\n" + canonical_path + "\n" + timestamp_ms + "\n" + nonce + "\n" + lowercase_hex_sha256_body
 */
object DeviceIdentityManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IDENTITY_ALIAS = "plmn_ecdsa_device_identity_v1"
    private const val SIGN_ALGORITHM = "SHA256withECDSA"
    const val KEY_ALGORITHM = "ES256"

    private var isTestMode = false
    private var testKeyPair: java.security.KeyPair? = null

    @Synchronized
    fun enableTestEnvironment(enable: Boolean = true) {
        isTestMode = enable
        if (enable && testKeyPair == null) {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            testKeyPair = kpg.generateKeyPair()
        }
    }

    @Synchronized
    fun setTestKeyPair(keyPair: java.security.KeyPair) {
        isTestMode = true
        testKeyPair = keyPair
    }

    @Synchronized
    private fun getOrCreateKeyPair(): java.security.KeyPair {
        if (isTestMode && testKeyPair != null) {
            return testKeyPair!!
        }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(IDENTITY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    IDENTITY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()

                kpg.initialize(spec)
                kpg.generateKeyPair()
            }

            val entry = keyStore.getEntry(IDENTITY_ALIAS, null) as KeyStore.PrivateKeyEntry
            java.security.KeyPair(entry.certificate.publicKey, entry.privateKey)
        } catch (e: Exception) {
            if (isTestMode) {
                testKeyPair ?: run {
                    val kpg = KeyPairGenerator.getInstance("EC")
                    kpg.initialize(ECGenParameterSpec("secp256r1"))
                    val kp = kpg.generateKeyPair()
                    testKeyPair = kp
                    kp
                }
            } else {
                throw IllegalStateException("Android Keystore identity unavailable: Keystore failure (Fail Closed)", e)
            }
        }
    }

    fun getDeviceId(): String {
        val pubKeyBytes = getOrCreateKeyPair().public.encoded
        val hash = CryptoVaultHelper.sha256Fingerprint(Base64.encodeToString(pubKeyBytes, Base64.NO_WRAP))
        return "PLMN-$hash"
    }

    fun getPublicKeySpkiBase64(): String {
        val pubKeyBytes = getOrCreateKeyPair().public.encoded
        return Base64.encodeToString(pubKeyBytes, Base64.NO_WRAP)
    }

    fun getPublicKeyPem(): String {
        val base64 = getPublicKeySpkiBase64()
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    fun buildCanonicalPayload(
        protocolVersion: String,
        deviceId: String,
        method: String,
        path: String,
        timestampMs: Long,
        nonce: String,
        bodySha256Hex: String
    ): String {
        return "$protocolVersion\n$deviceId\n${method.uppercase()}\n$path\n$timestampMs\n$nonce\n${bodySha256Hex.lowercase()}"
    }

    fun signPayload(canonicalPayload: String): String {
        val privateKey = getOrCreateKeyPair().private
        val signer = Signature.getInstance(SIGN_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        val signatureBytes = signer.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    fun verifySignature(
        canonicalPayload: String,
        signatureBase64: String,
        publicKeySpkiOrPem: String
    ): Boolean {
        return try {
            val cleanBase64 = publicKeySpkiOrPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .trim()
            val pubBytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(pubBytes)
            val kf = KeyFactory.getInstance("EC")
            val pubKey: PublicKey = kf.generatePublic(keySpec)

            val verifier = Signature.getInstance(SIGN_ALGORITHM)
            verifier.initVerify(pubKey)
            verifier.update(canonicalPayload.toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        } catch (_: Exception) {
            false
        }
    }

    fun generateNonce(): String = UUID.randomUUID().toString()

    fun purgeIdentity() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(IDENTITY_ALIAS)) {
                keyStore.deleteEntry(IDENTITY_ALIAS)
            }
        } catch (_: Exception) {}
        testKeyPair = null
    }
}
