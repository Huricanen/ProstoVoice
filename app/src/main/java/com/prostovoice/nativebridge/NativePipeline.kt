package com.prostovoice.nativebridge

import android.util.Log

/**
 * Safe Kotlin boundary for Native Whisper and Sherpa-ONNX.
 */
object NativePipeline {
    private var loaded = false
    private const val TAG = "NativePipeline"

    init {
        try {
            System.loadLibrary("prostovoice_native")
            loaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load native library: ${t.message}")
            loaded = false
        }
    }

    val isAvailable: Boolean get() = loaded

    fun backendStatus(): String = if (loaded) {
        try {
            nativeBackendStatus()
        } catch (t: Throwable) {
            "{\"status\":\"native_error\",\"message\":\"${t.message}\"}"
        }
    } else {
        "{\"status\":\"native_unavailable\",\"offline\":true}"
    }

    fun transcribePcm(pcm: FloatArray, sampleRate: Int, modelPath: String): String {
        if (!loaded) return "{\"status\":\"error\",\"message\":\"Native library not available\"}"
        return try {
            nativeTranscribePcm(pcm, sampleRate, modelPath)
        } catch (t: Throwable) {
            "{\"status\":\"error\",\"message\":\"Inference crashed: ${t.message}\"}"
        }
    }

    fun diarizePcm(pcm: FloatArray, sampleRate: Int, segPath: String, embPath: String): String {
        if (!loaded) return "{\"status\":\"error\",\"message\":\"Native library not available\"}"
        return try {
            nativeDiarizePcm(pcm, sampleRate, segPath, embPath)
        } catch (t: Throwable) {
            "{\"status\":\"error\",\"message\":\"Diarization crashed: ${t.message}\"}"
        }
    }

    @JvmStatic
    private external fun nativeBackendStatus(): String
    
    @JvmStatic
    private external fun nativeTranscribePcm(pcm: FloatArray, rate: Int, path: String): String
    
    @JvmStatic
    private external fun nativeDiarizePcm(pcm: FloatArray, rate: Int, segPath: String, embPath: String): String
}
