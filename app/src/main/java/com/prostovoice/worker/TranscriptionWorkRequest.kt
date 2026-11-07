package com.prostovoice.worker

import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.workDataOf

object TranscriptionWorkRequest {
    const val UNIQUE_NAME = "transcription"

    fun create(uri: Uri, forcedSpeakerCount: Int? = null): OneTimeWorkRequest = OneTimeWorkRequest.Builder(TranscriptionWorker::class.java)
        .setInputData(workDataOf(
            TranscriptionWorker.KEY_AUDIO_URI to uri.toString(),
            TranscriptionWorker.KEY_FORCED_SPEAKER_COUNT to (forcedSpeakerCount ?: 0),
        ))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build(),
        )
        .build()
}
