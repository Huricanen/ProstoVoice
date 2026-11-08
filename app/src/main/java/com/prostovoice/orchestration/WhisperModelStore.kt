package com.prostovoice.orchestration

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

/** Extracts the bundled GGML model once into app-private storage. */
class WhisperModelStore(
    private val context: Context,
    private val modelFileName: String,
) {

    fun ensureModel(): File {
        val directory = File(context.filesDir, "models")
        if (!directory.exists()) directory.mkdirs()
        
        val target = directory.resolve(modelFileName)
        if (target.isFile && target.length() > 0L) return target

        Log.i("WhisperModelStore", "Extracting bundled model: $modelFileName")
        
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
            "Не найден bundled Whisper-модельный файл: $modelFileName в assets"
        )

        val temporary = directory.resolve(".$modelFileName.part")
        try {
            if (temporary.exists()) temporary.delete()
            
            assetStream.use { stream ->
                temporary.outputStream().buffered().use { output -> 
                    stream.copyTo(output) 
                }
            }
            
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (e: Exception) {
            temporary.delete()
            Log.e("WhisperModelStore", "Failed to extract model", e)
            throw e
        }

        return target
    }
}
