package com.prostovoice.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prostovoice.orchestration.WhisperTranscriptionOrchestrator
import com.prostovoice.storage.TranscriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Background boundary for long-running transcription.
 * The full transcript is stored privately; WorkManager output contains only
 * small metadata because its Data payload is size-limited.
 */
class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uriString = inputData.getString(KEY_AUDIO_URI)
            ?: return@withContext Result.failure(errorData("Не указан URI аудиофайла"))
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext Result.failure(errorData("Некорректный URI аудиофайла"))

        setProgress(workDataOf(KEY_PROGRESS to 0, KEY_STAGE to "Подготовка"))
        runCatching {
            WhisperTranscriptionOrchestrator(applicationContext).transcribe(uri) { progress, stage ->
                runBlocking {
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to (progress * 100f).toInt().coerceIn(0, 100),
                            KEY_STAGE to stage,
                        ),
                    )
                }
            }
        }
            .fold(
                onSuccess = { transcript ->
                    TranscriptStore(applicationContext).save(transcript)
                    setProgress(workDataOf(KEY_PROGRESS to 100))
                    Result.success(
                        workDataOf(
                            KEY_SOURCE_NAME to transcript.sourceName,
                            KEY_SEGMENT_COUNT to transcript.segments.size,
                            KEY_RESULT_FILE to applicationContext.getFileStreamPath("last_transcript.json").absolutePath,
                            KEY_FALLBACK to false,
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
        const val KEY_STAGE = "stage"
        const val KEY_ERROR = "error"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_SEGMENT_COUNT = "segment_count"
        const val KEY_RESULT_FILE = "result_file"
        const val KEY_FALLBACK = "fallback"
    }
}
