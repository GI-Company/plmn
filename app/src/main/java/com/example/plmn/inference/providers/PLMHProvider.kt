package com.example.plmn.inference.providers

import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.data.security.DeviceIdentityManager
import com.example.plmn.inference.CapabilityState
import com.example.plmn.inference.ChatRequest
import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ModelProvider
import com.example.plmn.inference.ProviderCapabilities
import com.example.plmn.inference.ProviderHealth
import com.example.plmn.inference.ProviderType
import com.example.plmn.inference.parser.SseStreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

data class HubPairingResult(
    val isSuccess: Boolean,
    val hubId: String = "",
    val hubName: String = "",
    val protocolVersion: String = "1.0",
    val hubPublicKeySpki: String = "",
    val hubKeyAlgorithm: String = "Ed25519",
    val errorMessage: String = ""
)

/**
 * PLMHProvider: First-Class Private Language Model Hub Adapter
 *
 * Implements PLM Protocol v1.0 over local networks:
 * - Endpoints: /v1/health, /v1/capabilities, /v1/models, /v1/pair, /v1/chat/completions
 * - Signed Device Request Authentication: ES256 (ECDSA P-256 / SHA-256)
 * - Strict Canonical Request Signing (protocol_version|device_id|method|path|timestamp|nonce|body_sha256_hex)
 * - NO Bearer credential transmission (credentialRef is local-only)
 * - Incremental SSE streaming parser with HTTP cancellation
 */
