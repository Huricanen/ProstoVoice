package com.prostovoice.modelmanager

import android.content.Context
import android.util.Log
import com.prostovoice.orchestration.WhisperModelStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

class ModelManager(private val context: Context) {
    private val registryFile by lazy { context.filesDir.resolve("models/registry/models.json") }
    private val builtInModelPath: String by lazy {
        WhisperModelStore(context, ModelCatalog.builtInWhisper.fileName).ensureModel().absolutePath
    }

    private val builtInRecord: ModelRecord by lazy {
        ModelRecord(
            id = ModelCatalog.builtInWhisper.id,
            name = ModelCatalog.builtInWhisper.displayName,
            family = ModelCatalog.builtInWhisper.family,
            version = "base",
            backend = ModelCatalog.builtInWhisper.backend,
            path = builtInModelPath,
            sizeBytes = File(builtInModelPath).length(),
            source = ModelSource.BUILT_IN,
            status = ModelStatus.READY,
            isBuiltIn = true,
            isActive = true,
            requiredFiles = ModelCatalog.builtInWhisper.requiredFiles,
        )
    }

    fun registrySnapshot(): List<ModelRecord> {
        if (!registryFile.exists()) {
            saveRegistry(listOf(builtInRecord))
            return listOf(builtInRecord)
        }
        return runCatching {
            val content = registryFile.readText()
            if (content.isBlank() || content == "[]") {
                listOf(builtInRecord)
            } else {
                parseRegistry(content)
            }
        }.getOrDefault(listOf(builtInRecord))
    }

    fun activeModel(): ModelRecord = registrySnapshot().firstOrNull { it.isActive && it.status == ModelStatus.READY }
        ?: registrySnapshot().firstOrNull { it.status == ModelStatus.READY }
        ?: builtInRecord

    fun defaultBuiltInRecord(): ModelRecord = builtInRecord

    fun ensureBuiltInModel(): ModelRecord {
        val current = registrySnapshot()
        val updated = current.filterNot { it.id == builtInRecord.id } + builtInRecord.copy(isActive = true)
        saveRegistry(updated)
        return builtInRecord.copy(isActive = true)
    }

    fun ensureActiveModel(): ModelRecord = ensureBuiltInModel()

    fun getModelPathForActiveTranscription(): String = ensureBuiltInModel().path

    fun installModel(entry: ModelCatalogEntry, installedPath: String): ModelRecord {
        val file = File(installedPath)
        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException("Файл модели не найден или пустой")
        }
        val existing = registrySnapshot().firstOrNull { it.id == entry.id }
        val next = (existing ?: ModelRecord(
            id = entry.id,
            name = entry.displayName,
            family = entry.family,
            version = "v1",
            backend = entry.backend,
            path = installedPath,
            sizeBytes = file.length(),
            source = ModelSource.CATALOG,
            status = ModelStatus.READY,
            isBuiltIn = false,
            isActive = false,
            requiredFiles = entry.requiredFiles,
        )).copy(
            name = entry.displayName,
            family = entry.family,
            backend = entry.backend,
            path = installedPath,
            sizeBytes = file.length(),
            source = if (entry.isBuiltIn) ModelSource.BUILT_IN else ModelSource.CATALOG,
            status = ModelStatus.READY,
            requiredFiles = entry.requiredFiles,
            isActive = false,
        )
        val all = registrySnapshot().filterNot { it.id == entry.id } + next
        saveRegistry(all)
        return next
    }

    fun activateModel(modelId: String): ModelRecord? {
        val snapshot = registrySnapshot()
        val target = snapshot.firstOrNull { it.id == modelId && it.status == ModelStatus.READY }
            ?: return null
        val updated = snapshot.map { record ->
            if (record.id == target.id) record.copy(isActive = true) else record.copy(isActive = false)
        }
        saveRegistry(updated)
        return updated.first { it.id == target.id }
    }

    fun deleteModel(modelId: String): Boolean {
        val snapshot = registrySnapshot()
        val target = snapshot.firstOrNull { it.id == modelId } ?: return false
        if (target.isBuiltIn) return false
        if (snapshot.count { it.status == ModelStatus.READY } <= 1) return false
        if (target.isActive) {
            val replacement = snapshot.firstOrNull { it.id != modelId && it.status == ModelStatus.READY } ?: return false
            activateModel(replacement.id) ?: return false
        }
        val file = File(target.path)
        val deleted = safeDeleteFile(file)
        val updated = snapshot.filterNot { it.id == modelId }
        saveRegistry(updated)
        return deleted || !file.exists()
    }

    fun validateModelPath(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.length() > 0L
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun safeDeleteFile(file: File): Boolean = runCatching {
        if (!file.exists()) return true
        val deleted = file.delete()
        if (!deleted) Log.w("ModelManager", "Failed to delete file ${file.absolutePath}")
        deleted
    }.getOrDefault(false)

    fun ensureModelDirectory(): File {
        val directory = context.filesDir.resolve("models")
        directory.mkdirs()
        val registry = directory.resolve("registry")
        registry.mkdirs()
        return directory
    }

    fun copyToInternalStorage(source: File, targetName: String): File {
        val destination = ensureModelDirectory().resolve(targetName)
        val temp = File(destination.parentFile, ".${destination.name}.part")
        temp.delete()
        FileOutputStream(temp).use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
        if (!temp.renameTo(destination)) {
            throw IOException("Не удалось сохранить файл модели: $targetName")
        }
        return destination
    }

    private fun saveRegistry(records: List<ModelRecord>) {
        val directory = ensureModelDirectory()
        val file = directory.resolve("registry/models.json")
        file.parentFile?.mkdirs()
        val array = JSONArray()
        records.forEach { record -> array.put(record.toJsonObject()) }
        file.writeText(array.toString())
    }

    private fun parseRegistry(content: String): List<ModelRecord> {
        val array = JSONArray(content)
        val records = mutableListOf<ModelRecord>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            records += ModelRecord(
                id = item.optString("id", "unknown"),
                name = item.optString("name", "unknown"),
                family = runCatching { ModelFamily.valueOf(item.optString("family", ModelFamily.WHISPER.name)) }
                    .getOrDefault(ModelFamily.WHISPER),
                version = item.optString("version", "unknown"),
                backend = item.optString("backend", "whisper.cpp"),
                path = item.optString("path", ""),
                sizeBytes = item.optLong("sizeBytes", 0L),
                source = runCatching { ModelSource.valueOf(item.optString("source", ModelSource.BUILT_IN.name)) }
                    .getOrDefault(ModelSource.BUILT_IN),
                status = runCatching { ModelStatus.valueOf(item.optString("status", ModelStatus.READY.name)) }
                    .getOrDefault(ModelStatus.READY),
                isBuiltIn = item.optBoolean("isBuiltIn", false),
                isActive = item.optBoolean("isActive", false),
                requiredFiles = emptyList(),
            )
        }
        return if (records.isEmpty()) listOf(builtInRecord) else records
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
        put("isActive", isActive)
        put("requiredFiles", JSONArray(requiredFiles))
    }
}
