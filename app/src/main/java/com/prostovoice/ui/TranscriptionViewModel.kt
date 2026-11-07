package com.prostovoice.ui

import android.net.Uri
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prostovoice.domain.Transcript
import com.prostovoice.storage.TranscriptStore
import com.prostovoice.storage.HistoryItem
import com.prostovoice.storage.HistoryStore
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
    val history: List<HistoryItem> = emptyList(),
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val transcriptStore = TranscriptStore(application)
    private val historyStore = HistoryStore(application)
    private val workManager = WorkManager.getInstance(application)
    private val mutableState = MutableStateFlow(TranscriptionUiState())
    val state: StateFlow<TranscriptionUiState> = mutableState.asStateFlow()
    private var observationJob: Job? = null

    init {
        transcriptStore.load()?.let { transcript ->
            mutableState.value = TranscriptionUiState(
                transcript = transcript,
                elapsedMs = transcript.processingTimeMs,
                history = historyStore.load(),
            )
        } ?: run { mutableState.value = mutableState.value.copy(history = historyStore.load()) }
    }

    fun selectFile(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        mutableState.value = state.value.copy(selectedUri = uri, transcript = null, errorMessage = null)
    }

    fun openHistory(item: HistoryItem) {
        mutableState.value = state.value.copy(selectedUri = Uri.parse(item.uri), transcript = item.transcript, errorMessage = null)
    }

    fun transcribe() {
        transcribe(forcedSpeakerCount = null)
    }

    fun transcribeSingleSpeaker() {
        transcribe(forcedSpeakerCount = 1)
    }

    private fun transcribe(forcedSpeakerCount: Int?) {
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
            val request = TranscriptionWorkRequest.create(uri, forcedSpeakerCount)
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
                            history = historyStore.load(),
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
