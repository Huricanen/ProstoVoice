package com.prostovoice.nativebridge

import com.prostovoice.diarization.DiarizationConfig

/**
 * Stable Kotlin boundary for the Whisper.cpp and sherpa-onnx adapters.
 *
 * The native library is optional during the Android skeleton phase. Callers can
 * inspect [isAvailable] and keep the Kotlin fallback when the JNI library is
 * not packaged yet.
 */
object NativePipeline {
    private var loaded = false

    init {
        loaded = runCatching {
            System.loadLibrary("prostovoice_native")
            true
        }.getOrDefault(false)
    }

    val isAvailable: Boolean
        get() = loaded

    /** Returns a machine-readable status JSON for diagnostics and the UI. */
    fun backendStatus(): String = if (loaded) nativeBackendStatus() else
        "{\"status\":\"native_unavailable\",\"offline\":true}"

    fun deviceProfile(): String = backendStatus()

    fun transcribePcm(
        pcm: FloatArray,
        sampleRate: Int,
        modelPath: String,
    ): String {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(modelPath.isNotBlank()) { "modelPath must not be blank" }
        require(pcm.isNotEmpty()) { "pcm must not be empty" }
        return checkAvailable { nativeTranscribePcm(pcm, sampleRate, modelPath) }
    }

    fun diarizePcm(
        pcm: FloatArray,
        sampleRate: Int,
        segmentationModelPath: String,
        embeddingModelPath: String,
        config: DiarizationConfig = DiarizationConfig(),
    ): String {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(segmentationModelPath.isNotBlank()) { "segmentationModelPath must not be blank" }
        require(embeddingModelPath.isNotBlank()) { "embeddingModelPath must not be blank" }
        require(pcm.isNotEmpty()) { "pcm must not be empty" }
        return checkAvailable {
            nativeDiarizePcm(
                pcm,
                sampleRate,
                segmentationModelPath,
                embeddingModelPath,
                config.numClusters,
                config.threshold,
                config.windowShiftRatio,
                config.minDurationOnSeconds,
                config.minDurationOffSeconds,
            )
        }
    }

    fun extractWindowEmbeddings(
        pcm: FloatArray,
        sampleRate: Int,
        embeddingModelPath: String,
        windowMs: Int = 1_500,
        stepMs: Int = 500,
    ): String {
        require(sampleRate > 0)
        require(embeddingModelPath.isNotBlank())
        require(pcm.isNotEmpty())
        require(windowMs > 0 && stepMs > 0)
        return checkAvailable {
            nativeExtractWindowEmbeddings(pcm, sampleRate, embeddingModelPath, windowMs, stepMs)
        }
    }

    private fun <T> checkAvailable(block: () -> T): T {
        check(loaded) { "Native pipeline is unavailable; use the Kotlin fallback" }
        return block()
    }

    private external fun nativeBackendStatus(): String

    private external fun nativeTranscribePcm(
        pcm: FloatArray,
        sampleRate: Int,
        modelPath: String,
    ): String

    private external fun nativeDiarizePcm(
        pcm: FloatArray,
        sampleRate: Int,
        segmentationModelPath: String,
        embeddingModelPath: String,
        numClusters: Int,
        threshold: Float,
        windowShiftRatio: Float,
        minDurationOnSeconds: Float,
        minDurationOffSeconds: Float,
    ): String

    private external fun nativeExtractWindowEmbeddings(
        pcm: FloatArray,
        sampleRate: Int,
        embeddingModelPath: String,
        windowMs: Int,
        stepMs: Int,
    ): String
}
