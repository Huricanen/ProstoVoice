package com.prostovoice.worker

import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.workDataOf

object TranscriptionWorkRequest {
    const val UNIQUE_NAME = "transcription"

    fun create(uri: Uri): OneTimeWorkRequest = OneTimeWorkRequest.Builder(TranscriptionWorker::class.java)
        .setInputData(workDataOf(TranscriptionWorker.KEY_AUDIO_URI to uri.toString()))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build(),
        )
        .build()
}
