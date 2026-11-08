package com.prostovoice.modelmanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ModelRegistry(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("model_registry", Context.MODE_PRIVATE)

    fun registerModel(model: ModelInfo) {
        val models = getModels().toMutableList()
        models.removeAll { it.id == model.id }
        models.add(model)
        saveModels(models)
    }

    fun getModels(): List<ModelInfo> {
        val json = prefs.getString("models_json", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<ModelInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                deserialize(obj)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) {
            Log.e("ModelRegistry", "Error loading models", e)
            emptyList()
        }
    }

    fun getModel(id: String): ModelInfo? {
        return getModels().find { it.id == id }
    }

    fun removeModel(id: String) {
        val models = getModels().toMutableList()
        models.removeAll { it.id == id }
        saveModels(models)
    }

    private fun saveModels(models: List<ModelInfo>) {
        try {
            val array = JSONArray()
            models.forEach { array.put(serialize(it)) }
            // Используем commit() для мгновенной синхронизации между процессами/потоками
            prefs.edit().putString("models_json", array.toString()).commit()
        } catch (e: Exception) {
            Log.e("ModelRegistry", "Error saving models", e)
        }
    }

    private fun serialize(model: ModelInfo): JSONObject {
        return JSONObject().apply {
            put("id", model.id)
            put("name", model.name)
            put("family", model.family.name)
            put("version", model.version)
            put("backend", model.backend)
            put("path", model.path)
            put("sizeBytes", model.sizeBytes)
            put("source", model.source)
            put("status", model.status.name)
            put("isBuiltIn", model.isBuiltIn)
            put("description", model.description)
            put("tokensUrl", model.tokensUrl)
            put("tokensPath", model.tokensPath)
            put("sha256", model.sha256)
            put("dateInstalled", model.dateInstalled)
            put("requiresInternet", model.requiresInternet)
            put("lastError", model.lastError ?: "")
        }
    }

    private fun deserialize(obj: JSONObject): ModelInfo? {
        return try {
            ModelInfo(
                id = obj.getString("id"),
                name = obj.getString("name"),
                family = ModelFamily.valueOf(obj.getString("family")),
                version = obj.getString("version"),
                backend = obj.getString("backend"),
                path = obj.getString("path"),
                sizeBytes = obj.getLong("sizeBytes"),
                source = obj.getString("source"),
                status = ModelState.valueOf(obj.getString("status")),
                isBuiltIn = obj.getBoolean("isBuiltIn"),
                description = obj.optString("description", ""),
                tokensUrl = obj.optString("tokensUrl", ""),
                tokensPath = obj.optString("tokensPath", ""),
                sha256 = obj.optString("sha256", ""),
                dateInstalled = obj.optLong("dateInstalled", 0L),
                requiresInternet = obj.optBoolean("requiresInternet", false),
                lastError = obj.optString("lastError", "").let { if (it.isEmpty()) null else it }
            )
        } catch (e: Exception) {
            Log.e("ModelRegistry", "Deserialization failed", e)
            null
        }
    }
}
