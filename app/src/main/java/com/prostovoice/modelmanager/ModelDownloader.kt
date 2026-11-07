package com.prostovoice.modelmanager

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(
    private val context: Context,
    private val tempDirectory: File = context.filesDir.resolve("models/temp"),
) {
    data class DownloadResult(
        val file: File,
        val sizeBytes: Long,
    )

    fun downloadToTemp(url: String, targetName: String): DownloadResult {
        require(url.startsWith("https://")) { "Поддерживаются только HTTPS-ссылки для скачивания моделей" }
        val tempDir = tempDirectory.apply { mkdirs() }
        val target = File(tempDir, targetName)
        target.delete()

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IOException("Не удалось скачать модель. HTTP ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        return DownloadResult(target, target.length())
    }

    fun importFromUri(uri: Uri, targetName: String): File {
        val destination = context.filesDir.resolve("models/temp/$targetName")
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Не удалось открыть выбранный файл модели")
        return destination
    }

    fun installVerifiedModel(entry: ModelCatalogEntry, downloadedFile: File): ModelRecord {
        val modelManager = ModelManager(context)
        val verifier = ModelVerifier()
        val verified = verifier.requireUsableModel(downloadedFile.parentFile ?: downloadedFile.absoluteFile.parentFile, entry)
        if (!verified) {
            throw IllegalStateException("Модель не прошла проверку целостности и формата")
        }
        val destination = modelManager.copyToInternalStorage(downloadedFile, downloadedFile.name)
        return modelManager.installModel(entry, destination.absolutePath)
    }
}
