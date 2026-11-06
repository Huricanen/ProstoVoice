package com.prostovoice.orchestration

import android.net.Uri
import com.prostovoice.domain.Transcript

/** Pipeline boundary for the future decoder, Whisper.cpp and sherpa-onnx adapters. */
interface TranscriptionOrchestrator {
    suspend fun transcribe(uri: Uri): Transcript
}

class TranscriptionUnavailableException(message: String) : IllegalStateException(message)

class PlaceholderTranscriptionOrchestrator : TranscriptionOrchestrator {
    override suspend fun transcribe(uri: Uri): Transcript {
        throw TranscriptionUnavailableException(
            "Локальный Whisper/diarization backend ещё не подключён; обработка файла невозможна",
        )
    }
}
