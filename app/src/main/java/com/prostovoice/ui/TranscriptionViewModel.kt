package com.prostovoice.ui

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.orchestration.WhisperTranscriptionOrchestrator
import com.prostovoice.orchestration.TranscriptionOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TranscriptionUiState(
    val selectedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val transcript: Transcript? = null,
    val errorMessage: String? = null,
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val orchestrator: TranscriptionOrchestrator = WhisperTranscriptionOrchestrator(application)
    private val mutableState = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = mutableState.asStateFlow()

    fun selectFile(uri: Uri) {
        mutableState.value = TranscriptionUiState(selectedUri = uri)
    }

    fun transcribe() {
        val uri = state.value.selectedUri ?: return
        if (state.value.isProcessing) return
        viewModelScope.launch {
            mutableState.value = state.value.copy(isProcessing = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.Default) { orchestrator.transcribe(uri) }
            }
                .onSuccess { transcript ->
                    mutableState.value = state.value.copy(
                        isProcessing = false,
                        transcript = transcript,
                    )
                }
                .onFailure { error ->
                    mutableState.value = state.value.copy(
                        isProcessing = false,
                        errorMessage = error.message ?: "Не удалось обработать аудиофайл",
                    )
                }
        }
    }

    fun clearError() {
        mutableState.value = state.value.copy(errorMessage = null)
    }
}
