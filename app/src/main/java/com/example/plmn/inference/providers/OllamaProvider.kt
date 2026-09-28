package com.example.plmn.inference.providers

import com.example.plmn.inference.CapabilityState
import com.example.plmn.inference.ChatRequest
import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ModelProvider
import com.example.plmn.inference.ProviderCapabilities
import com.example.plmn.inference.ProviderHealth
import com.example.plmn.inference.ProviderType
import com.example.plmn.inference.parser.NdjsonStreamParser
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

class OllamaProvider(
    override val id: String,
    val baseUrl: String,
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.OLLAMA
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(): String = baseUrl.trimEnd('/')

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = "${cleanBaseUrl()}/api/version"
            val req = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val ver = try { JSONObject(body).optString("version", "unknown") } catch (_: Exception) { "ok" }
                    ProviderHealth(
                        isHealthy = true,
                        latencyMs = latency,
                        message = "Ollama Daemon v$ver online"
                    )
                } else {
                    ProviderHealth(
                        isHealthy = false,
                        latencyMs = latency,
                        message = "Ollama returned HTTP ${res.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "Ollama server unreachable on LAN"
            )
        }
    }

    override suspend fun listModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        try {
            val url = "${cleanBaseUrl()}/api/tags"
            val req = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(req).execute()
            response.use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val modelsArr = root.optJSONArray("models")
                    if (modelsArr != null) {
                        val list = mutableListOf<ModelDescriptor>()
                        for (i in 0 until modelsArr.length()) {
                            val mObj = modelsArr.optJSONObject(i) ?: continue
                            val modelName = mObj.optString("name", "")
                            if (modelName.isNotBlank()) {
                                val details = mObj.optJSONObject("details")
                                val paramSize = details?.optString("parameter_size", "") ?: ""
                                val displayLabel = if (paramSize.isNotBlank()) "$modelName ($paramSize)" else modelName

                                list.add(
                                    ModelDescriptor(
                                        id = modelName,
                                        displayName = "$displayLabel · Ollama",
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

        val targetUrl = "${cleanBaseUrl()}/api/chat"

        val jsonReq = JSONObject().apply {
            put("model", request.modelId)
            put("stream", true)

            val options = JSONObject()
            if (request.temperature != null) {
                options.put("temperature", request.temperature.toDouble())
            }
            if (request.maxTokens != null) {
                options.put("num_predict", request.maxTokens)
            }
            put("options", options)

            val msgsArr = JSONArray()
            for (m in request.messages) {
                msgsArr.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
            put("messages", msgsArr)
        }

        val httpRequest = Request.Builder()
            .url(targetUrl)
            .post(jsonReq.toString().toRequestBody(jsonMediaType))
            .build()

        val call = httpClient.newCall(httpRequest)

        var promptEvalCount: Int? = null
        var evalCount: Int? = null

        val ndjsonParser = NdjsonStreamParser { jsonObj ->
            val msgObj = jsonObj.optJSONObject("message")
            val textDelta = msgObj?.optString("content", "") ?: jsonObj.optString("response", "")
            if (textDelta.isNotEmpty()) {
                trySend(InferenceEvent.TextDelta(textDelta))
            }

            if (jsonObj.has("prompt_eval_count")) {
                promptEvalCount = jsonObj.optInt("prompt_eval_count")
            }
            if (jsonObj.has("eval_count")) {
                evalCount = jsonObj.optInt("eval_count")
            }

            if (jsonObj.optBoolean("done", false)) {
                trySend(InferenceEvent.Usage(inputTokens = promptEvalCount, outputTokens = evalCount))
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
            }
        }

        Dispatchers.IO.asExecutor().execute {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    trySend(InferenceEvent.Failure("Ollama HTTP ${response.code}: $errBody", recoverable = false))
                    close()
                    return@execute
                }

                val reader = InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
                val charBuf = CharArray(1024)
                var read: Int
                while (reader.read(charBuf).also { read = it } != -1) {
                    ndjsonParser.processChunk(String(charBuf, 0, read))
                }
                ndjsonParser.processEof()
                trySend(InferenceEvent.Usage(inputTokens = promptEvalCount, outputTokens = evalCount))
                trySend(InferenceEvent.Completed(reason = "stop"))
                close()
            } catch (e: Exception) {
                if (call.isCanceled()) {
                    trySend(InferenceEvent.Cancelled)
                } else {
                    trySend(InferenceEvent.Failure("Ollama stream error: ${e.localizedMessage}", recoverable = false))
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
