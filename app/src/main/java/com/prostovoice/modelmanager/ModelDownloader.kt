package com.prostovoice.modelmanager

import android.content.Context
import androidx.work.*
import java.util.UUID

class ModelDownloader(private val context: Context) {
    
    fun startDownload(model: ModelInfo): UUID {
        val data = workDataOf(
            "model_id" to model.id,
            "url" to model.source,
            "tokens_url" to model.tokensUrl
        )
        
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${model.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }
}
