package com.prostovoice.modelmanager

enum class ModelFamily {
    WHISPER,
    PARAKEET,
    GIGAAM_V3,
    GIGAAM_V3_MULTILINGUAL,
}

enum class ModelSource {
    BUILT_IN,
    CATALOG,
    IMPORTED,
}

enum class ModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    IMPORTING,
    VERIFYING,
    REGISTERING,
    INITIALIZING,
    READY,
    FAILED,
}

data class ModelCatalogEntry(
    val id: String,
    val family: ModelFamily,
    val displayName: String,
    val fileName: String,
    val description: String,
    val backend: String,
    val sizeBytes: Long = 0L,
    val requiredFiles: List<String> = listOf(fileName),
    val isBuiltIn: Boolean = false,
    val downloadUrl: String? = null,
    val sha256: String? = null,
)

data class ModelRecord(
    val id: String,
    val name: String,
    val family: ModelFamily,
    val version: String,
    val backend: String,
    val path: String,
    val sizeBytes: Long,
    val source: ModelSource,
    val status: ModelStatus,
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val requiredFiles: List<String> = emptyList(),
    val installedAtEpochMs: Long = System.currentTimeMillis(),
)

object ModelCatalog {
    val builtInWhisper = ModelCatalogEntry(
        id = "whisper-base-built-in",
        family = ModelFamily.WHISPER,
        displayName = "Whisper Base",
        fileName = "ggml-base.bin",
        description = "Локальная резервная модель Whisper, доступная без интернета.",
        backend = "whisper.cpp",
        sizeBytes = 0L,
        requiredFiles = listOf("ggml-base.bin"),
        isBuiltIn = true,
        downloadUrl = null,
        sha256 = null,
    )

    fun defaultWhisperEntry(fileName: String = builtInWhisper.fileName): ModelCatalogEntry =
        builtInWhisper.copy(fileName = fileName)

    fun availableModels(): List<ModelCatalogEntry> = listOf(
        builtInWhisper,
        ModelCatalogEntry(
            id = "whisper-tiny-catalog",
            family = ModelFamily.WHISPER,
            displayName = "Whisper Tiny",
            fileName = "ggml-tiny.bin",
            description = "Компактная Whisper-модель для быстрого офлайн-распознавания.",
            backend = "whisper.cpp",
            sizeBytes = 0L,
            requiredFiles = listOf("ggml-tiny.bin"),
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            sha256 = null,
        ),
        ModelCatalogEntry(
            id = "parakeet-base",
            family = ModelFamily.PARAKEET,
            displayName = "Parakeet Base",
            fileName = "parakeet-base.onnx",
            description = "Parakeet в формате ONNX для будущего локального ASR backend.",
            backend = "parakeet",
            sizeBytes = 0L,
            requiredFiles = listOf("parakeet-base.onnx"),
            downloadUrl = null,
            sha256 = null,
        ),
        ModelCatalogEntry(
            id = "gigaam-v3",
            family = ModelFamily.GIGAAM_V3,
            displayName = "GigaAM v3",
            fileName = "gigaam-v3.onnx",
            description = "Отдельный вариант модели GigaAM v3 для локального выбора.",
            backend = "gigaam-v3",
            sizeBytes = 0L,
            requiredFiles = listOf("gigaam-v3.onnx"),
            downloadUrl = null,
            sha256 = null,
        ),
        ModelCatalogEntry(
            id = "gigaam-v3-multilingual",
            family = ModelFamily.GIGAAM_V3_MULTILINGUAL,
            displayName = "GigaAM v3 Multilingual",
            fileName = "gigaam-v3-multilingual.onnx",
            description = "Многоязычная версия GigaAM v3 как отдельный вариант модели.",
            backend = "gigaam-v3-multilingual",
            sizeBytes = 0L,
            requiredFiles = listOf("gigaam-v3-multilingual.onnx"),
            downloadUrl = null,
            sha256 = null,
        ),
    )
}
