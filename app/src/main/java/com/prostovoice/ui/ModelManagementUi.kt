package com.prostovoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prostovoice.modelmanager.ModelCatalog

@Composable
fun ModelManagementScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: ModelManagementViewModel = viewModel(factory = ModelManagementViewModelFactory(context.applicationContext))
    val state by viewModel.state.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFromUri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Модели распознавания", style = MaterialTheme.typography.headlineSmall)
            Text("Приложение выбирает рабочую модель автоматически и сохраняет резервную Whisper-модель.")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Импорт модели") }
                Button(onClick = { viewModel.refresh() }) { Text("Обновить") }
            }
        }

        item {
            state.lastMessage?.let { Text(it) }
        }

        items(state.models) { model ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(model.name)
                        if (model.isActive) Text("Активна")
                        if (model.isBuiltIn) Text("Встроенная")
                    }
                    Text(model.backend)
                    Text(model.status.name)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.activate(model.id) },
                            enabled = model.status.name == "READY" && !model.isActive,
                        ) {
                            Text("Использовать")
                        }
                        Button(
                            onClick = { viewModel.delete(model.id) },
                            enabled = !model.isBuiltIn,
                        ) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }

        item { Text("Каталог:") }

        items(ModelCatalog.availableModels()) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.displayName)
                    Text(entry.description)
                    Button(
                        onClick = { viewModel.install(entry) },
                        enabled = entry.downloadUrl != null || entry.fileName.endsWith(".onnx") || entry.fileName.endsWith(".bin"),
                    ) {
                        Text(if (entry.downloadUrl != null) "Скачать и установить" else "Использовать файл")
                    }
                }
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            ) {
                Text("Назад")
            }
        }
    }
}
