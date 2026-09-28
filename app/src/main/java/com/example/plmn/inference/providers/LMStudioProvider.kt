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

class LMStudioProvider(
    override val id: String,
    val baseUrl: String,
    val apiKey: String = "",
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.LM_STUDIO
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
            val response = httpClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val count = try {
                        val obj = JSONObject(body)
                        obj.optJSONArray("data")?.length() ?: 0
                    } catch (_: Exception) { 0 }
                    ProviderHealth(
                        isHealthy = true,
                        latencyMs = latency,
                        message = "LM Studio online ($count models loaded)"
                    )
                } else {
                    ProviderHealth(
                        isHealthy = false,
                        latencyMs = latency,
                        message = "LM Studio returned HTTP ${res.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "LM Studio server unreachable"
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
                                        displayName = "$modelId · LM Studio",
                                        providerId = id,
                                        endpointId = endpointId,
                                        contextLength = 4096,
                                        supportsStreaming = CapabilityState.SUPPORTED,
                                        supportsTools = CapabilityState.UNSUPPORTED,
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
            tools = CapabilityState.UNSUPPORTED,
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

        val call = httpClient.newCall(reqBuilder.build())

        var inputTokens: Int? = null
        var outputTokens: Int? = null

        val sseParser = SseStreamParser { data ->
            if (data == "[DONE]") {
                trySend(InferenceEvent.Usage(inputTokens = inputTokens, outputTokens = outputTokens))
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
                return@SseStreamParser
            }
            try {
                val jsonObj = JSONObject(data)
                val usage = jsonObj.optJSONObject("usage")
                if (usage != null) {
                    inputTokens = usage.optInt("prompt_tokens")
                    outputTokens = usage.optInt("completion_tokens")
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
                    trySend(InferenceEvent.Failure("LM Studio HTTP ${response.code}: $errBody", recoverable = false))
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
                trySend(InferenceEvent.Usage(inputTokens = inputTokens, outputTokens = outputTokens))
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
            } catch (e: Exception) {
                if (call.isCanceled()) {
                    trySend(InferenceEvent.Cancelled)
                } else {
                    trySend(InferenceEvent.Failure("LM Studio stream error: ${e.localizedMessage}", recoverable = false))
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
