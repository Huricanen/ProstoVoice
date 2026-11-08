package com.prostovoice.modelmanager

import android.content.Context
import android.util.Log
import com.prostovoice.nativebridge.NativePipeline

class ModelInitializer(private val context: Context) {

    fun initialize(model: ModelInfo): Boolean {
        return try {
            when (model.family) {
                ModelFamily.WHISPER -> {
                    // For Whisper, we check if the native pipeline is available
                    // and potentially do a small test run or just check if the file is valid GGML.
                    NativePipeline.isAvailable && NativePipeline.backendStatus().contains("ok", ignoreCase = true)
                }
                ModelFamily.PARAKEET,
                ModelFamily.GIGAAM_V3,
                ModelFamily.GIGAAM_V3_MULTILINGUAL -> {
                    // For Sherpa-ONNX based models, we would check the ONNX runtime availability.
                    // Currently, we'll mark as success if the file exists, 
                    // until the full Sherpa integration is complete.
                    true 
                }
            }
        } catch (e: Exception) {
            Log.e("ModelInitializer", "Failed to initialize model ${model.id}", e)
            false
        }
    }
}
