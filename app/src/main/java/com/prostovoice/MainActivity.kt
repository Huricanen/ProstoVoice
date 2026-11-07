package com.prostovoice

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import com.prostovoice.domain.Transcript
import com.prostovoice.export.toJson
import com.prostovoice.export.toTxt
import com.prostovoice.ui.TranscriptionViewModel
import kotlinx.coroutines.delay

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
    var deviceProfile by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
            Scaffold(
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        state.selectedUri?.let { uri ->
                            AudioPlayer(uri = uri, transcript = state.transcript)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val transcript = state.transcript
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = transcript != null,
                                onClick = {
                                    transcript?.let {
                                        pendingExport = it.toTxt()
                                        exportLauncher.launch("${it.sourceName}.txt")
                                    }
                                },
                            ) { Text("TXT") }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = transcript != null,
                                onClick = {
                                    transcript?.let {
                                        pendingExport = it.toJson()
                                        jsonExportLauncher.launch("${it.sourceName}.json")
                                    }
                                },
                            ) { Text("JSON") }
                        }
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                Text("ProstoVoice", style = MaterialTheme.typography.headlineMedium)
                Text("Локальная расшифровка аудиозаписей")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { deviceProfile = com.prostovoice.nativebridge.NativePipeline.deviceProfile() },
                ) { Text("Диагностика устройства") }
                deviceProfile?.let { Text("Профиль: $it") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                },
                            )
                        },
                    ) { Text("Включить уведомления") }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { picker.launch(arrayOf("audio/*")) },
                ) {
                    Text("Выбрать аудиофайл")
                }
                if (state.history.isNotEmpty()) {
                    Text("Библиотека обработок", style = MaterialTheme.typography.titleMedium)
                    state.history.asReversed().forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = !state.isProcessing,
                                onClick = { viewModel.openHistory(item) },
                            ) {
                                Text("${item.transcript.sourceName} · ${item.transcript.segments.size} реплик")
                            }
                            Button(
                                enabled = !state.isProcessing,
                                onClick = { viewModel.removeHistory(item) },
                            ) { Text("Удалить") }
                        }
                    }
                }
                state.selectedUri?.let { uri ->
                    Text("Выбран файл: ${uri.lastPathSegment ?: uri}")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing,
                        onClick = viewModel::transcribe,
                    ) {
                        Text("Автоматически определить голоса")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing,
                        onClick = viewModel::transcribeSingleSpeaker,
                    ) {
                        Text("Обработать как один голос")
                    }
                }
                if (state.isProcessing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("${state.processingStage}\n${formatElapsed(state.elapsedMs)}")
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.errorMessage?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }
                state.transcript?.let { transcript ->
                    TranscriptResult(
                        transcript = transcript,
                    )
                }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AudioPlayer(uri: android.net.Uri, transcript: Transcript?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(transcript?.durationMs?.coerceAtLeast(1L) ?: 1L) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uri) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener {
            durationMs = it.duration.toLong().coerceAtLeast(transcript?.durationMs ?: 0L)
            prepared = true
            error = null
        }
        mediaPlayer.setOnCompletionListener {
            playing = false
            positionMs = 0L
            it.seekTo(0)
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            prepared = false
            playing = false
            error = "Не удалось воспроизвести аудио"
            true
        }
        runCatching {
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepareAsync()
        }.onFailure {
            error = "Не удалось открыть аудио"
        }
        onDispose {
            mediaPlayer.release()
            if (player === mediaPlayer) player = null
        }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            player?.let { positionMs = it.currentPosition.toLong() }
            delay(200L)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Прослушивание", style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            androidx.compose.material3.Slider(
                value = positionMs.coerceIn(0L, durationMs).toFloat(),
                onValueChange = { value ->
                    positionMs = value.toLong()
                    player?.seekTo(positionMs.toInt())
                },
                valueRange = 0f..durationMs.toFloat(),
                enabled = prepared,
                modifier = Modifier.fillMaxWidth(),
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                if (durationMs > 0L) {
                    transcript?.segments?.forEach { segment ->
                        val start = segment.startMs.coerceIn(0L, durationMs).toFloat() / durationMs
                        val end = segment.endMs.coerceIn(0L, durationMs).toFloat() / durationMs
                        if (end > start) {
                            drawRect(
                                color = speakerColor(segment.speaker),
                                topLeft = Offset(size.width * start, 8f),
                                size = Size(size.width * (end - start), size.height - 16f),
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatClock(positionMs))
            Text(formatClock(durationMs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = prepared,
                onClick = {
                    player?.let {
                        if (playing) {
                            it.pause()
                            playing = false
                        } else {
                            it.start()
                            playing = true
                        }
                    }
                },
            ) { Text(if (playing) "Пауза" else "Воспроизвести") }
            Button(
                enabled = prepared && (playing || positionMs > 0L),
                onClick = {
                    player?.pause()
                    player?.seekTo(0)
                    positionMs = 0L
                    playing = false
                },
            ) { Text("Стоп") }
        }
        Text(
            if (error != null) error!! else if (transcript == null) "Метки появятся после обработки" else "Цветные блоки — временные интервалы реплик",
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun speakerColor(speaker: String): Color {
    val number = Regex("speaker_(\\d+)").find(speaker)?.groupValues?.get(1)?.toIntOrNull()
    val colors = listOf(
        Color(0xFFE05252), // speaker 1: red
        Color(0xFF4C8DDE), // speaker 2: blue
        Color(0xFF4FAF72), // speaker 3: green
        Color(0xFFE39A3B), // speaker 4: orange
        Color(0xFF9B6BD6), // speaker 5: purple
    )
    return if (number != null && number >= 1 && number <= colors.size) {
        colors[number - 1].copy(alpha = 0.82f)
    } else if ((number ?: 6) % 2 == 0) {
        Color(0xFFB85C5C).copy(alpha = 0.72f)
    } else {
        Color(0xFFE05252).copy(alpha = 0.82f)
    }
}

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatElapsed(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "Время: %02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@androidx.compose.runtime.Composable
private fun TranscriptResult(
    transcript: Transcript,
) {
    Text("Результат: ${transcript.segments.size} реплик")
    Text("Время обработки: ${formatElapsed(transcript.processingTimeMs)}")
    transcript.segments.forEach { segment ->
        Text("${segment.speaker} [${segment.startMs}–${segment.endMs}]\n${segment.text}")
    }
}
