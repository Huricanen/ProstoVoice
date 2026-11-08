package com.prostovoice.orchestration

import android.net.Uri
import com.prostovoice.domain.Transcript
import com.prostovoice.modelmanager.ModelInfo

interface TranscriptionBackend {
    suspend fun transcribe(uri: Uri, model: ModelInfo): Transcript
    fun isSupported(model: ModelInfo): Boolean
}
