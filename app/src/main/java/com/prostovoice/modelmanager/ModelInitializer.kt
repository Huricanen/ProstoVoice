package com.prostovoice.modelmanager

import java.io.File

class ModelInitializer {
    fun initialize(entry: ModelCatalogEntry, modelDirectory: File): Boolean {
        if (!modelDirectory.exists() || !modelDirectory.isDirectory) return false
        val backend = backendFor(entry.family)
        return backend?.initialize(modelDirectory) == true && backend.canTranscribe(modelDirectory)
    }

    fun backendFor(family: ModelFamily): ModelBackend? = when (family) {
        ModelFamily.WHISPER -> WhisperBackend()
        ModelFamily.PARAKEET -> ParakeetBackend()
        ModelFamily.GIGAAM_V3 -> GigaAmV3Backend()
        ModelFamily.GIGAAM_V3_MULTILINGUAL -> GigaAmV3MultilingualBackend()
    }
}
