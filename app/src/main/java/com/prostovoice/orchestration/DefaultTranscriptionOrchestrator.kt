package com.prostovoice.orchestration

import android.content.Context
import android.net.Uri
import com.prostovoice.domain.Transcript
import com.prostovoice.modelmanager.ModelManager
import com.prostovoice.modelmanager.ModelSelector

class DefaultTranscriptionOrchestrator(
    private val context: Context,
    private val modelManager: ModelManager
) : TranscriptionOrchestrator {
    
    private val selector = ModelSelector(modelManager, context)
    private val backends = listOf(
        WhisperTranscriptionBackend(context),
        SherpaTranscriptionBackend(context)
    )

    override suspend fun transcribe(uri: Uri): Transcript {
        val model = selector.pickPreferredModel()
        val backend = backends.find { it.isSupported(model) }
            ?: throw TranscriptionUnavailableException("Backend not found for model family: ${model.family}")
            
        return backend.transcribe(uri, model)
    }
}
