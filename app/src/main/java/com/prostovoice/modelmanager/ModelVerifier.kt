package com.prostovoice.modelmanager

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ModelVerifier {
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return sha256.equals(expectedSha256, ignoreCase = true)
    }

    fun verifyStructure(model: ModelInfo): Boolean {
        val file = File(model.path)
        if (!file.exists()) return false
        
        return when (model.family) {
            ModelFamily.WHISPER -> file.name.endsWith(".bin")
            ModelFamily.PARAKEET, 
            ModelFamily.GIGAAM_V3, 
            ModelFamily.GIGAAM_V3_MULTILINGUAL -> file.name.endsWith(".onnx")
        }
    }
}
