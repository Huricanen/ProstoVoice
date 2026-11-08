package com.prostovoice.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.modelmanager.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsUiState(
    val installedModels: List<ModelInfo> = emptyList(),
    val availableModels: List<ModelInfo> = emptyList(),
    val isImporting: Boolean = false,
    val error: String? = null
)

class ModelsViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    private val modelImporter = ModelImporter(application, modelManager)
    private val modelDownloader = ModelDownloader(application)
    
    private val _state = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        val installed = modelManager.getInstalledModels()
        val available = ModelCatalog.availableModels.filter { avail ->
            installed.none { inst -> inst.id == avail.id }
        }
        _state.update { it.copy(installedModels = installed, availableModels = available) }
    }

    fun installFromCatalog(model: ModelInfo) {
        modelDownloader.startDownload(model)
        // In a real app, we'd observe the download progress
        refreshModels()
    }

    fun importModel(uri: Uri, family: ModelFamily, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            runCatching {
                modelImporter.importModel(uri, family, name)
            }.onSuccess {
                refreshModels()
                _state.update { it.copy(isImporting = false) }
            }.onFailure { e ->
                _state.update { it.copy(isImporting = false, error = e.message) }
            }
        }
    }

    fun deleteModel(id: String) {
        modelManager.deleteModel(id)
            .onSuccess { refreshModels() }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
