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

class CustomBridgeProvider(
    override val id: String,
    val baseUrl: String,
    val apiKey: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    private val httpClient: OkHttpClient,
    val endpointId: String = id
) : ModelProvider {

    override val type: ProviderType = ProviderType.CUSTOM
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(): String = baseUrl.trimEnd('/')

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url(cleanBaseUrl()).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            customHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            response.use { res ->
                ProviderHealth(
                    isHealthy = res.isSuccessful || res.code in 400..404,
                    latencyMs = latency,
                    message = "Custom bridge HTTP ${res.code}"
                )
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = 0L,
                message = e.localizedMessage ?: "Custom bridge unreachable"
            )
        }
    }

    override suspend fun listModels(): List<ModelDescriptor> = emptyList()

    override suspend fun capabilities(): ProviderCapabilities {
        return ProviderCapabilities(
            chat = CapabilityState.SUPPORTED,
            streaming = CapabilityState.SUPPORTED,
            tools = CapabilityState.UNSUPPORTED,
            structuredOutput = CapabilityState.UNSUPPORTED,
            vision = CapabilityState.UNKNOWN,
            modelDiscovery = CapabilityState.UNSUPPORTED,
            vaultContext = CapabilityState.UNKNOWN,
            persistentMemory = CapabilityState.UNSUPPORTED
        )
    }

    override fun streamChat(request: ChatRequest): Flow<InferenceEvent> = callbackFlow {
        trySend(InferenceEvent.Started(providerId = id, modelId = request.modelId))

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
            .url(cleanBaseUrl())
            .post(jsonReq.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        customHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

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
                val delta = choices?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                    ?: jsonObj.optString("text", "")
                if (!delta.isNullOrEmpty()) {
                    trySend(InferenceEvent.TextDelta(delta))
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
                    trySend(InferenceEvent.Failure("Custom Bridge HTTP ${response.code}: $errBody", recoverable = false))
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
