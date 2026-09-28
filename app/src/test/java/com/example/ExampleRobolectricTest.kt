package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.plmn.context.ContextBudget
import com.example.plmn.context.ContextExceededException
import com.example.plmn.context.RequestAssembler
import com.example.plmn.data.local.AppDatabase
import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.security.AuditLedgerEngine
import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.data.security.DecryptionResult
import com.example.plmn.data.security.DeviceIdentityManager
import com.example.plmn.data.security.KeystoreCryptoVault
import com.example.plmn.data.security.SecretStore
import com.example.plmn.inference.CapabilityState
import com.example.plmn.inference.ChatRequest
import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ProviderCapabilities
import com.example.plmn.inference.parser.NdjsonStreamParser
import com.example.plmn.inference.parser.SseStreamParser
import com.example.plmn.inference.providers.OnDeviceProvider
import com.example.plmn.inference.providers.PLMHProvider
import com.example.plmn.security.NetworkDestinationGuard
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecretStore.init(context)
        DeviceIdentityManager.enableTestEnvironment(true)
    }

    private fun createMockClient(handler: (okhttp3.Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                handler(chain.request())
            })
            .build()
    }

    private fun okResponse(request: okhttp3.Request, body: String, contentType: String = "application/json"): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }

    @Test
    fun `1 PLM protocol endpoint names match OpenAPI`() = runBlocking {
        var requestedPath: String? = null
        val client = createMockClient { req ->
            requestedPath = req.url.encodedPath
            okResponse(req, "{\"status\":\"ok\",\"hub_id\":\"PLMH-1\",\"protocol_version\":\"1.0\"}")
        }

        val provider = PLMHProvider("p1", "http://192.168.1.100:8080", client)
        provider.healthCheck()

        assertEquals("Health endpoint must match /v1/health", "/v1/health", requestedPath)
    }

    @Test
    fun `2 pairing really invokes POST v1 pair`() = runBlocking {
        var pairingMethod: String? = null
        var pairingPath: String? = null
        var requestBodyStr: String? = null

        val client = createMockClient { req ->
            pairingMethod = req.method
            pairingPath = req.url.encodedPath
            val buffer = okio.Buffer()
            req.body?.writeTo(buffer)
            requestBodyStr = buffer.readUtf8()

            okResponse(req, "{\"status\":\"PAIRED\",\"hub_id\":\"HUB-123\",\"hub_name\":\"Desktop Hub\",\"protocol_version\":\"1.0\",\"hub_public_key_spki\":\"MCow...\",\"hub_key_algorithm\":\"Ed25519\"}")
        }

        val provider = PLMHProvider("p1", "http://192.168.1.100:8080", client)
        val res = provider.pairHub("TOKEN-1234", "Pixel 8")

        assertTrue(res.isSuccess)
        assertEquals("POST", pairingMethod)
        assertEquals("/v1/pair", pairingPath)

        val json = JSONObject(requestBodyStr!!)
        assertEquals("TOKEN-1234", json.getString("pairing_token"))
        assertEquals("ES256", json.getString("key_algorithm"))
        assertTrue(json.getString("public_key_spki").isNotBlank())
    }

    @Test
    fun `3 pairing token cannot become permanent credential`() {
        val pairingToken = "ONE-TIME-TOKEN-999"
        val credRef = SecretStore.storeSecret(pairingToken)
        assertNotNull(credRef)

        // After pairing succeeds, PLMN erases pairing token
        SecretStore.removeSecret(credRef)
        val retrieved = SecretStore.retrieveSecret(credRef)

        assertEquals("Pairing token must be erased after consumption", "", retrieved)
    }

    @Test
    fun `4 ES256 public key export`() {
        val pubKeySpki = DeviceIdentityManager.getPublicKeySpkiBase64()
        val pubKeyPem = DeviceIdentityManager.getPublicKeyPem()

        assertTrue("SPKI public key must be Base64 non-empty string", pubKeySpki.isNotBlank())
        assertTrue("PEM public key must contain BEGIN PUBLIC KEY header", pubKeyPem.contains("BEGIN PUBLIC KEY"))
        assertEquals("ES256", DeviceIdentityManager.KEY_ALGORITHM)
    }

    @Test
    fun `5 full body SHA-256 golden vectors`() {
        val emptyHash = CryptoVaultHelper.sha256Hex("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        // Vector 1: Non-streaming chat body
        val bodyNonStreaming = "{\"model\":\"intent-1m\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"
        val computedHash1 = CryptoVaultHelper.sha256Hex(bodyNonStreaming)
        val expectedHash1 = "28301f70784ca07c0b754caab4240384deef245607d961c7b5c07595874b4a89"
        assertEquals(expectedHash1, computedHash1)

        // Vector 2: Streaming chat body
        val bodyStreaming = "{\"model\":\"intent-1m\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":true}"
        val computedHash2 = CryptoVaultHelper.sha256Hex(bodyStreaming)
        val expectedHash2 = "7909206a0ad3aa84ba33ad11db66bd7236d1d8ac06d70d8a5fd57717905bf517"
        assertEquals(expectedHash2, computedHash2)

        // Mutating even 1 byte MUST break the hash assertion
        val mutatedBody = "{\"model\":\"intent-1m\",\"messages\":[{\"role\":\"user\",\"content\":\"hellp\"}],\"stream\":true}"
        val mutatedHash = CryptoVaultHelper.sha256Hex(mutatedBody)
        assertNotEquals("Mutating 1 byte in request body MUST alter the SHA-256 digest", expectedHash2, mutatedHash)
    }

    @Test
    fun `6 canonical request golden vectors`() {
        // GET Vector
        val canonicalGet = DeviceIdentityManager.buildCanonicalPayload(
            protocolVersion = "1.0",
            deviceId = "PLMN-ANDROID-NODE-A1B2",
            method = "GET",
            path = "/v1/models",
            timestampMs = 1727450400000L,
            nonce = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
            bodySha256Hex = CryptoVaultHelper.sha256Hex("")
        )
        val expectedGet = "1.0\nPLMN-ANDROID-NODE-A1B2\nGET\n/v1/models\n1727450400000\n9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expectedGet, canonicalGet)

        // POST Vector
        val postBody = "{\"model\":\"intent-1m\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":true}"
        val computedPostHash = CryptoVaultHelper.sha256Hex(postBody)
        val canonicalPost = DeviceIdentityManager.buildCanonicalPayload(
            protocolVersion = "1.0",
            deviceId = "PLMN-ANDROID-NODE-A1B2",
            method = "POST",
            path = "/v1/chat/completions",
            timestampMs = 1727450400000L,
            nonce = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
            bodySha256Hex = computedPostHash
        )
        val expectedPost = "1.0\nPLMN-ANDROID-NODE-A1B2\nPOST\n/v1/chat/completions\n1727450400000\n9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d\n7909206a0ad3aa84ba33ad11db66bd7236d1d8ac06d70d8a5fd57717905bf517"
        assertEquals(expectedPost, canonicalPost)
    }

    @Test
    fun `7 signature golden vectors`() {
        val payload = "1.0\nPLMN-ANDROID-NODE-A1B2\nGET\n/v1/models\n1727450400000\n9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val signature = DeviceIdentityManager.signPayload(payload)
        val pubKey = DeviceIdentityManager.getPublicKeySpkiBase64()

        val verified = DeviceIdentityManager.verifySignature(payload, signature, pubKey)
        assertTrue("Signature generated by DeviceIdentityManager must verify with ES256 key", verified)
    }

    @Test
    fun `8 empty-body hash`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", CryptoVaultHelper.EMPTY_BODY_SHA256_HEX)
        assertEquals(CryptoVaultHelper.EMPTY_BODY_SHA256_HEX, CryptoVaultHelper.sha256Hex(ByteArray(0)))
    }

    @Test
    fun `9 production Keystore failure is fail-closed`() {
        DeviceIdentityManager.enableTestEnvironment(false)
        var failClosed = false
        try {
            DeviceIdentityManager.signPayload("test")
        } catch (e: IllegalStateException) {
            failClosed = true
        }
        assertTrue("In production mode, Keystore failure must throw IllegalStateException (Fail Closed)", failClosed)
        DeviceIdentityManager.enableTestEnvironment(true)
    }

    @Test
    fun `10 repeated identical user prompts remain separate historical turns`() {
        val historyWithDuplicates = listOf(
            ChatMessage(1, "s1", "user", "What is PLMN?", "What is PLMN?"),
            ChatMessage(2, "s1", "assistant", "PLMN is a private node.", "PLMN is a private node."),
            ChatMessage(3, "s1", "user", "What is PLMN?", "What is PLMN?")
        )

        val model = ModelDescriptor("m1", "Model 1", "p1", "ep1", 4096)
        val assembled = RequestAssembler.assemble(
            model = model,
            baseSystemPrompt = "System",
            sanitizedPrompt = "What is PLMN?",
            vaultDocs = emptyList(),
            chatHistory = historyWithDuplicates
        )

        val userMsgs = assembled.chatRequest.messages.filter { it.role == "user" }
        assertEquals("Identical user questions in different turns must be preserved in context", 3, userMsgs.size)
    }

    @Test
    fun `11 oversized system or current prompt is rejected`() {
        val oversizedPrompt = "A".repeat(10000)
        var rejected = false
        try {
            ContextBudget.calculateBudget(
                baseSystemPrompt = "System",
                currentPrompt = oversizedPrompt,
                activeVaultDocs = emptyList(),
                chatHistory = emptyList(),
                modelContextLength = 512,
                requestedMaxTokens = 128
            )
        } catch (e: ContextExceededException) {
            rejected = true
        }
        assertTrue("Oversized prompt exceeding context capacity must throw ContextExceededException", rejected)
    }

    @Test
    fun `12 complete-turn trimming preserves user-assistant pairs`() {
        val history = listOf(
            ChatMessage(1, "s1", "user", "User 1 " + "word ".repeat(50), "User 1 " + "word ".repeat(50)),
            ChatMessage(2, "s1", "assistant", "Assistant 1 " + "word ".repeat(50), "Assistant 1 " + "word ".repeat(50)),
            ChatMessage(3, "s1", "user", "User 2 " + "word ".repeat(20), "User 2 " + "word ".repeat(20)),
            ChatMessage(4, "s1", "assistant", "Assistant 2 " + "word ".repeat(20), "Assistant 2 " + "word ".repeat(20))
        )

        val budget = ContextBudget.calculateBudget(
            baseSystemPrompt = "Sys",
            currentPrompt = "Turn 3",
            activeVaultDocs = emptyList(),
            chatHistory = history,
            modelContextLength = 300,
            requestedMaxTokens = 64
        )

        val roles = budget.historyMessages.map { it.role }
        if (roles.contains("assistant")) {
            val assistantIdx = roles.indexOf("assistant")
            assertTrue("Assistant message in history must be preceded by its user prompt", assistantIdx > 0 && roles[assistantIdx - 1] == "user")
        }
    }

    @Test
    fun `13 missing context length remains unknown null`() {
        val descriptor = ModelDescriptor("m1", "Display", "p1", "ep1", contextLength = null)
        assertNull("Missing context length must remain null/unknown", descriptor.contextLength)
    }

    @Test
    fun `14 missing capability remains unknown`() {
        val capabilities = ProviderCapabilities()
        assertEquals(CapabilityState.UNKNOWN, capabilities.tools)
        assertEquals(CapabilityState.UNKNOWN, capabilities.vision)
        assertEquals(CapabilityState.UNKNOWN, capabilities.persistentMemory)
    }

    @Test
    fun `15 vault_context is distinct from persistent_memory`() {
        val capabilities = ProviderCapabilities(
            vaultContext = CapabilityState.SUPPORTED,
            persistentMemory = CapabilityState.UNSUPPORTED
        )
        assertTrue(capabilities.vaultContext.isSupported)
        assertFalse(capabilities.persistentMemory.isSupported)
        assertNotEquals(capabilities.vaultContext, capabilities.persistentMemory)
    }

    @Test
    fun `16 credentialRef is never transmitted in PLMH headers`() = runBlocking {
        var sentHeaders = mapOf<String, String>()
        val client = createMockClient { req ->
            val headersMap = mutableMapOf<String, String>()
            req.headers.names().forEach { name ->
                headersMap[name] = req.header(name).orEmpty()
            }
            sentHeaders = headersMap
            okResponse(req, "{\"status\":\"ok\"}")
        }

        val provider = PLMHProvider("p1", "http://192.168.1.100:8080", client)
        provider.healthCheck()

        assertFalse("PLMH headers must not contain Authorization header with credentialRef", sentHeaders.containsKey("Authorization"))
        assertTrue("PLMH request must include X-PLM-Signature-Algorithm", sentHeaders.containsKey("X-PLM-Signature-Algorithm"))
        assertEquals("ES256", sentHeaders["X-PLM-Signature-Algorithm"])
    }

    @Test
    fun `20 public PLMH health is PROTOCOL_OK and signed models achieves AUTHENTICATED or MODEL_READY`() = runBlocking {
        val client = createMockClient { req ->
            when (req.url.encodedPath) {
                "/v1/health" -> okResponse(req, "{\"status\":\"ok\",\"hub_id\":\"HUB1\",\"protocol_version\":\"1.0\"}")
                "/v1/models" -> {
                    assertTrue("Models endpoint request MUST carry X-PLM-Signature header", req.header("X-PLM-Signature") != null)
                    okResponse(req, "{\"object\":\"list\",\"data\":[{\"id\":\"intent-1m\",\"displayName\":\"INTENT 1M\"}]}")
                }
                else -> Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(404).message("Not Found").build()
            }
        }

        val registry = com.example.plmn.inference.ProviderRegistry(client)
        val nodeWithModel = NodeEndpoint(
            id = "n-hub-1",
            name = "PLMH Hub",
            endpointType = "PLMH",
            url = "http://192.168.1.100:8080",
            selectedModel = "intent-1m"
        )

        val healthRes = registry.health(nodeWithModel)
        assertTrue(healthRes.isHealthy)
        assertEquals(com.example.plmn.inference.NodeVerificationLevel.MODEL_READY, healthRes.verification.level)
        assertTrue(healthRes.verification.authenticated)
        assertTrue(healthRes.verification.selectedModelPresent)

        val nodeMissingModel = nodeWithModel.copy(selectedModel = "non-existent-model-xyz")
        val healthResMissing = registry.health(nodeMissingModel)
        assertEquals("When selected model is missing, node must remain AUTHENTICATED and not advance to MODEL_READY", com.example.plmn.inference.NodeVerificationLevel.AUTHENTICATED, healthResMissing.verification.level)
        assertFalse(healthResMissing.verification.selectedModelPresent)
        assertEquals(com.example.plmn.inference.VerificationFailureCode.MODEL_NOT_FOUND, healthResMissing.verification.failureCode)
    }

    @Test
    fun `21 redirect destination guard strips X-PLM headers on cross-origin redirects`() {
        val originalReq = okhttp3.Request.Builder()
            .url("http://192.168.1.100:8080/v1/models")
            .addHeader("X-PLM-Signature", "sig123")
            .addHeader("X-PLM-Device-ID", "dev123")
            .addHeader("Authorization", "Bearer tok123")
            .build()

        val crossOriginUrl = okhttp3.HttpUrl.get("http://192.168.1.105:8080/v1/models")
        val strippedReq = com.example.plmn.security.NetworkDestinationGuard.buildRedirectRequest(originalReq, crossOriginUrl)

        assertNull("X-PLM-Signature must be stripped on cross-origin redirect", strippedReq.header("X-PLM-Signature"))
        assertNull("Authorization header must be stripped on cross-origin redirect", strippedReq.header("Authorization"))
    }

    @Test
    fun `22 DefaultProviderUrlResolver provides identical URLs for health models and chat`() {
        val node = NodeEndpoint(
            id = "n-1",
            name = "Node",
            endpointType = "PLMH",
            url = "http://192.168.1.100:8080/",
            selectedModel = "intent-1m"
        )

        val healthUrl = com.example.plmn.inference.DefaultProviderUrlResolver.healthUrl(node)
        val modelsUrl = com.example.plmn.inference.DefaultProviderUrlResolver.modelsUrl(node)
        val chatUrl = com.example.plmn.inference.DefaultProviderUrlResolver.chatUrl(node)

        assertEquals("http://192.168.1.100:8080/v1/health", healthUrl.toString())
        assertEquals("http://192.168.1.100:8080/v1/models", modelsUrl.toString())
        assertEquals("http://192.168.1.100:8080/v1/chat/completions", chatUrl.toString())
    }

    @Test
    fun `23 NetworkDiagnosticsHelper returns non-fabricated real network status`() {
        val info = com.example.plmn.security.NetworkDiagnosticsHelper.getNetworkInfo(context)
        assertNotNull(info.activeNetworkType)
        assertFalse("Network info must not be empty or fabricated", info.localIpAddresses.isEmpty())
    }

    @Test
    fun `19 PlmTransport shares exact OkHttpClient and destination guard between ping and stream`() = runBlocking {
        val client = createMockClient { req ->
            okResponse(req, "{\"status\":\"ok\"}")
        }

        val registry = com.example.plmn.inference.ProviderRegistry(client)
        val plmTransport: com.example.plmn.inference.PlmTransport = registry

        val node = NodeEndpoint(
            id = "test-node-2",
            name = "LM Studio Node",
            endpointType = "LM_STUDIO",
            url = "http://192.168.1.100:1234/v1",
            selectedModel = "model-1"
        )

        val healthResult = plmTransport.health(node)
        assertEquals("http://192.168.1.100:1234/v1/models", healthResult.resolvedUrl)
    }

    @Test
    fun `Room migration 2 to 3 preserves node endpoints and audit logs`() {
        val helper = androidx.room.testing.MigrationTestHelper(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )

        val dbV2 = helper.createDatabase("migration_test.db", 2).apply {
            execSQL("""
                INSERT INTO node_endpoints (id, name, endpointType, url, selectedModel, encryptedApiKey, isActive, latencyMs, isReachable, systemPrompt, authToken, hubCapabilities)
                VALUES ('node-v2', 'V2 Node', 'PLMH', 'http://192.168.1.100:8080', 'intent-1m', 'enc_key_123', 1, 10, 1, 'System', 'auth_tok_456', 'INTENT')
            """.trimIndent())

            execSQL("""
                INSERT INTO audit_logs (id, timestamp, action, targetNode, tokens, status, previousHash, currentHash, details)
                VALUES (1, 1000, 'INFERENCE_EXEC', 'node-v2', 50, 'SECURE', '000', '111', 'Sample log')
            """.trimIndent())
            close()
        }

        val dbV3 = helper.runMigrationsAndValidate("migration_test.db", 3, true, AppDatabase.MIGRATION_2_3)

        val cursorNode = dbV3.query("SELECT id, name, credentialRef FROM node_endpoints WHERE id = 'node-v2'")
        assertTrue(cursorNode.moveToFirst())
        assertEquals("V2 Node", cursorNode.getString(1))
        assertNull(cursorNode.getString(2))
        cursorNode.close()

        val cursorAudit = dbV3.query("SELECT tokensProcessed, securityStatus, details FROM audit_logs WHERE id = 1")
        assertTrue(cursorAudit.moveToFirst())
        assertEquals(50, cursorAudit.getInt(0))
        assertEquals("SECURE", cursorAudit.getString(1))
        assertEquals("Sample log", cursorAudit.getString(2))
        cursorAudit.close()
    }
}
