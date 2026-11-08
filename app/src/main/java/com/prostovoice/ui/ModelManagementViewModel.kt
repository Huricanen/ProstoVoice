package com.prostovoice.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.prostovoice.modelmanager.ModelCatalog
import com.prostovoice.modelmanager.ModelCatalogEntry
import com.prostovoice.modelmanager.ModelDownloader
import com.prostovoice.modelmanager.ModelFamily
import com.prostovoice.modelmanager.ModelManager
import com.prostovoice.modelmanager.ModelRecord
import com.prostovoice.modelmanager.ModelStatus
import com.prostovoice.modelmanager.ModelVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ModelManagementUiState(
    val models: List<ModelRecord> = emptyList(),
    val lastMessage: String? = null,
)

class ModelManagementViewModel(private val context: Context) : ViewModel() {
    private val modelManager = ModelManager(context)
    private val verifier = ModelVerifier()
    private val downloader = ModelDownloader(context)
    private val _state = MutableStateFlow(ModelManagementUiState(modelManager.registrySnapshot()))
    val state: StateFlow<ModelManagementUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = ModelManagementUiState(modelManager.registrySnapshot(), _state.value.lastMessage)
    }

    fun setMessage(message: String) {
        _state.value = _state.value.copy(lastMessage = message)
    }

    fun activate(modelId: String) {
        val updated = modelManager.activateModel(modelId)
        if (updated != null) {
            setMessage("Активирована модель: ${updated.name}")
        }
        refresh()
    }

    fun delete(modelId: String) {
        val success = modelManager.deleteModel(modelId)
        setMessage(if (success) "Модель удалена" else "Удаление запрещено: оставьте последнюю рабочую модель")
        refresh()
    }

    fun install(entry: ModelCatalogEntry) {
        if (entry.isBuiltIn) {
            setMessage("Встроенная модель уже активна и защищена")
            refresh()
            return
        }
        if (entry.downloadUrl == null) {
            val file = File(context.filesDir, "models/${entry.fileName}")
            if (!file.exists()) {
                setMessage("Для этой модели нет доступной HTTPS-ссылки. Используйте импорт файла.")
                refresh()
                return
            }
            val record = modelManager.installModel(entry, file.absolutePath)
            setMessage("Модель установлена: ${record.name}")
            refresh()
            return
        }
        if (!entry.downloadUrl.startsWith("https://")) {
            setMessage("Только HTTPS-ссылки поддерживаются для загрузки моделей")
            refresh()
            return
        }

        try {
            val temp = downloader.downloadToTemp(entry.downloadUrl, entry.fileName)
            val directory = temp.file.parentFile ?: File(context.filesDir, "models")
            val verified = verifier.verifyRequiredFiles(directory, entry.requiredFiles)
            if (!verified) {
                throw IllegalStateException("Загруженный файл не проходит проверку моделей")
            }
            if (entry.sha256 != null && !verifier.verifyChecksum(temp.file, entry.sha256)) {
                throw IllegalStateException("Контрольная сумма модели не совпала")
            }
            val saved = modelManager.copyToInternalStorage(temp.file, entry.fileName)
            val record = modelManager.installModel(entry, saved.absolutePath)
            setMessage("Скачивание завершено: ${record.name}")
            refresh()
        } catch (error: Throwable) {
            setMessage(error.message ?: "Ошибка загрузки модели")
            refresh()
        }
    }

    fun importFromUri(uri: Uri) {
        try {
            val file = downloader.importFromUri(uri, "custom-model.bin")
            val entry = ModelCatalog.availableModels().firstOrNull { it.family == ModelFamily.WHISPER }
                ?: ModelCatalog.builtInWhisper
            val valid = verifier.verifyRequiredFiles(file.parentFile ?: file.absoluteFile.parentFile, entry.requiredFiles)
            if (!valid) {
                throw IllegalStateException("Импортированная модель не соответствует формату Whisper")
            }
            val saved = modelManager.copyToInternalStorage(file, entry.fileName)
            val record = modelManager.installModel(entry, saved.absolutePath)
            setMessage("Импорт выполнен: ${record.name}")
            refresh()
        } catch (error: Throwable) {
            setMessage(error.message ?: "Ошибка импорта модели")
            refresh()
        }
    }
}

class ModelManagementViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ModelManagementViewModel::class.java))
        return ModelManagementViewModel(context) as T
    }
}
