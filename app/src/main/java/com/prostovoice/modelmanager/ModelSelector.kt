package com.prostovoice.modelmanager

class ModelSelector(private val modelManager: ModelManager) {
    fun pickPreferredModel(): ModelRecord {
        val readyModel = modelManager.registrySnapshot().firstOrNull { it.status == ModelStatus.READY }
        return readyModel ?: modelManager.ensureBuiltInModel()
    }

    fun pickFallbackModel(): ModelRecord = modelManager.ensureBuiltInModel()
}
