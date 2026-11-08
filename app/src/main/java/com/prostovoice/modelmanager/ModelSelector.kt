package com.prostovoice.modelmanager

import android.content.Context
import android.content.SharedPreferences

class ModelSelector(private val modelManager: ModelManager, context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("model_selector", Context.MODE_PRIVATE)

    fun pickPreferredModel(): ModelInfo {
        val preferredId = prefs.getString("preferred_model_id", null)
        val installedModels = modelManager.getInstalledModels()

        if (preferredId != null) {
            val model = installedModels.find { it.id == preferredId && it.status == ModelState.READY }
            if (model != null) return model
        }

        // Fallback to built-in or any ready model
        return installedModels.find { it.isBuiltIn && it.status == ModelState.READY }
            ?: installedModels.find { it.status == ModelState.READY }
            ?: modelManager.ensureBuiltInModel()
    }

    fun selectModel(id: String) {
        val model = modelManager.getModelById(id)
        if (model != null && model.status == ModelState.READY) {
            prefs.edit().putString("preferred_model_id", id).apply()
        }
    }
}