class PLMHProvider(
    override val id: String,
    val baseUrl: String,
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.PLMH
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var cachedCapabilities: ProviderCapabilities? = null
    private var cachedModels: List<ModelDescriptor> = emptyList()

    private fun cleanBaseUrl(): String = baseUrl.trimEnd('/')

    private fun buildSignedHeaders(
        method: String,
        path: String,
        bodyBytes: ByteArray = ByteArray(0)
    ): Map<String, String> {
        val timestamp = System.currentTimeMillis()
        val nonce = DeviceIdentityManager.generateNonce()
        val deviceId = DeviceIdentityManager.getDeviceId()

        val bodySha256Hex = CryptoVaultHelper.sha256Hex(bodyBytes)

        val canonicalPayload = DeviceIdentityManager.buildCanonicalPayload(
            protocolVersion = "1.0",
            deviceId = deviceId,
            method = method,
            path = path,
            timestampMs = timestamp,
            nonce = nonce,
            bodySha256Hex = bodySha256Hex
        )

        val signature = DeviceIdentityManager.signPayload(canonicalPayload)

        return mapOf(
            "X-PLM-Protocol-Version" to "1.0",
            "X-PLM-Device-ID" to deviceId,
            "X-PLM-Timestamp" to timestamp.toString(),
            "X-PLM-Nonce" to nonce,
            "X-PLM-Signature-Algorithm" to DeviceIdentityManager.KEY_ALGORITHM,
            "X-PLM-Signature" to signature
        )
    }

    suspend fun pairHub(pairingToken: String, deviceName: String = "Android Node"): HubPairingResult = withContext(Dispatchers.IO) {
        try {
            val path = "/v1/pair"
            val url = "${cleanBaseUrl()}$path"
            val deviceId = DeviceIdentityManager.getDeviceId()
            val pubKeySpki = DeviceIdentityManager.getPublicKeySpkiBase64()

            val jsonReq = JSONObject().apply {
                put("pairing_token", pairingToken.trim())
                put("device_id", deviceId)
                put("device_name", deviceName.ifBlank { "Android Node" })
                put("key_algorithm", DeviceIdentityManager.KEY_ALGORITHM)
                put("public_key_spki", pubKeySpki)
            }

            val requestBodyStr = jsonReq.toString()
            val req = Request.Builder()
                .url(url)
                .post(requestBodyStr.toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    HubPairingResult(
                        isSuccess = true,
                        hubId = json.optString("hub_id", id),
                        hubName = json.optString("hub_name", "PLMH Desktop Hub"),
                        protocolVersion = json.optString("protocol_version", "1.0"),
                        hubPublicKeySpki = json.optString("hub_public_key_spki", ""),
                        hubKeyAlgorithm = json.optString("hub_key_algorithm", "Ed25519")
                    )
                } else {
                    val errBody = res.body?.string().orEmpty()
                    HubPairingResult(
                        isSuccess = false,
                        errorMessage = "PLMH Pairing Failed (HTTP ${res.code}): $errBody"
                    )
                }
            }
        } catch (e: Exception) {
            HubPairingResult(
                isSuccess = false,
                errorMessage = "PLMH Pairing Error: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val path = "/v1/health"
            val url = "${cleanBaseUrl()}$path"
            val req = Request.Builder().url(url).get().build()

            val response = httpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string().orEmpty()
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { null }
                    val hubId = json?.optString("hub_id", "PLMH") ?: "PLMH"
                    val ver = json?.optString("protocol_version", "1.0") ?: "1.0"
                    ProviderHealth(
                        isHealthy = true,
                        latencyMs = latency,
                        message = "PLMH Hub '$hubId' v$ver Online"
                    )
                } else {
                    ProviderHealth(
                        isHealthy = false,
                        latencyMs = latency,
                        message = "PLMH Hub HTTP ${res.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "PLMH Hub unreachable"
            )
        }
    }

    override suspend fun listModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        if (cachedModels.isNotEmpty()) {
            return@withContext cachedModels
        }

        try {
            val path = "/v1/models"
            val url = "${cleanBaseUrl()}$path"
            val reqBuilder = Request.Builder().url(url).get()
            buildSignedHeaders("GET", path).forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: root.optJSONArray("models")
                    if (data != null) {
                        val list = mutableListOf<ModelDescriptor>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val modelId = item.optString("id", "")
                            if (modelId.isNotBlank()) {
                                val ctxLen = if (item.has("context_length") && !item.isNull("context_length")) {
                                    item.optInt("context_length")
                                } else null

                                list.add(
                                    ModelDescriptor(
                                        id = modelId,
                                        displayName = "$modelId (PLMH)",
                                        providerId = id,
                                        endpointId = endpointId,
                                        contextLength = ctxLen,
                                        supportsStreaming = CapabilityState.fromBoolean(item.optBoolean("supports_streaming", true)),
                                        supportsTools = CapabilityState.fromBoolean(if (item.has("supports_tools")) item.optBoolean("supports_tools") else null),
                                        supportsVision = CapabilityState.fromBoolean(if (item.has("supports_vision")) item.optBoolean("supports_vision") else null),
                                        supportsVaultContext = CapabilityState.fromBoolean(if (item.has("supports_vault_context")) item.optBoolean("supports_vault_context") else null),
                                        supportsPersistentMemory = CapabilityState.fromBoolean(if (item.has("supports_persistent_memory")) item.optBoolean("supports_persistent_memory") else null)
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) {
                            cachedModels = list
                            return@withContext list
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        emptyList()
    }

    override suspend fun capabilities(): ProviderCapabilities = withContext(Dispatchers.IO) {
        cachedCapabilities?.let { return@withContext it }

        try {
            val path = "/v1/capabilities"
            val url = "${cleanBaseUrl()}$path"
            val reqBuilder = Request.Builder().url(url).get()
            buildSignedHeaders("GET", path).forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val caps = ProviderCapabilities(
                        chat = CapabilityState.fromString(json.optString("chat")),
                        streaming = CapabilityState.fromString(json.optString("streaming")),
                        tools = CapabilityState.fromString(json.optString("tools")),
                        structuredOutput = CapabilityState.fromString(json.optString("structured_output")),
                        vision = CapabilityState.fromString(json.optString("vision")),
                        modelDiscovery = CapabilityState.fromString(json.optString("model_discovery")),
                        vaultContext = CapabilityState.fromString(json.optString("vault_context")),
                        persistentMemory = CapabilityState.fromString(json.optString("persistent_memory"))
                    )
                    cachedCapabilities = caps
                    return@withContext caps
                }
            }
        } catch (_: Exception) {}

        ProviderCapabilities()
    }

    override fun streamChat(request: ChatRequest): Flow<InferenceEvent> = callbackFlow {
        trySend(InferenceEvent.Started(providerId = id, modelId = request.modelId))

        val path = "/v1/chat/completions"
        val targetUrl = "${cleanBaseUrl()}$path"

        val jsonReq = JSONObject().apply {
            put("model", request.modelId)
            put("stream", true)
            if (request.temperature != null) {
                put("temperature", request.temperature.toDouble())
            }
            if (request.maxTokens != null) {
                put("max_tokens", request.maxTokens)
            }

            val msgsArr = JSONArray()
            for (m in request.messages) {
                msgsArr.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                    if (!m.name.isNullOrBlank()) put("name", m.name)
                })
            }
            put("messages", msgsArr)
        }

        val requestBodyBytes = jsonReq.toString().toByteArray(Charsets.UTF_8)
        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestBodyBytes.toRequestBody(jsonMediaType))

        buildSignedHeaders("POST", path, requestBodyBytes).forEach { (k, v) ->
            reqBuilder.addHeader(k, v)
        }

        val call = httpClient.newCall(reqBuilder.build())

        val sseParser = SseStreamParser { data ->
            if (data == "[DONE]") {
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
                return@SseStreamParser
            }
            try {
                val jsonObj = JSONObject(data)
                val choices = jsonObj.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                    val textChunk = delta?.optString("content", "") ?: ""
                    if (textChunk.isNotEmpty()) {
                        trySend(InferenceEvent.TextDelta(textChunk))
                    }
                }
            } catch (_: Exception) {
                if (data.isNotEmpty()) trySend(InferenceEvent.TextDelta(data))
            }
        }

        Dispatchers.IO.asExecutor().execute {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    trySend(InferenceEvent.Failure("PLMH HTTP ${response.code}: $errBody", recoverable = false))
                    close()
                    return@execute
                }

                val reader = InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
                val charBuf = CharArray(1024)
                var read: Int
                while (reader.read(charBuf).also { read = it } != -1) {
                    sseParser.processChunk(String(charBuf, 0, read))
                }
                sseParser.processEof()
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
            } catch (e: Exception) {
                if (call.isCanceled()) {
                    trySend(InferenceEvent.Cancelled)
                } else {
                    trySend(InferenceEvent.Failure("Stream error: ${e.localizedMessage}", recoverable = false))
                }
                close()
            }
        }

        awaitClose {
            if (!call.isCanceled()) {
                call.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)
}
