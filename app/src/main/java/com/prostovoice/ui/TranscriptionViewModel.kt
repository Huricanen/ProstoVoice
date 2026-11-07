package com.prostovoice.ui

import android.net.Uri
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.storage.TranscriptStore
import com.prostovoice.worker.TranscriptionWorkRequest
import com.prostovoice.worker.TranscriptionWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

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
    private val transcriptStore = TranscriptStore(application)
    private val workManager = WorkManager.getInstance(application)
    private val mutableState = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = mutableState.asStateFlow()
    private var observationJob: Job? = null

    init {
        transcriptStore.load()?.let { transcript ->
            mutableState.value = TranscriptionUiState(
                transcript = transcript,
                elapsedMs = transcript.processingTimeMs,
            )
        }
    }

    fun selectFile(uri: Uri) {
        mutableState.value = TranscriptionUiState(selectedUri = uri)
    }

    fun transcribe() {
        val uri = state.value.selectedUri ?: return
        if (state.value.isProcessing) return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
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
            val request = TranscriptionWorkRequest.create(uri)
            workManager.enqueueUniqueWork(
                TranscriptionWorkRequest.UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                val progress = info.progress.getInt(TranscriptionWorker.KEY_PROGRESS, 0)
                    .coerceIn(0, 100) / 100f
                val stage = info.progress.getString(TranscriptionWorker.KEY_STAGE)
                    ?: state.value.processingStage
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                        mutableState.value = state.value.copy(
                            progress = progress,
                            processingStage = stage,
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val transcript = transcriptStore.load()
                        mutableState.value = state.value.copy(
                            isProcessing = false,
                            progress = 1f,
                            processingStage = "Готово",
                            elapsedMs = info.outputData.getLong(TranscriptionWorker.KEY_ELAPSED_MS, state.value.elapsedMs),
                            transcript = transcript,
                        )
                        timerJob.cancel()
                        return@collect
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        mutableState.value = state.value.copy(
                            isProcessing = false,
                            processingStage = "",
                            errorMessage = info.outputData.getString(TranscriptionWorker.KEY_ERROR)
                                ?: "Не удалось обработать аудиофайл",
                        )
                        timerJob.cancel()
                        return@collect
                    }
                }
            }
        }
    }

    fun clearError() {
        mutableState.value = state.value.copy(errorMessage = null)
    }
}
