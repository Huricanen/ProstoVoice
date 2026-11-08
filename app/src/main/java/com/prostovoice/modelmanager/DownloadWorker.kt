package com.prostovoice.modelmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id") ?: return Result.failure()
        val urlString = inputData.getString("url") ?: return Result.failure()
        val tokensUrlString = inputData.getString("tokens_url") ?: ""
        
        setProgress(workDataOf("task" to "Инициализация...", "progress" to 0, "model_id" to modelId))

        val registry = ModelRegistry(applicationContext)
        val model = registry.getModel(modelId) ?: ModelCatalog.availableModels.find { it.id == modelId }
        
        if (model == null) return Result.failure(workDataOf("error" to "Модель не найдена"))
        
        registry.registerModel(model.copy(status = ModelState.DOWNLOADING, lastError = null))
        
        val modelsDir = File(applicationContext.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        try {
            // 1. Загрузка файла модели
            val extension = if (model.family == ModelFamily.WHISPER) "bin" else "onnx"
            val modelFile = downloadFile(urlString, modelsDir, "model_$modelId.$extension", "Загрузка модели", modelId)
            
            // ПРОВЕРКА НА LFS: Если файл слишком мал, проверяем не текст ли это
            if (modelFile.length() < 2048) {
                val buffer = ByteArray(1024)
                val read = FileInputStream(modelFile).use { it.read(buffer) }
                val content = if (read > 0) String(buffer, 0, read) else ""
                if (content.contains("version https://git-lfs")) {
                    modelFile.delete()
                    throw Exception("Ссылка HuggingFace вернула LFS-указатель. Требуется прямая ссылка на скачивание.")
                }
            }

            // 2. Загрузка токенов (если есть)
            var tokensPath = ""
            if (tokensUrlString.isNotBlank()) {
                setProgress(workDataOf("task" to "Загрузка словаря...", "progress" to 0, "model_id" to modelId))
                val tokensFile = downloadFile(tokensUrlString, modelsDir, "tokens_$modelId.txt", "Загрузка словаря", modelId)
                tokensPath = tokensFile.absolutePath
            }
            
            val updatedModel = model.copy(
                status = ModelState.READY,
                path = modelFile.absolutePath,
                sizeBytes = modelFile.length(),
                tokensPath = tokensPath,
                lastError = null
            )
            registry.registerModel(updatedModel)
            
            Log.i("DownloadWorker", "Модель $modelId успешно скачана")
            return Result.success(workDataOf("path" to modelFile.absolutePath))
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Сетевая ошибка"
            Log.e("DownloadWorker", "Ошибка загрузки $modelId: $errorMsg")
            registry.registerModel(model.copy(status = ModelState.FAILED, lastError = errorMsg))
            return Result.failure(workDataOf("error" to errorMsg))
        }
    }

    private suspend fun downloadFile(urlString: String, dir: File, fileName: String, taskName: String, modelId: String): File {
        var currentUrl = urlString
        var resultFile: File? = null
        
        for (i in 0..10) { 
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                connectTimeout = 30000
                readTimeout = 90000
                instanceFollowRedirects = false 
                // Имитируем современный браузер, иначе HuggingFace может блокировать или отдавать LFS-страницы
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "application/octet-stream, */*")
                setRequestProperty("Referer", "https://huggingface.co/")
            }
            
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location != null) {
                    currentUrl = if (location.startsWith("http")) location else {
                        URL(URL(currentUrl), location).toString()
                    }
                    continue
                }
            }
            
            if (status != HttpURLConnection.HTTP_OK) {
                throw Exception("Сервер вернул ошибку $status")
            }

            val totalBytes = connection.getHeaderField("Content-Length")?.toLong() ?: connection.contentLength.toLong()
            val targetFile = File(dir, fileName)
            val tempFile = File(dir, ".$fileName.tmp")
            
            var bytesDownloaded = 0L
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    var lastUpdate = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 1000) {
                            val progress = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else -1
                            setProgress(workDataOf(
                                "progress" to progress,
                                "bytes" to bytesDownloaded,
                                "total" to totalBytes,
                                "task" to taskName,
                                "model_id" to modelId
                            ))
                            lastUpdate = now
                        }
                    }
                }
            }
            connection.disconnect()
            
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                resultFile = targetFile
            }
            break
        }
        
        return resultFile ?: throw Exception("Не удалось скачать файл $fileName")
    }
}
