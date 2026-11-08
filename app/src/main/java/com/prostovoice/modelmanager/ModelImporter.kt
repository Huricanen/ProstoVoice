package com.prostovoice.modelmanager

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class ModelImporter(private val context: Context, private val modelManager: ModelManager) {
    
    fun importModel(uri: Uri, family: ModelFamily, name: String): String {
        val modelId = UUID.randomUUID().toString()
        val extension = when (family) {
            ModelFamily.WHISPER -> "bin"
            else -> "onnx"
        }
        val targetFile = File(modelManager.ensureModelDirectory(), "$modelId.$extension")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        val info = ModelInfo(
            id = modelId,
            name = name,
            family = family,
            version = "imported",
            backend = if (family == ModelFamily.WHISPER) "whisper.cpp" else "sherpa-onnx",
            path = targetFile.absolutePath,
            sizeBytes = targetFile.length(),
            source = "local_import",
            status = ModelState.READY // Should go through VERIFYING/INITIALIZING in production
        )
        
        // In a real implementation, we would call ModelVerifier and ModelInitializer here
        
        val registry = ModelRegistry(context)
        registry.registerModel(info)
        
        return modelId
    }
}
