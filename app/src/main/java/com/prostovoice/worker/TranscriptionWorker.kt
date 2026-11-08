package com.prostovoice.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prostovoice.modelmanager.ModelManager
import com.prostovoice.orchestration.DefaultTranscriptionOrchestrator
import com.prostovoice.orchestration.TranscriptionOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background boundary for long-running transcription.
 */
class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val modelManager = ModelManager(applicationContext)
    private val orchestrator: TranscriptionOrchestrator = DefaultTranscriptionOrchestrator(applicationContext, modelManager)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uriString = inputData.getString(KEY_AUDIO_URI)
            ?: return@withContext Result.failure(errorData("Не указан URI аудиофайла"))
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext Result.failure(errorData("Некорректный URI аудиофайла"))

        setProgress(workDataOf(KEY_PROGRESS to 0))
        runCatching { orchestrator.transcribe(uri) }
            .fold(
                onSuccess = { transcript ->
                    setProgress(workDataOf(KEY_PROGRESS to 100))
                    Result.success(
                        workDataOf(
                            KEY_SOURCE_NAME to transcript.sourceName,
                            KEY_SEGMENT_COUNT to transcript.segments.size,
                            KEY_FALLBACK to true,
                        ),
                    )
                },
                onFailure = { error ->
                    Result.failure(errorData(error.message ?: "Ошибка обработки аудио"))
                },
            )
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    companion object {
        const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_SEGMENT_COUNT = "segment_count"
        const val KEY_FALLBACK = "fallback"
    }
}
