package com.prostovoice.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.orchestration.PlaceholderTranscriptionOrchestrator
import com.prostovoice.orchestration.TranscriptionOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TranscriptionUiState(
    val selectedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val transcript: Transcript? = null,
    val errorMessage: String? = null,
)

class TranscriptionViewModel(
    private val orchestrator: TranscriptionOrchestrator = PlaceholderTranscriptionOrchestrator(),
) : ViewModel() {
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
            runCatching { orchestrator.transcribe(uri) }
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
