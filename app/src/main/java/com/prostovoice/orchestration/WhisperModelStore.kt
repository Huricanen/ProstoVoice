package com.prostovoice.orchestration

import android.content.Context
import com.prostovoice.modelmanager.ModelManager
import java.io.File
import java.io.InputStream

/** Extracts the bundled GGML model once into app-private storage. */
class WhisperModelStore(
    private val context: Context,
    private val modelFileName: String,
) {
    private val modelManager = ModelManager(context)

    fun ensureModel(): File {
        val directory = modelManager.ensureModelDirectory()
        val target = directory.resolve(modelFileName)
        if (target.isFile && target.length() > 0L) return target

        val assetCandidates = listOf(
            modelFileName,
            "models/whisper/$modelFileName",
        )

        var inputStream: InputStream? = null
        for (assetPath in assetCandidates) {
            inputStream = runCatching { context.assets.open(assetPath) }.getOrNull()
            if (inputStream != null) break
        }
        val assetStream = inputStream ?: throw IllegalStateException(
            "Не найден bundled Whisper-модельный файл: $modelFileName",
        )

        val temporary = directory.resolve(".$modelFileName.part")
        temporary.delete()
        assetStream.use { stream ->
            temporary.outputStream().buffered().use { output -> stream.copyTo(output) }
        }
        check(temporary.renameTo(target)) { "Не удалось сохранить Whisper-модель" }
        return target
    }
}
