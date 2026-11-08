package com.prostovoice.modelmanager

enum class ModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    IMPORTING,
    VERIFYING,
    REGISTERING,
    INITIALIZING,
    READY,
    FAILED
}

enum class ModelFamily {
    WHISPER,
    PARAKEET,
    GIGAAM_V3,
    GIGAAM_V3_MULTILINGUAL
}

data class ModelInfo(
    val id: String,
    val name: String,
    val family: ModelFamily,
    val version: String,
    val backend: String,
    val path: String,
    val sizeBytes: Long,
    val source: String,
    val status: ModelState,
    val isBuiltIn: Boolean = false,
    val description: String = "",
    val tokensUrl: String = "",
    val tokensPath: String = "",
    val sha256: String = "",
    val dateInstalled: Long = 0L,
    val requiresInternet: Boolean = false,
    val lastError: String? = null
)
