package com.prostovoice.worker

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prostovoice.orchestration.WhisperTranscriptionOrchestrator
import com.prostovoice.storage.TranscriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Background boundary for long-running transcription.
 * The full transcript is stored privately; WorkManager output contains only
 * small metadata because its Data payload is size-limited.
 */
class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, "Подготовка")

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uriString = inputData.getString(KEY_AUDIO_URI)
            ?: return@withContext Result.failure(errorData("Не указан URI аудиофайла"))
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext Result.failure(errorData("Некорректный URI аудиофайла"))
        val forcedSpeakerCount = inputData.getInt(KEY_FORCED_SPEAKER_COUNT, 0).takeIf { it > 0 }

        setForeground(createForegroundInfo(0, "Подготовка"))
        setProgress(workDataOf(KEY_PROGRESS to 0, KEY_STAGE to "Подготовка"))
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val progressState = AtomicReference(ProgressState(0, "Подготовка"))
        val progressMonitor = launch {
            while (isActive) {
                delay(500L)
                val current = progressState.get()
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to current.progress,
                        KEY_STAGE to current.stage,
                    ),
                )
                setForeground(createForegroundInfo(current.progress, current.stage))
            }
        }
        val transcription = runCatching {
            WhisperTranscriptionOrchestrator(applicationContext).transcribe(uri, forcedSpeakerCount) { progress, stage ->
                progressState.set(
                    ProgressState((progress * 100f).toInt().coerceIn(0, 100), stage),
                )
            }
        }
        progressMonitor.cancelAndJoin()
        return@withContext transcription.fold(
                onSuccess = { transcript ->
                    val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
                    val completed = transcript.copy(processingTimeMs = elapsedMs)
                    TranscriptStore(applicationContext).save(completed)
                    setProgress(workDataOf(KEY_PROGRESS to 100))
                    showCompletionNotification(
                        title = "Расшифровка завершена",
                        message = "Обработка заняла ${formatElapsed(elapsedMs)}",
                        icon = android.R.drawable.stat_sys_download_done,
                    )
                    Result.success(
                        workDataOf(
                            KEY_SOURCE_NAME to completed.sourceName,
                            KEY_SEGMENT_COUNT to completed.segments.size,
                            KEY_RESULT_FILE to applicationContext.getFileStreamPath("last_transcript.json").absolutePath,
                            KEY_ELAPSED_MS to elapsedMs,
                            KEY_FALLBACK to false,
                        ),
                    )
                },
                onFailure = { error ->
                    val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
                    showCompletionNotification(
                        title = "Ошибка расшифровки",
                        message = "${error.message ?: "Не удалось обработать аудио"} · ${formatElapsed(elapsedMs)}",
                        icon = android.R.drawable.stat_notify_error,
                    )
                    Result.failure(errorData(error.message ?: "Ошибка обработки аудио"))
                },
            )
    }

    private data class ProgressState(val progress: Int, val stage: String)

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    private fun showCompletionNotification(title: String, message: String, icon: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    "Результаты обработки",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        manager.notify(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, COMPLETION_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun formatElapsed(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
        return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun createForegroundInfo(progress: Int, stage: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Обработка аудио", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ProstoVoice")
            .setContentText("$stage — $progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_FORCED_SPEAKER_COUNT = "forced_speaker_count"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_ERROR = "error"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_SEGMENT_COUNT = "segment_count"
        const val KEY_RESULT_FILE = "result_file"
        const val KEY_ELAPSED_MS = "elapsed_ms"
        const val KEY_FALLBACK = "fallback"
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETION_CHANNEL_ID = "transcription_status"
        private const val COMPLETION_NOTIFICATION_ID = 1002
    }
}
