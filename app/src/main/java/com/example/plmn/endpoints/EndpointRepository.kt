package com.example.plmn.endpoints

import com.example.plmn.data.local.NodeDao
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.data.security.SecretStore
import com.example.plmn.inference.CapabilitiesResult
import com.example.plmn.inference.HealthResult
import com.example.plmn.inference.ModelDescriptor
import com.example.plmn.inference.ProviderRegistry
import kotlinx.coroutines.flow.Flow

/**
 * EndpointRepository: Authoritative persistence layer for NodeEndpoints.
 *
 * Persists concrete verification state: verificationLevel, lastVerifiedAt, lastHttpStatus,
 * lastFailureCode, and lastStreamVerifiedAt.
 */
class EndpointRepository(
    private val nodeDao: NodeDao,
    private val providerRegistry: ProviderRegistry,
    private val modelRegistry: ModelRegistry
) {
    val allNodes: Flow<List<NodeEndpoint>> = nodeDao.getAllNodes()
    val activeNode: Flow<NodeEndpoint?> = nodeDao.getActiveNode()

    suspend fun healthCheckEndpoint(node: NodeEndpoint): Pair<HealthResult, List<ModelDescriptor>> {
        val healthResult = providerRegistry.health(node)
        val capsResult = if (healthResult.isHealthy) {
            try {
                providerRegistry.capabilities(node)
            } catch (_: Exception) {
                CapabilitiesResult(com.example.plmn.inference.ProviderCapabilities(), emptyList())
            }
        } else {
            CapabilitiesResult(com.example.plmn.inference.ProviderCapabilities(), emptyList())
        }

        if (capsResult.models.isNotEmpty()) {
            modelRegistry.updateModelsForEndpoint(node.id, capsResult.models)
        }

        val v = healthResult.verification
        nodeDao.updateNode(
            node.copy(
                isReachable = healthResult.isHealthy,
                latencyMs = healthResult.latencyMs,
                verificationLevel = v.level.name,
                lastVerifiedAt = v.checkedAt,
                lastHttpStatus = v.protocolStatus,
                lastFailureCode = v.failureCode?.name,
                lastStreamVerifiedAt = v.streamVerifiedAt ?: node.lastStreamVerifiedAt
            )
        )

        return Pair(healthResult, capsResult.models)
    }

    suspend fun recordStreamVerified(node: NodeEndpoint) {
        val now = System.currentTimeMillis()
        nodeDao.updateNode(
            node.copy(
                verificationLevel = "STREAM_READY",
                lastStreamVerifiedAt = now,
                lastVerifiedAt = now
            )
        )
    }

    suspend fun saveEndpoint(node: NodeEndpoint, rawSecret: String? = null) {
        val finalCredRef = if (!rawSecret.isNullOrBlank()) {
            SecretStore.removeSecret(node.credentialRef)
            SecretStore.storeSecret(rawSecret)
        } else {
            node.credentialRef
        }

        val nodeToSave = node.copy(credentialRef = finalCredRef)

        providerRegistry.invalidateProvider(nodeToSave.id)
        nodeDao.insertNode(nodeToSave)
        healthCheckEndpoint(nodeToSave)
    }

    suspend fun deleteEndpoint(node: NodeEndpoint) {
        SecretStore.removeSecret(node.credentialRef)
        providerRegistry.invalidateProvider(node.id)
        modelRegistry.removeEndpoint(node.id)
        nodeDao.deleteNode(node)
    }

    suspend fun switchActiveEndpoint(nodeId: String) {
        nodeDao.switchActiveNode(nodeId)
    }
}
