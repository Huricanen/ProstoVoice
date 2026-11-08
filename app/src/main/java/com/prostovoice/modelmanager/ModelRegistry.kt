package com.prostovoice.modelmanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ModelRegistry(private val context: Context) {
    private val registryDirectory: File by lazy {
        context.filesDir.resolve("models/registry").apply { mkdirs() }
    }

    fun save(record: ModelRecord) {
        val existing = load().filterNot { it.id == record.id }
        val updated = (existing + record).distinctBy { it.id }
        val file = File(registryDirectory, "models.json")
        file.writeText(JSONArray(updated.map { it.toJsonObject() }).toString())
    }

    fun load(): List<ModelRecord> {
        val file = File(registryDirectory, "models.json")
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank() || text == "[]") return emptyList()
        val array = JSONArray(text)
        val records = mutableListOf<ModelRecord>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            records += ModelRecord(
                id = item.optString("id", "unknown"),
                name = item.optString("name", "unknown"),
                family = runCatching { ModelFamily.valueOf(item.optString("family", ModelFamily.WHISPER.name)) }
                    .getOrDefault(ModelFamily.WHISPER),
                version = item.optString("version", "unknown"),
                backend = item.optString("backend", "unknown"),
                path = item.optString("path", ""),
                sizeBytes = item.optLong("sizeBytes", 0L),
                source = runCatching { ModelSource.valueOf(item.optString("source", ModelSource.BUILT_IN.name)) }
                    .getOrDefault(ModelSource.BUILT_IN),
                status = runCatching { ModelStatus.valueOf(item.optString("status", ModelStatus.NOT_INSTALLED.name)) }
                    .getOrDefault(ModelStatus.NOT_INSTALLED),
                isBuiltIn = item.optBoolean("isBuiltIn", false),
                requiredFiles = emptyList(),
            )
        }
        return records
    }

    fun remove(recordId: String) {
        val remaining = load().filterNot { it.id == recordId }
        val file = File(registryDirectory, "models.json")
        file.writeText(JSONArray(remaining.map { it.toJsonObject() }).toString())
    }
}

private fun ModelRecord.toJsonObject(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("family", family.name)
    put("version", version)
    put("backend", backend)
    put("path", path)
    put("sizeBytes", sizeBytes)
    put("source", source.name)
    put("status", status.name)
    put("isBuiltIn", isBuiltIn)
    put("requiredFiles", JSONArray(requiredFiles))
}
