package com.prostovoice

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prostovoice.domain.Transcript
import com.prostovoice.export.toJson
import com.prostovoice.export.toTxt
import com.prostovoice.ui.TranscriptionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProstoVoiceApp() }
    }
}

@androidx.compose.runtime.Composable
private fun ProstoVoiceApp() {
    val viewModel: TranscriptionViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::selectFile)
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

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("ProstoVoice", style = MaterialTheme.typography.headlineMedium)
                Text("Локальная расшифровка аудиозаписей")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { picker.launch(arrayOf("audio/*")) },
                ) {
                    Text("Выбрать аудиофайл")
                }
                state.selectedUri?.let { uri ->
                    Text("Выбран файл: ${uri.lastPathSegment ?: uri}")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing,
                        onClick = viewModel::transcribe,
                    ) {
                        Text("Запустить расшифровку")
                    }
                }
                if (state.isProcessing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Обработка…")
                    }
                }
                state.errorMessage?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }
                state.transcript?.let { transcript ->
                    TranscriptResult(
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
    }
}

@androidx.compose.runtime.Composable
private fun TranscriptResult(
    transcript: Transcript,
    onTxt: () -> Unit,
    onJson: () -> Unit,
) {
    Text("Результат: ${transcript.segments.size} реплик")
    transcript.segments.forEach { segment ->
        Text("${segment.speaker} [${segment.startMs}–${segment.endMs}]\n${segment.text}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTxt) { Text("TXT") }
        Button(onClick = onJson) { Text("JSON") }
    }
}
