package com.prostovoice.ui

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.orchestration.WhisperTranscriptionOrchestrator
import com.prostovoice.storage.TranscriptStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.SystemClock

data class TranscriptionUiState(
    val selectedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val processingStage: String = "",
    val elapsedMs: Long = 0L,
    val transcript: Transcript? = null,
    val errorMessage: String? = null,
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val orchestrator = WhisperTranscriptionOrchestrator(application)
    private val transcriptStore = TranscriptStore(application)
    private val mutableState = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = mutableState.asStateFlow()

    init {
        transcriptStore.load()?.let { transcript ->
            mutableState.value = TranscriptionUiState(transcript = transcript)
        }
    }

    fun selectFile(uri: Uri) {
        mutableState.value = TranscriptionUiState(selectedUri = uri)
    }

    fun transcribe() {
        val uri = state.value.selectedUri ?: return
        if (state.value.isProcessing) return
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            mutableState.value = state.value.copy(
                isProcessing = true,
                progress = 0f,
                processingStage = "Подготовка",
                elapsedMs = 0L,
                errorMessage = null,
            )
            val timerJob = launch {
                while (isActive) {
                    mutableState.value = state.value.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                    delay(250L)
                }
            }
            runCatching {
                withContext(Dispatchers.Default) {
                    orchestrator.transcribe(uri) { progress, stage ->
                        mutableState.value = state.value.copy(
                            progress = progress.coerceIn(0f, 1f),
                            processingStage = stage,
                        )
                    }
                }
            }
                .onSuccess { transcript ->
                    transcriptStore.save(transcript)
                    mutableState.value = state.value.copy(
                        isProcessing = false,
                        progress = 1f,
                        processingStage = "Готово",
                        transcript = transcript,
                    )
                }
                .onFailure { error ->
                    mutableState.value = state.value.copy(
                        isProcessing = false,
                        processingStage = "",
                        errorMessage = error.message ?: "Не удалось обработать аудиофайл",
                    )
                }
            timerJob.cancel()
        }
    }

    fun clearError() {
        mutableState.value = state.value.copy(errorMessage = null)
    }
}
