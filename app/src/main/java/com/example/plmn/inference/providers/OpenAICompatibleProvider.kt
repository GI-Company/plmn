package com.example.plmn.inference.providers

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

class OpenAICompatibleProvider(
    override val id: String,
    val baseUrl: String,
    val apiKey: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.OPENAI_COMPATIBLE
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = "${cleanBaseUrl()}/models"
            val reqBuilder = Request.Builder().url(url).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            customHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                val ok = res.isSuccessful || res.code == 401
                ProviderHealth(
                    isHealthy = ok,
                    latencyMs = latency,
                    message = if (res.isSuccessful) "OpenAI-Compatible endpoint reachable" else "Authorization required (HTTP ${res.code})"
                )
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "Endpoint unreachable"
            )
        }
    }

    override suspend fun listModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        try {
            val url = "${cleanBaseUrl()}/models"
            val reqBuilder = Request.Builder().url(url).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            customHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data")
                    if (data != null) {
                        val list = mutableListOf<ModelDescriptor>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val modelId = item.optString("id", "")
                            if (modelId.isNotBlank()) {
                                list.add(
                                    ModelDescriptor(
                                        id = modelId,
                                        displayName = "$modelId · OpenAI-Compatible",
                                        providerId = id,
                                        endpointId = endpointId,
                                        contextLength = 4096,
                                        supportsStreaming = CapabilityState.SUPPORTED,
                                        supportsTools = CapabilityState.SUPPORTED,
                                        supportsVision = CapabilityState.UNKNOWN,
                                        supportsVaultContext = CapabilityState.UNKNOWN,
                                        supportsPersistentMemory = CapabilityState.UNSUPPORTED
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            }
        } catch (_: Exception) {}

        emptyList()
    }

    override suspend fun capabilities(): ProviderCapabilities {
        return ProviderCapabilities(
            chat = CapabilityState.SUPPORTED,
            streaming = CapabilityState.SUPPORTED,
            tools = CapabilityState.SUPPORTED,
            structuredOutput = CapabilityState.UNSUPPORTED,
            vision = CapabilityState.UNKNOWN,
            modelDiscovery = CapabilityState.SUPPORTED,
            vaultContext = CapabilityState.UNKNOWN,
            persistentMemory = CapabilityState.UNSUPPORTED
        )
    }

    override fun streamChat(request: ChatRequest): Flow<InferenceEvent> = callbackFlow {
        trySend(InferenceEvent.Started(providerId = id, modelId = request.modelId))

        val targetUrl = "${cleanBaseUrl()}/chat/completions"

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
                })
            }
            put("messages", msgsArr)
        }

        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .post(jsonReq.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        customHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        val call = httpClient.newCall(reqBuilder.build())

        var inTokens: Int? = null
        var outTokens: Int? = null

        val sseParser = SseStreamParser { data ->
            if (data == "[DONE]") {
                trySend(InferenceEvent.Usage(inputTokens = inTokens, outputTokens = outTokens))
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
                return@SseStreamParser
            }
            try {
                val jsonObj = JSONObject(data)
                val usage = jsonObj.optJSONObject("usage")
                if (usage != null) {
                    inTokens = usage.optInt("prompt_tokens")
                    outTokens = usage.optInt("completion_tokens")
                }

                val choices = jsonObj.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.optJSONObject(0)
                    val delta = choice?.optJSONObject("delta")
                    val textDelta = delta?.optString("content", "") ?: ""
                    if (textDelta.isNotEmpty()) {
                        trySend(InferenceEvent.TextDelta(textDelta))
                    }
                }
            } catch (_: Exception) {}
        }

        Dispatchers.IO.asExecutor().execute {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    trySend(InferenceEvent.Failure("HTTP ${response.code}: $errBody", recoverable = false))
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
                trySend(InferenceEvent.Usage(inputTokens = inTokens, outputTokens = outTokens))
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
