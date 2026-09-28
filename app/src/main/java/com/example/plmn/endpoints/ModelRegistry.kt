package com.example.plmn.endpoints

import com.example.plmn.inference.ModelDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ModelRegistry: Universal multi-endpoint model catalog.
 *
 * Discovers and indexes models across all configured mesh endpoints (Ollama, LM Studio,
 * llama.cpp, PLMH Hub, On-Device, etc.).
 * Handles model deduplication and active model selection.
 */
class ModelRegistry {

    private val _models = MutableStateFlow<List<ModelDescriptor>>(emptyList())
    val models: StateFlow<List<ModelDescriptor>> = _models.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelDescriptor?>(null)
    val activeModel: StateFlow<ModelDescriptor?> = _activeModel.asStateFlow()

    fun updateModelsForEndpoint(endpointId: String, newModels: List<ModelDescriptor>) {
        val current = _models.value.filter { it.endpointId != endpointId }
        val updated = (current + newModels).distinctBy { "${it.endpointId}::${it.id}" }
        _models.value = updated

        // If no active model or active model was from this endpoint and no longer exists, update
        if (_activeModel.value == null || (_activeModel.value?.endpointId == endpointId && newModels.none { it.id == _activeModel.value?.id })) {
            _activeModel.value = updated.firstOrNull { it.endpointId == endpointId } ?: updated.firstOrNull()
        }
    }

    fun removeEndpoint(endpointId: String) {
        val updated = _models.value.filter { it.endpointId != endpointId }
        _models.value = updated
        if (_activeModel.value?.endpointId == endpointId) {
            _activeModel.value = updated.firstOrNull()
        }
    }

    fun setActiveModel(model: ModelDescriptor) {
        _activeModel.value = model
    }

    fun getModel(endpointId: String, modelId: String): ModelDescriptor? {
        return _models.value.firstOrNull { it.endpointId == endpointId && it.id == modelId }
            ?: _models.value.firstOrNull { it.id == modelId }
    }
}
