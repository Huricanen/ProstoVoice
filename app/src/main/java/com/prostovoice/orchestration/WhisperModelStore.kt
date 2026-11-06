package com.prostovoice.orchestration

import android.content.Context
import java.io.File

/** Extracts the bundled GGML model once into app-private storage. */
class WhisperModelStore(
    private val context: Context,
    private val modelFileName: String,
) {
    fun ensureModel(): File {
        val directory = context.filesDir.resolve("models")
        val target = directory.resolve(modelFileName)
        if (target.isFile && target.length() > 0L) return target

        directory.mkdirs()
        val temporary = directory.resolve(".$modelFileName.part")
        temporary.delete()
        context.assets.open(modelFileName).use { input ->
            temporary.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        check(temporary.renameTo(target)) { "Не удалось сохранить Whisper-модель" }
        return target
    }
}
