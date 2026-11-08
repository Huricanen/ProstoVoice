package com.prostovoice.modelmanager

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

class ModelImporter(private val context: Context) {
    fun importFromUri(uri: Uri, targetFileName: String): File {
        val directory = context.filesDir.resolve("models")
        directory.mkdirs()
        val destination = directory.resolve(targetFileName)
        val temp = File(directory, ".${targetFileName}.import")
        temp.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Не удалось открыть выбранный файл модели")
        if (!temp.renameTo(destination)) {
            throw IOException("Не удалось сохранить импортированную модель")
        }
        return destination
    }
}
