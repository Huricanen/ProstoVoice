package com.prostovoice.modelmanager

import java.io.File
import java.security.MessageDigest
import java.util.Locale

class ModelVerifier {
    fun verifyRequiredFiles(directory: File, requiredFiles: List<String>): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false
        return requiredFiles.all { required ->
            val file = File(directory, required)
            file.exists() && file.length() > 0L
        }
    }

    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun verifyModelShape(modelDirectory: File, entry: ModelCatalogEntry): Boolean {
        val files = modelDirectory.listFiles() ?: emptyArray()
        return files.any { it.name == entry.fileName } && verifyRequiredFiles(modelDirectory, entry.requiredFiles)
    }

    fun validateForFamily(directory: File, family: ModelFamily): Boolean = when (family) {
        ModelFamily.WHISPER -> directory.exists() && (directory.listFiles()?.any { it.name.endsWith(".bin") || it.name.endsWith(".gguf") } == true)
        ModelFamily.PARAKEET -> directory.exists() && (directory.listFiles()?.any { it.name.endsWith(".onnx") } == true)
        ModelFamily.GIGAAM_V3 -> directory.exists() && (directory.listFiles()?.any { it.name.endsWith(".onnx") } == true)
        ModelFamily.GIGAAM_V3_MULTILINGUAL -> directory.exists() && (directory.listFiles()?.any { it.name.endsWith(".onnx") } == true)
    }

    fun requireUsableModel(directory: File, entry: ModelCatalogEntry): Boolean {
        if (!verifyRequiredFiles(directory, entry.requiredFiles)) return false
        if (!validateForFamily(directory, entry.family)) return false
        return true
    }
}
