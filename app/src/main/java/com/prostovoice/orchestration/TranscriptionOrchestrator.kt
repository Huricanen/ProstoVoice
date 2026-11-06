package com.prostovoice.orchestration

import android.net.Uri
import com.prostovoice.domain.Transcript

/** Pipeline boundary for the future decoder, Whisper.cpp and sherpa-onnx adapters. */
interface TranscriptionOrchestrator {
    suspend fun transcribe(uri: Uri): Transcript
}

class PlaceholderTranscriptionOrchestrator : TranscriptionOrchestrator {
    override suspend fun transcribe(uri: Uri): Transcript = Transcript(
        sourceName = uri.lastPathSegment ?: "audio",
        durationMs = 0L,
        language = null,
        segments = emptyList(),
    )
}
