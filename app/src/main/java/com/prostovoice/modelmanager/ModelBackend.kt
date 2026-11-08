package com.prostovoice.modelmanager

import java.io.File

interface ModelBackend {
    val family: ModelFamily
    val backendName: String
    fun supports(model: ModelCatalogEntry): Boolean
    fun isCompatible(deviceInfo: DeviceProfile = DeviceProfile()): Boolean
    fun initialize(modelDirectory: File): Boolean
    fun canTranscribe(modelDirectory: File): Boolean
    fun release(modelDirectory: File)
}

data class DeviceProfile(
    val totalRamBytes: Long = 0L,
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
)

class WhisperBackend : ModelBackend {
    override val family: ModelFamily = ModelFamily.WHISPER
    override val backendName: String = "whisper.cpp"

    override fun supports(model: ModelCatalogEntry): Boolean = model.family == ModelFamily.WHISPER

    override fun isCompatible(deviceInfo: DeviceProfile): Boolean = true

    override fun initialize(modelDirectory: File): Boolean = modelDirectory.exists() && modelDirectory.listFiles()?.isNotEmpty() == true

    override fun canTranscribe(modelDirectory: File): Boolean = initialize(modelDirectory)

    override fun release(modelDirectory: File) {
        // No-op; resources are managed by native lifecycle and file deletion rules.
    }
}

class ParakeetBackend : ModelBackend {
    override val family: ModelFamily = ModelFamily.PARAKEET
    override val backendName: String = "parakeet"

    override fun supports(model: ModelCatalogEntry): Boolean = model.family == ModelFamily.PARAKEET

    override fun isCompatible(deviceInfo: DeviceProfile): Boolean = deviceInfo.totalRamBytes <= 0L || deviceInfo.totalRamBytes >= 2L * 1024 * 1024 * 1024

    override fun initialize(modelDirectory: File): Boolean = modelDirectory.exists()

    override fun canTranscribe(modelDirectory: File): Boolean = initialize(modelDirectory)

    override fun release(modelDirectory: File) = Unit
}

class GigaAmV3Backend : ModelBackend {
    override val family: ModelFamily = ModelFamily.GIGAAM_V3
    override val backendName: String = "gigaam-v3"

    override fun supports(model: ModelCatalogEntry): Boolean = model.family == ModelFamily.GIGAAM_V3

    override fun isCompatible(deviceInfo: DeviceProfile): Boolean = deviceInfo.availableProcessors >= 4

    override fun initialize(modelDirectory: File): Boolean = modelDirectory.exists()

    override fun canTranscribe(modelDirectory: File): Boolean = initialize(modelDirectory)

    override fun release(modelDirectory: File) = Unit
}

class GigaAmV3MultilingualBackend : ModelBackend {
    override val family: ModelFamily = ModelFamily.GIGAAM_V3_MULTILINGUAL
    override val backendName: String = "gigaam-v3-multilingual"

    override fun supports(model: ModelCatalogEntry): Boolean = model.family == ModelFamily.GIGAAM_V3_MULTILINGUAL

    override fun isCompatible(deviceInfo: DeviceProfile): Boolean = deviceInfo.availableProcessors >= 4

    override fun initialize(modelDirectory: File): Boolean = modelDirectory.exists()

    override fun canTranscribe(modelDirectory: File): Boolean = initialize(modelDirectory)

    override fun release(modelDirectory: File) = Unit
}
