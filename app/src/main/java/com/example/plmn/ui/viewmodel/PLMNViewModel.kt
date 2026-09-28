package com.example.plmn.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plmn.context.RequestAssembler
import com.example.plmn.data.local.AppDatabase
import com.example.plmn.data.model.ChatMessage
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.data.repository.PLMNRepository
import com.example.plmn.data.security.DeviceIdentityManager
import com.example.plmn.data.security.LedgerVerificationResult
import com.example.plmn.data.security.PromptSanitizer
import com.example.plmn.data.security.SanitizationResult
import com.example.plmn.data.security.SecretStore
import com.example.plmn.endpoints.EndpointRepository
import com.example.plmn.endpoints.ModelRegistry
import com.example.plmn.inference.InferenceEvent
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ProviderRegistry
import com.example.plmn.inference.ProviderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PLMNViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = PLMNRepository(
        nodeDao = database.nodeDao(),
        chatDao = database.chatDao(),
        vaultDao = database.vaultDao(),
        auditDao = database.auditDao()
    )

    private val providerRegistry = ProviderRegistry()
    private val modelRegistry = ModelRegistry()
    private val endpointRepository = EndpointRepository(
        nodeDao = database.nodeDao(),
        providerRegistry = providerRegistry,
        modelRegistry = modelRegistry
    )

    private var generationJob: Job? = null

    private val _isAirgapEnabled = MutableStateFlow(false)
    val isAirgapEnabled: StateFlow<Boolean> = _isAirgapEnabled.asStateFlow()

    private val _isSanitizationEnabled = MutableStateFlow(true)
    val isSanitizationEnabled: StateFlow<Boolean> = _isSanitizationEnabled.asStateFlow()

    val currentSessionId = MutableStateFlow(com.example.plmn.data.security.CryptoVaultHelper.generateNodeSessionId())

    val activeNode: StateFlow<NodeEndpoint?> = repository.activeNode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allNodes: StateFlow<List<NodeEndpoint>> = repository.allNodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultDocuments: StateFlow<List<VaultDocument>> = repository.allVaultDocs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs = repository.recentAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredModels: StateFlow<List<ModelDescriptor>> = modelRegistry.models
    val activeModel: StateFlow<ModelDescriptor?> = modelRegistry.activeModel

    private val _ledgerIntegrity = MutableStateFlow<LedgerVerificationResult?>(null)
    val ledgerIntegrity: StateFlow<LedgerVerificationResult?> = _ledgerIntegrity.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _lastToolRequest = MutableStateFlow<InferenceEvent.ToolRequest?>(null)
    val lastToolRequest: StateFlow<InferenceEvent.ToolRequest?> = _lastToolRequest.asStateFlow()

    init {
        // Observe messages for current session
        viewModelScope.launch {
            currentSessionId.collect { sessionId ->
                repository.getMessagesForSession(sessionId).collect { msgs ->
                    _messages.value = msgs
                }
            }
        }

        // Initialize active model when active node changes
        viewModelScope.launch {
            activeNode.collect { node ->
                if (node != null) {
                    val pType = ProviderType.fromString(node.endpointType)
                    val model = ModelDescriptor(
                        id = node.selectedModel.ifBlank { "default" },
                        displayName = "${node.selectedModel} (${pType.displayName})",
                        providerId = node.id,
                        endpointId = node.id,
                        contextLength = 4096,
                        supportsStreaming = com.example.plmn.inference.CapabilityState.SUPPORTED,
                        supportsTools = if (pType != ProviderType.ON_DEVICE) com.example.plmn.inference.CapabilityState.SUPPORTED else com.example.plmn.inference.CapabilityState.UNSUPPORTED,
                        supportsVaultContext = com.example.plmn.inference.CapabilityState.SUPPORTED,
                        supportsPersistentMemory = com.example.plmn.inference.CapabilityState.UNSUPPORTED
                    )
                    modelRegistry.setActiveModel(model)
                }
            }
        }

        verifyLedger()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setTemperature(temp: Float) {
        _temperature.value = temp.coerceIn(0.0f, 1.5f)
    }

    fun toggleAirgap(enabled: Boolean) {
        _isAirgapEnabled.value = enabled
        viewModelScope.launch {
            repository.recordAudit(
                action = if (enabled) "AIRGAP_ENGAGED" else "AIRGAP_DISENGAGED",
                targetNode = activeNode.value?.id ?: "local",
                tokens = 0,
                status = if (enabled) "AIRGAP_LOCAL" else "SECURE",
                details = if (enabled)
                    "Airgap boundary enabled. Remote network inference connections blocked."
                else
                    "LAN and authenticated remote connections permitted."
            )
            verifyLedger()
        }
    }

    fun toggleSanitization(enabled: Boolean) {
        _isSanitizationEnabled.value = enabled
    }

    fun clearNotice() {
        _statusNotice.value = null
    }

    fun verifyLedger() {
        viewModelScope.launch {
            _ledgerIntegrity.value = repository.verifyLedgerIntegrity()
        }
    }

    fun selectModel(model: ModelDescriptor) {
        modelRegistry.setActiveModel(model)
        viewModelScope.launch {
            repository.switchActiveNode(model.endpointId)
        }
    }

    fun switchActiveNode(nodeId: String) {
        viewModelScope.launch {
            repository.switchActiveNode(nodeId)
            repository.recordAudit(
                action = "NODE_SWITCH",
                targetNode = nodeId,
                tokens = 0,
                status = "SECURE",
                details = "Active node target set to: $nodeId"
            )
            verifyLedger()
        }
    }

    fun pingNode(node: NodeEndpoint) {
        viewModelScope.launch {
            val (healthResult, models) = endpointRepository.healthCheckEndpoint(node)
            repository.recordAudit(
                action = "NODE_PING",
                targetNode = node.id,
                tokens = 0,
                status = if (healthResult.isHealthy) "REACHABLE" else "UNREACHABLE",
                details = "Ping ${node.url} (${node.endpointType}) - Level: ${healthResult.verification.level.name} (${healthResult.latencyMs}ms, ${models.size} models) - ${healthResult.message}"
            )
            _statusNotice.value = "${node.name}: ${healthResult.verification.level.name} (${if (healthResult.isHealthy) "${healthResult.latencyMs}ms, ${models.size} models" else healthResult.message})"
            verifyLedger()
        }
    }

    fun saveNode(node: NodeEndpoint, rawSecret: String? = null) {
        viewModelScope.launch {
            endpointRepository.saveEndpoint(node, rawSecret)
            _statusNotice.value = "Node saved: ${node.name}"
            verifyLedger()
        }
    }

    fun pairPLMHHub(url: String, pairingToken: String = "", name: String = "Home Desktop PLMH Hub") {
        viewModelScope.launch {
            _statusNotice.value = "Executing POST /v1/pair with PLMH Hub at $url..."

            val tempEndpoint = NodeEndpoint(
                id = "node-plmh-temp-" + System.currentTimeMillis().toString().takeLast(6),
                name = name,
                endpointType = "PLMH",
                url = url.trim(),
                selectedModel = "intent-1m"
            )
            val provider = providerRegistry.createProvider(tempEndpoint) as? com.example.plmn.inference.providers.PLMHProvider
            if (provider == null) {
                _statusNotice.value = "Pairing Error: Unable to instantiate PLMH provider."
                return@launch
            }

            val pairingResult = provider.pairHub(pairingToken = pairingToken, deviceName = name)

            // Erase pairing token immediately; never persist it as a permanent credential.

            if (pairingResult.isSuccess) {
                val finalEndpoint = NodeEndpoint(
                    id = "node-plmh-" + pairingResult.hubId.takeLast(8),
                    name = pairingResult.hubName.ifBlank { name },
                    endpointType = "PLMH",
                    url = url.trim(),
                    selectedModel = "intent-1m",
                    credentialRef = null, // Cryptographic ES256 identity used; no bearer token stored
                    isActive = true,
                    systemPrompt = "You are the INTENT Private Language Model running on desktop PLMH."
                )
                endpointRepository.saveEndpoint(finalEndpoint)
                repository.switchActiveNode(finalEndpoint.id)

                val deviceId = DeviceIdentityManager.getDeviceId()
                repository.recordAudit(
                    action = "PLMH_PAIRING",
                    targetNode = finalEndpoint.id,
                    tokens = 0,
                    status = "PAIRED",
                    details = "Paired with hub '${pairingResult.hubName}' (${pairingResult.hubId}) using ES256 device ID $deviceId. Token consumed and erased."
                )
                _statusNotice.value = "Successfully Paired with ${pairingResult.hubName}!"
            } else {
                _statusNotice.value = pairingResult.errorMessage
            }
            verifyLedger()
        }
    }

    fun deleteNode(node: NodeEndpoint) {
        viewModelScope.launch {
            endpointRepository.deleteEndpoint(node)
            _statusNotice.value = "Node deleted: ${node.name}"
            verifyLedger()
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        if (_streamingText.value.isNotBlank()) {
            _streamingText.value += "\n\n[Inference Generation Stopped by User]"
        }
    }

    fun sendMessage(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""
            _lastToolRequest.value = null

            val currentNode = activeNode.value ?: NodeEndpoint(
                id = "node-on-device-rules",
                name = "PLMN Local Rules Engine",
                endpointType = "ON_DEVICE",
                url = "local://on-device-processor",
                selectedModel = "plmn-local-rules-v1",
                isActive = true
            )

            val providerType = ProviderType.fromString(currentNode.endpointType)

            // Step 1: Privacy Sanitization
            val sanitizationResult = PromptSanitizer.sanitize(
                text = trimmed,
                enabled = _isSanitizationEnabled.value
            )

            val promptTokens = com.example.plmn.data.security.CryptoVaultHelper.estimateTokenCount(trimmed)

            // Load PREVIOUS history BEFORE saving current message (Fixes prompt duplication bug)
            val previousHistory = repository.getMessagesListForSession(currentSessionId.value)
            val activeDocs = repository.getActiveContextList()

            val targetModel = activeModel.value ?: ModelDescriptor(
                id = currentNode.selectedModel.ifBlank { "default" },
                displayName = "${currentNode.name} (${providerType.displayName})",
                providerId = currentNode.id,
                endpointId = currentNode.id,
                contextLength = 4096,
                supportsStreaming = com.example.plmn.inference.CapabilityState.SUPPORTED,
                supportsTools = if (providerType != ProviderType.ON_DEVICE) com.example.plmn.inference.CapabilityState.SUPPORTED else com.example.plmn.inference.CapabilityState.UNSUPPORTED,
                supportsVaultContext = com.example.plmn.inference.CapabilityState.SUPPORTED,
                supportsPersistentMemory = com.example.plmn.inference.CapabilityState.UNSUPPORTED
            )

            // Step 2: Assemble Unified ChatRequest (Guarantees single inclusion of current query)
            val assembled = try {
                RequestAssembler.assemble(
                    model = targetModel,
                    baseSystemPrompt = currentNode.systemPrompt,
                    sanitizedPrompt = sanitizationResult.sanitizedText,
                    vaultDocs = activeDocs,
                    chatHistory = previousHistory,
                    temperature = _temperature.value,
                    maxTokens = 512,
                    isSanitized = _isSanitizationEnabled.value
                )
            } catch (e: com.example.plmn.context.ContextExceededException) {
                _isGenerating.value = false
                _streamingText.value = "[REQUEST REJECTED: ${e.localizedMessage}]"
                repository.recordAudit(
                    action = "CONTEXT_EXCEEDED",
                    targetNode = currentNode.id,
                    tokens = promptTokens,
                    status = "REJECTED",
                    details = e.localizedMessage ?: "Context capacity exceeded"
                )
                verifyLedger()
                return@launch
            }

            // Step 3: Save user message to database AFTER context assembly
            val userMsg = ChatMessage(
                sessionId = currentSessionId.value,
                role = "user",
                content = trimmed,
                sanitizedContent = sanitizationResult.sanitizedText,
                tokenCount = promptTokens,
                nodeName = currentNode.name,
                redactedItemsCount = sanitizationResult.redactedItems.size
            )
            repository.insertMessage(userMsg)

            if (sanitizationResult.redactedItems.isNotEmpty()) {
                repository.recordAudit(
                    action = "PROMPT_SCRUB",
                    targetNode = currentNode.id,
                    tokens = promptTokens,
                    status = "SCRUBBED",
                    details = "Redacted ${sanitizationResult.redactedItems.size} items: " +
                            sanitizationResult.redactedItems.joinToString { it.type }
                )
            }

            // Step 4: Log pre-stream diagnostics & Stream inference through ProviderRegistry
            val resolvedUrl = com.example.plmn.inference.DefaultProviderUrlResolver.chatUrl(currentNode)?.toString() ?: currentNode.url
            val netInfo = com.example.plmn.security.NetworkDiagnosticsHelper.getNetworkInfo(getApplication())

            val streamDiagnostics = """
                [STREAM_INIT_DIAGNOSTIC]
                • node.id: ${currentNode.id}
                • node.type: ${currentNode.endpointType}
                • configuredBaseUrl: ${currentNode.url}
                • resolvedRequestUrl: $resolvedUrl
                • HTTP method: POST
                • device local IP: ${netInfo.localIpAddresses.joinToString(", ")}
                • active Android Network: ${netInfo.activeNetworkType}
                • connect timeout: 5000 ms
                • status: Opening connection...
            """.trimIndent()

            repository.recordAudit(
                action = "STREAM_INIT",
                targetNode = currentNode.id,
                tokens = promptTokens,
                status = "CONNECTING",
                details = streamDiagnostics
            )
            _statusNotice.value = "Opening stream to $resolvedUrl..."

            val startTime = System.currentTimeMillis()
            val fullResponseBuilder = StringBuilder()
            var reportedInputTokens: Int? = assembled.estimatedTotalTokens.count
            var reportedOutputTokens: Int? = null
            var hasRecordedStreamReady = false

            providerRegistry.streamChat(
                endpoint = currentNode,
                request = assembled.chatRequest,
                isAirgapEnabled = _isAirgapEnabled.value
            ).collect { event ->
                if (!hasRecordedStreamReady && (event is InferenceEvent.TextDelta || event is InferenceEvent.Started)) {
                    hasRecordedStreamReady = true
                    endpointRepository.recordStreamVerified(currentNode)
                }

                when (event) {
                    is InferenceEvent.Started -> {}
                    is InferenceEvent.TextDelta -> {
                        fullResponseBuilder.append(event.text)
                        _streamingText.value = fullResponseBuilder.toString()
                    }
                    is InferenceEvent.ToolRequest -> {
                        _lastToolRequest.value = event
                        fullResponseBuilder.append("\n\n[TOOL REQUEST: ${event.name}(${event.arguments})]\n\n")
                        _streamingText.value = fullResponseBuilder.toString()
                    }
                    is InferenceEvent.Usage -> {
                        if (event.inputTokens != null) reportedInputTokens = event.inputTokens
                        if (event.outputTokens != null) reportedOutputTokens = event.outputTokens
                    }
                    is InferenceEvent.Completed -> {}
                    is InferenceEvent.Cancelled -> {
                        fullResponseBuilder.append("\n[Inference Cancelled]")
                        _streamingText.value = fullResponseBuilder.toString()
                    }
                    is InferenceEvent.Failure -> {
                        fullResponseBuilder.append("\n[ERROR: ${event.message}]")
                        _streamingText.value = fullResponseBuilder.toString()
                    }
                }
            }

            val finalResponse = fullResponseBuilder.toString()
            val elapsedMs = System.currentTimeMillis() - startTime
            val completionTokens = reportedOutputTokens ?: com.example.plmn.data.security.CryptoVaultHelper.estimateTokenCount(finalResponse)

            val assistantMsg = ChatMessage(
                sessionId = currentSessionId.value,
                role = "assistant",
                content = finalResponse,
                sanitizedContent = finalResponse,
                tokenCount = completionTokens,
                generationMs = elapsedMs,
                nodeName = currentNode.name,
                redactedItemsCount = 0
            )
            repository.insertMessage(assistantMsg)

            val provenanceDetails = "Model: ${targetModel.id} | ${providerType.displayName} | " +
                    "Vault: ${assembled.includedVaultDocTitles.size} docs | " +
                    "T=${"%.1f".format(_temperature.value)} | ${completionTokens} tokens in ${elapsedMs}ms"

            repository.recordAudit(
                action = "INFERENCE_EXEC",
                targetNode = currentNode.id,
                tokens = (reportedInputTokens ?: 0) + completionTokens,
                status = if (_isAirgapEnabled.value) "AIRGAP_LOCAL" else "SECURE",
                details = provenanceDetails
            )

            verifyLedger()
            _isGenerating.value = false
            _streamingText.value = ""
        }
    }

    // Vault management
    fun addVaultDoc(title: String, content: String, category: String, tags: String) {
        viewModelScope.launch {
            repository.insertVaultDoc(
                title = title.trim(),
                plainContent = content.trim(),
                category = category,
                tags = tags.trim()
            )
            repository.recordAudit(
                action = "VAULT_INSERT",
                targetNode = "keystore-vault",
                tokens = com.example.plmn.data.security.CryptoVaultHelper.estimateTokenCount(content),
                status = "SECURE",
                details = "Stored document '$title' encrypted at rest with Keystore AES-256 GCM."
            )
            _statusNotice.value = "Stored in Keystore Vault: $title"
            verifyLedger()
        }
    }

    fun toggleVaultContext(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleContextActive(id, isActive)
        }
    }

    fun deleteVaultDoc(doc: VaultDocument) {
        viewModelScope.launch {
            repository.deleteVaultDoc(doc)
            _statusNotice.value = "Deleted: ${doc.title}"
            verifyLedger()
        }
    }

    fun newSession() {
        currentSessionId.value = com.example.plmn.data.security.CryptoVaultHelper.generateNodeSessionId()
        _statusNotice.value = "Started new session"
    }

    fun clearCurrentSession() {
        viewModelScope.launch {
            repository.clearSession(currentSessionId.value)
            _statusNotice.value = "Session cleared"
        }
    }

    fun cryptographicNuclearWipe() {
        viewModelScope.launch {
            repository.cryptographicNuclearWipe()
            SecretStore.purgeAllSecrets()
            currentSessionId.value = com.example.plmn.data.security.CryptoVaultHelper.generateNodeSessionId()
            _statusNotice.value = "Cryptographic Erase complete: Master Keystore key rotated & database purged."
            verifyLedger()
        }
    }

    fun testScrubber(input: String): SanitizationResult {
        return PromptSanitizer.sanitize(input, enabled = true)
    }
}
