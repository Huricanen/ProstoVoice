package com.prostovoice.ui

import android.net.Uri
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.modelmanager.*
import com.prostovoice.orchestration.DefaultTranscriptionOrchestrator
import com.prostovoice.orchestration.TranscriptionOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

data class TranscriptionUiState(
    val selectedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val transcript: Transcript? = null,
    val errorMessage: String? = null,
    val modelStatus: String = "Инициализация...",
    val currentModel: ModelInfo? = null,
    val installedModels: List<ModelInfo> = emptyList(),
    val catalogModels: List<ModelInfo> = emptyList(),
    val downloadProgress: Int? = null,
    val downloadTask: String? = null,
    val downloadBytes: Long = 0,
    val downloadTotal: Long = 0,
    val downloadingModelId: String? = null
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager by lazy { ModelManager(getApplication()) }
    private val modelSelector by lazy { ModelSelector(modelManager, getApplication()) }
    private val orchestrator by lazy { DefaultTranscriptionOrchestrator(getApplication(), modelManager) }
    
    private val mutableState = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = mutableState.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    modelManager.ensureBuiltInModel() 
                    
                    val installed = modelManager.getInstalledModels()
                    val current = modelSelector.pickPreferredModel()
                    val catalog = ModelCatalog.availableModels.filter { avail ->
                        installed.none { inst -> inst.id == avail.id }
                    }
                    Triple(installed, current, catalog)
                }
                
                mutableState.update { prevState ->
                    val isDownloading = prevState.downloadingModelId != null
                    
                    // Ищем любую модель, которая упала с ошибкой
                    val failedModel = result.first.find { it.status == ModelState.FAILED }
                    
                    val newStatus = when {
                        isDownloading -> prevState.modelStatus
                        failedModel != null -> "Ошибка: ${failedModel.name}"
                        else -> "Распознавание готово"
                    }
                    
                    // Сохраняем сообщение об ошибке, если оно есть
                    val currentError = when {
                        isDownloading -> prevState.errorMessage
                        failedModel != null -> failedModel.lastError ?: "Сбой загрузки"
                        else -> null 
                    }
                    
                    prevState.copy(
                        modelStatus = newStatus,
                        currentModel = result.second,
                        installedModels = result.first,
                        catalogModels = result.third,
                        errorMessage = currentError
                    )
                }
            } catch (t: Throwable) {
                Log.e("TranscriptionVM", "Refresh failed", t)
            }
        }
    }

    fun selectModel(model: ModelInfo) {
        viewModelScope.launch {
            modelSelector.selectModel(model.id)
            refreshModels()
        }
    }

    fun installModel(model: ModelInfo) {
        viewModelScope.launch {
            mutableState.update { it.copy(
                modelStatus = "Запуск загрузки...",
                downloadTask = "Подготовка",
                downloadingModelId = model.id,
                errorMessage = null,
                downloadProgress = 0
            ) }
            
            try {
                val downloader = ModelDownloader(getApplication())
                val workId = withContext(Dispatchers.IO) {
                    downloader.startDownload(model)
                }
                
                androidx.work.WorkManager.getInstance(getApplication())
                    .getWorkInfoByIdFlow(workId)
                    .collect { workInfo ->
                        if (workInfo != null) {
                            val progress = workInfo.progress.getInt("progress", -1)
                            val bytes = workInfo.progress.getLong("bytes", 0L)
                            val total = workInfo.progress.getLong("total", 0L)
                            val task = workInfo.progress.getString("task")
                            
                            mutableState.update { prevState ->
                                prevState.copy(
                                    downloadProgress = if (progress >= 0) progress else prevState.downloadProgress,
                                    downloadTask = task ?: prevState.downloadTask,
                                    downloadBytes = if (bytes > 0) bytes else prevState.downloadBytes,
                                    downloadTotal = if (total > 0) total else prevState.downloadTotal,
                                    modelStatus = when {
                                        task != null -> "$task: ${if (progress >= 0) "$progress%" else ""}"
                                        else -> prevState.modelStatus
                                    }
                                )
                            }

                            if (workInfo.state.isFinished) {
                                if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                                    val error = workInfo.outputData.getString("error") ?: "Ошибка загрузки"
                                    mutableState.update { it.copy(
                                        downloadProgress = null,
                                        downloadTask = null,
                                        downloadingModelId = null,
                                        errorMessage = "Сбой: $error",
                                        modelStatus = "Ошибка"
                                    ) }
                                } else {
                                    delay(1000)
                                    mutableState.update { it.copy(
                                        downloadProgress = null,
                                        downloadTask = null,
                                        downloadingModelId = null,
                                        modelStatus = "Загружено"
                                    ) }
                                }
                                refreshModels()
                                return@collect // Stop collecting after finish
                            }
                        }
                    }
            } catch (e: Exception) {
                mutableState.update { it.copy(
                    errorMessage = "Не удалось запустить: ${e.message}",
                    downloadingModelId = null
                ) }
            }
        }
    }

    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelManager.deleteModel(model.id) }
            refreshModels()
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(modelStatus = "Импорт...", errorMessage = null) }
            try {
                withContext(Dispatchers.IO) {
                    val importer = ModelImporter(getApplication(), modelManager)
                    importer.importModel(uri, ModelFamily.WHISPER, "Импорт")
                }
                refreshModels()
            } catch (e: Exception) {
                mutableState.update { it.copy(errorMessage = "Ошибка импорта: ${e.message}") }
            }
        }
    }

    fun selectFile(uri: Uri) {
        mutableState.update { it.copy(selectedUri = uri, transcript = null, errorMessage = null) }
    }

    fun transcribe() {
        val uri = state.value.selectedUri ?: return
        if (state.value.isProcessing) return
        viewModelScope.launch {
            mutableState.value = state.value.copy(isProcessing = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.Default) { orchestrator.transcribe(uri) }
            }.onSuccess { transcript ->
                mutableState.value = state.value.copy(isProcessing = false, transcript = transcript)
            }.onFailure { error ->
                mutableState.value = state.value.copy(isProcessing = false, errorMessage = error.message)
            }
        }
    }

    fun clearError() {
        mutableState.value = state.value.copy(errorMessage = null)
    }
}
