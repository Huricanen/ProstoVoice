package com.prostovoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prostovoice.domain.Transcript
import com.prostovoice.export.toJson
import com.prostovoice.export.toTxt
import com.prostovoice.modelmanager.ModelInfo
import com.prostovoice.ui.TranscriptionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProstoVoiceApp()
            }
        }
    }
}

@Composable
private fun ProstoVoiceApp() {
    val viewModel: TranscriptionViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::selectFile)
    }
    
    val modelImporter = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
        if (uri != null && pendingExport != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
        }
        pendingExport = null
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExport != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
        }
        pendingExport = null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ProstoVoice", style = MaterialTheme.typography.headlineMedium)
                    Text("Офлайн расшифровка", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showModelPicker = true }) {
                    Text("Модели")
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = state.modelStatus, style = MaterialTheme.typography.titleSmall)
                    
                    state.downloadProgress?.let { progress ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val sizeInfo = if (state.downloadTotal > 0) {
                            "${state.downloadBytes / 1024 / 1024} / ${state.downloadTotal / 1024 / 1024} МБ"
                        } else {
                            "${state.downloadBytes / 1024 / 1024} МБ"
                        }
                        Text(
                            text = sizeInfo,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    state.currentModel?.let { model ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Активна: ${model.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
            ) {
                Text("Выбрать аудиофайл")
            }

            state.selectedUri?.let { uri ->
                Text("Файл: ${uri.lastPathSegment ?: "audio"}", style = MaterialTheme.typography.bodyMedium)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isProcessing && state.currentModel != null,
                    onClick = viewModel::transcribe,
                ) {
                    Text("Запустить распознавание")
                }
            }

            if (state.isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Обработка...")
                }
            }

            state.errorMessage?.let { 
                Text("Ошибка: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) 
            }

            state.transcript?.let { transcript ->
                TranscriptResultView(
                    transcript = transcript,
                    onTxt = {
                        pendingExport = transcript.toTxt()
                        exportLauncher.launch("${transcript.sourceName}.txt")
                    },
                    onJson = {
                        pendingExport = transcript.toJson()
                        jsonExportLauncher.launch("${transcript.sourceName}.json")
                    },
                )
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentModel = state.currentModel,
            installedModels = state.installedModels,
            catalogModels = state.catalogModels,
            onDismiss = { showModelPicker = false },
            onSelect = { model ->
                viewModel.selectModel(model)
                showModelPicker = false
            },
            onInstall = { viewModel.installModel(it) },
            onDelete = { viewModel.deleteModel(it) },
            onImport = { modelImporter.launch(arrayOf("*/*")) }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    currentModel: ModelInfo?,
    installedModels: List<ModelInfo>,
    catalogModels: List<ModelInfo>,
    onDismiss: () -> Unit,
    onSelect: (ModelInfo) -> Unit,
    onInstall: (ModelInfo) -> Unit,
    onDelete: (ModelInfo) -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Управление моделями")
                TextButton(onClick = onImport) { Text("Импорт") }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item { Text("Установленные", style = MaterialTheme.typography.labelMedium) }
                items(installedModels) { model ->
                    val isCurrent = model.id == currentModel?.id
                    ListItem(
                        modifier = Modifier.clickable { if (model.status == com.prostovoice.modelmanager.ModelState.READY) onSelect(model) },
                        headlineContent = { 
                            Text(model.name, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) 
                        },
                        supportingContent = { 
                            Column {
                                Text("Тип: ${model.family.name}")
                                if (model.status != com.prostovoice.modelmanager.ModelState.READY) {
                                    val statusText = when(model.status) {
                                        com.prostovoice.modelmanager.ModelState.DOWNLOADING -> "Загрузка..."
                                        com.prostovoice.modelmanager.ModelState.FAILED -> "Ошибка: ${model.lastError ?: "неизвестно"}"
                                        else -> "Не готова"
                                    }
                                    Text(statusText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (model.status == com.prostovoice.modelmanager.ModelState.READY) {
                                    RadioButton(selected = isCurrent, onClick = { onSelect(model) })
                                } else if (model.status == com.prostovoice.modelmanager.ModelState.FAILED) {
                                    TextButton(onClick = { onInstall(model) }) { Text("Повторить") }
                                }

                                if (!model.isBuiltIn) {
                                    IconButton(onClick = { onDelete(model) }) {
                                        Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    )
                }
                if (catalogModels.isNotEmpty()) {
                    item { 
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Каталог", style = MaterialTheme.typography.labelMedium) 
                    }
                    items(catalogModels) { model ->
                        ListItem(
                            headlineContent = { Text(model.name) },
                            supportingContent = { Text("${model.description} (~${model.sizeBytes/1024/1024} МБ)") },
                            trailingContent = {
                                TextButton(onClick = { onInstall(model) }) { Text("Загрузить") }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun TranscriptResultView(transcript: Transcript, onTxt: () -> Unit, onJson: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Результат: ${transcript.segments.size} реплик", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTxt) { Text("TXT") }
                Button(onClick = onJson) { Text("JSON") }
            }
        }
    }
}
