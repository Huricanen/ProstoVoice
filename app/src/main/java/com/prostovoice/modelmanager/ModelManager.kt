package com.prostovoice.modelmanager

import android.content.Context
import com.prostovoice.orchestration.WhisperModelStore
import java.io.File

class ModelManager(private val context: Context) {
    private val registry = ModelRegistry(context)
    private val builtInWhisperFileName = "ggml-base.bin"
    private val whisperModelStore = WhisperModelStore(context, builtInWhisperFileName)

    fun ensureModelDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun ensureBuiltInModel(): ModelInfo {
        val file = whisperModelStore.ensureModel()
        val info = ModelInfo(
            id = "builtin-whisper",
            name = "Whisper Base (Built-in)",
            family = ModelFamily.WHISPER,
            version = "1.0",
            backend = "whisper.cpp",
            path = file.absolutePath,
            sizeBytes = file.length(),
            source = "assets",
            status = ModelState.READY,
            isBuiltIn = true
        )
        registry.registerModel(info)
        return info
    }

    fun validateModelPath(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isFile && file.length() > 0
    }

    fun getInstalledModels(): List<ModelInfo> {
        return registry.getModels()
    }
    
    fun getModelById(id: String): ModelInfo? {
        return registry.getModel(id)
    }

    fun deleteModel(id: String): Result<Unit> {
        val model = getModelById(id) ?: return Result.failure(Exception("Модель не найдена"))
        
        if (model.isBuiltIn) {
            return Result.failure(Exception("Нельзя удалить встроенную модель"))
        }
        
        val readyModels = getInstalledModels().filter { it.status == ModelState.READY }
        if (readyModels.size <= 1 && model.status == ModelState.READY) {
            return Result.failure(Exception("Нельзя удалить последнюю рабочую модель"))
        }

        // In a real app, check if model is currently in use
        
        val file = File(model.path)
        if (file.exists()) {
            file.delete()
        }
        
        registry.removeModel(id)
        return Result.success(Unit)
    }
}
