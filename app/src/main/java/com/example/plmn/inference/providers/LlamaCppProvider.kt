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

class LlamaCppProvider(
    override val id: String,
    val baseUrl: String,
    val apiKey: String = "",
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.LLAMA_CPP
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(): String = baseUrl.trimEnd('/')

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = "${cleanBaseUrl()}/health"
            val req = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                if (res.isSuccessful) {
                    ProviderHealth(
                        isHealthy = true,
                        latencyMs = latency,
                        message = "llama.cpp server online (Status 200)"
                    )
                } else {
                    val v1Url = "${cleanBaseUrl()}/v1/models"
                    val v1Req = Request.Builder().url(v1Url).get().build()
                    val v1Res = httpClient.newCall(v1Req).execute()
                    val v1Latency = System.currentTimeMillis() - startTime
                    v1Res.use { r ->
                        ProviderHealth(
                            isHealthy = r.isSuccessful,
                            latencyMs = v1Latency,
                            message = if (r.isSuccessful) "llama.cpp server online (/v1/models)" else "llama.cpp HTTP ${r.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "llama.cpp server unreachable"
            )
        }
    }

    override suspend fun listModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        try {
            val url = "${cleanBaseUrl()}/v1/models"
            val req = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(req).execute()
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
                                        displayName = "$modelId · llama.cpp",
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

        val targetUrl = if (cleanBaseUrl().endsWith("/v1")) {
            "${cleanBaseUrl()}/chat/completions"
        } else {
            "${cleanBaseUrl()}/v1/chat/completions"
        }

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
                    val choice = choices.optJSONObject(0)
                    val delta = choice?.optJSONObject("delta")
                    val textDelta = delta?.optString("content", "")
                        ?: choice?.optString("text", "") ?: ""
                    if (textDelta.isNotEmpty()) {
                        trySend(InferenceEvent.TextDelta(textDelta))
                    }
                } else {
                    val content = jsonObj.optString("content", "")
                    if (content.isNotEmpty()) {
                        trySend(InferenceEvent.TextDelta(content))
                    }
                }
            } catch (_: Exception) {}
        }

        Dispatchers.IO.asExecutor().execute {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    trySend(InferenceEvent.Failure("llama.cpp HTTP ${response.code}: $errBody", recoverable = false))
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
                    trySend(InferenceEvent.Failure("llama.cpp stream error: ${e.localizedMessage}", recoverable = false))
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
