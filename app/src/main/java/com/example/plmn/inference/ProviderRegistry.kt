package com.example.plmn.inference

import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.security.SecretStore
import com.example.plmn.inference.providers.CustomBridgeProvider
import com.example.plmn.inference.providers.LMStudioProvider
import com.example.plmn.inference.providers.LlamaCppProvider
import com.example.plmn.inference.providers.OllamaProvider
import com.example.plmn.inference.providers.OnDeviceProvider
import com.example.plmn.inference.providers.OpenAICompatibleProvider
import com.example.plmn.inference.providers.PLMHProvider
import com.example.plmn.security.NetworkDestinationGuard
import com.example.plmn.security.NetworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ProviderRegistry(
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkDestinationGuard())
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    val urlResolver: ProviderUrlResolver = DefaultProviderUrlResolver
) : PlmTransport {

    private val providerCache = ConcurrentHashMap<String, ModelProvider>()
    private val defaultOnDeviceProvider = OnDeviceProvider()

    fun getProvider(endpoint: NodeEndpoint): ModelProvider {
        val pType = ProviderType.fromString(endpoint.endpointType)
        if (pType == ProviderType.ON_DEVICE) {
            return defaultOnDeviceProvider
        }
        return providerCache.getOrPut(endpoint.id) {
            createProvider(endpoint)
        }
    }

    fun createProvider(endpoint: NodeEndpoint): ModelProvider {
        val pType = ProviderType.fromString(endpoint.endpointType)
        val decryptedSecret = SecretStore.retrieveSecret(endpoint.credentialRef)

        return when (pType) {
            ProviderType.ON_DEVICE -> OnDeviceProvider(id = endpoint.id, displayName = endpoint.name)
            ProviderType.PLMH -> PLMHProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                httpClient = httpClient
            )
            ProviderType.OLLAMA -> OllamaProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                httpClient = httpClient
            )
            ProviderType.LM_STUDIO -> LMStudioProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                apiKey = decryptedSecret,
                httpClient = httpClient
            )
            ProviderType.LLAMA_CPP -> LlamaCppProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                apiKey = decryptedSecret,
                httpClient = httpClient
            )
            ProviderType.OPENAI_COMPATIBLE -> OpenAICompatibleProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                apiKey = decryptedSecret,
                httpClient = httpClient
            )
            ProviderType.CUSTOM -> CustomBridgeProvider(
                id = endpoint.id,
                baseUrl = endpoint.url,
                apiKey = decryptedSecret,
                httpClient = httpClient
            )
        }
    }

    override suspend fun health(node: NodeEndpoint): HealthResult = withContext(Dispatchers.IO) {
        val pType = ProviderType.fromString(node.endpointType)
        val startTime = System.currentTimeMillis()

        if (pType == ProviderType.ON_DEVICE) {
            val verification = NodeVerification(
                level = NodeVerificationLevel.STREAM_READY,
                resolvedAddresses = listOf("local://on-device-processor"),
                tcpReachable = true,
                protocolStatus = 200,
                authenticated = true,
                selectedModelPresent = true,
                streamVerifiedAt = System.currentTimeMillis(),
                checkedAt = System.currentTimeMillis()
            )
            return@withContext HealthResult(
                isHealthy = true,
                latencyMs = 2L,
                verification = verification,
                resolvedUrl = "local://on-device-processor",
                message = "PLMN Local Rules Engine (Offline local execution)"
            )
        }

        val healthUrl = urlResolver.healthUrl(node)
        val resolvedUrlStr = healthUrl?.toString() ?: node.url

        // Step 1: Evidence - DNS Lookup
        val resolvedIps = mutableListOf<String>()
        val host = healthUrl?.host ?: try { java.net.URI(node.url).host } catch (_: Exception) { null }

        if (host.isNullOrBlank()) {
            val verification = NodeVerification(
                level = NodeVerificationLevel.CONFIGURED,
                checkedAt = System.currentTimeMillis(),
                failureCode = VerificationFailureCode.DNS_LOOKUP_FAILED,
                failureMessage = "Invalid node URL host"
            )
            return@withContext HealthResult(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - startTime,
                verification = verification,
                resolvedUrl = resolvedUrlStr,
                message = "DNS Lookup Failed: Invalid host"
            )
        }

        try {
            val addrs = InetAddress.getAllByName(host)
            addrs.forEach { resolvedIps.add(it.hostAddress) }
        } catch (e: Exception) {
            val failureCode = VerificationFailureCode.fromException(e)
            val verification = NodeVerification(
                level = NodeVerificationLevel.CONFIGURED,
                checkedAt = System.currentTimeMillis(),
                failureCode = failureCode,
                failureMessage = "DNS lookup failed for '$host': ${e.localizedMessage}"
            )
            return@withContext HealthResult(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - startTime,
                verification = verification,
                resolvedUrl = resolvedUrlStr,
                message = "DNS Failure: ${e.localizedMessage}"
            )
        }

        // Step 2: Evidence - TCP / Transport & Public Liveness (PROTOCOL_OK)
        val provider = getProvider(node)
        val providerHealth = try {
            provider.healthCheck()
        } catch (e: Exception) {
            val failureCode = VerificationFailureCode.fromException(e)
            val verification = NodeVerification(
                level = NodeVerificationLevel.DISCOVERED,
                resolvedAddresses = resolvedIps,
                tcpReachable = false,
                checkedAt = System.currentTimeMillis(),
                failureCode = failureCode,
                failureMessage = e.localizedMessage
            )
            return@withContext HealthResult(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - startTime,
                verification = verification,
                resolvedUrl = resolvedUrlStr,
                message = "Transport Error: ${e.localizedMessage}"
            )
        }

        val latency = System.currentTimeMillis() - startTime
        val healthMsg = providerHealth.message.orEmpty()
        if (!providerHealth.isHealthy) {
            val verification = NodeVerification(
                level = NodeVerificationLevel.REACHABLE,
                resolvedAddresses = resolvedIps,
                tcpReachable = true,
                checkedAt = System.currentTimeMillis(),
                failureCode = VerificationFailureCode.HTTP_ERROR,
                failureMessage = healthMsg
            )
            return@withContext HealthResult(
                isHealthy = false,
                latencyMs = latency,
                verification = verification,
                resolvedUrl = resolvedUrlStr,
                message = healthMsg
            )
        }

        // Step 3: Evidence - Signed / Authenticated Discovery
        var isAuthenticated = false
        var discoveredModels = emptyList<ModelDescriptor>()

        try {
            discoveredModels = provider.listModels()
            isAuthenticated = true
        } catch (e: Exception) {
            val verification = NodeVerification(
                level = NodeVerificationLevel.PROTOCOL_OK,
                resolvedAddresses = resolvedIps,
                tcpReachable = true,
                protocolStatus = 200,
                authenticated = false,
                checkedAt = System.currentTimeMillis(),
                failureCode = VerificationFailureCode.fromException(e),
                failureMessage = "Authentication check failed: ${e.localizedMessage}"
            )
            return@withContext HealthResult(
                isHealthy = true,
                latencyMs = latency,
                verification = verification,
                resolvedUrl = resolvedUrlStr,
                message = "Protocol OK (Auth failed: ${e.localizedMessage})"
            )
        }

        // Step 4: Evidence - Selected Model Check (MODEL_READY)
        val isModelPresent = discoveredModels.any { m ->
            m.id.equals(node.selectedModel, ignoreCase = true) ||
                    m.id.contains(node.selectedModel, ignoreCase = true)
        }

        val finalLevel = when {
            isModelPresent -> NodeVerificationLevel.MODEL_READY
            isAuthenticated -> NodeVerificationLevel.AUTHENTICATED
            else -> NodeVerificationLevel.PROTOCOL_OK
        }

        val failureCode = if (!isModelPresent) VerificationFailureCode.MODEL_NOT_FOUND else null

        val verification = NodeVerification(
            level = finalLevel,
            resolvedAddresses = resolvedIps,
            tcpReachable = true,
            protocolStatus = 200,
            authenticated = isAuthenticated,
            selectedModelPresent = isModelPresent,
            checkedAt = System.currentTimeMillis(),
            failureCode = failureCode,
            failureMessage = if (!isModelPresent) "Configured model '${node.selectedModel}' absent in discovered models list" else null
        )

        HealthResult(
            isHealthy = true,
            latencyMs = latency,
            verification = verification,
            resolvedUrl = resolvedUrlStr,
            message = "Verified Level: ${finalLevel.name} (${discoveredModels.size} models discovered)"
        )
    }

    override suspend fun capabilities(node: NodeEndpoint): CapabilitiesResult {
        val provider = getProvider(node)
        val caps = provider.capabilities()
        val models = provider.listModels()
        return CapabilitiesResult(capabilities = caps, models = models)
    }

    override fun stream(node: NodeEndpoint, request: ChatRequest): Flow<InferenceEvent> {
        return streamChat(node, request, isAirgapEnabled = false)
    }

    fun invalidateProvider(endpointId: String) {
        providerCache.remove(endpointId)
    }

    fun streamChat(
        endpoint: NodeEndpoint,
        request: ChatRequest,
        isAirgapEnabled: Boolean
    ): Flow<InferenceEvent> = flow {
        val provider = getProvider(endpoint)

        if (!NetworkPolicy.validateExecution(provider.type, isAirgapEnabled)) {
            emit(
                InferenceEvent.Failure(
                    message = "Airgap isolation active: external network connection to ${provider.type.displayName} blocked.",
                    recoverable = false
                )
            )
            return@flow
        }

        provider.streamChat(request)
            .catch { e ->
                emit(
                    InferenceEvent.Failure(
                        message = "Provider stream exception: ${e.localizedMessage ?: "Unknown error"}",
                        recoverable = false
                    )
                )
            }
            .collect { event ->
                emit(event)
            }
    }
}
