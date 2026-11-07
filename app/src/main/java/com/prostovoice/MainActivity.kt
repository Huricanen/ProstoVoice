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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
            Column(
                modifier = Modifier
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
                    state.selectedUri?.let { uri ->
                        AudioPlayer(uri = uri, transcript = transcript)
                    }
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
private fun AudioPlayer(uri: android.net.Uri, transcript: Transcript) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(transcript.durationMs.coerceAtLeast(1L)) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uri) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener {
            durationMs = it.duration.toLong().coerceAtLeast(transcript.durationMs)
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
                    transcript.segments.drop(1).forEach { segment ->
                        val x = size.width * (segment.startMs.coerceIn(0L, durationMs).toFloat() / durationMs)
                        drawLine(
                            color = Color.Red.copy(alpha = 0.8f),
                            start = Offset(x, 5f),
                            end = Offset(x, size.height - 5f),
                            strokeWidth = 2f,
                        )
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
            if (error != null) error!! else "Красные отметки — границы реплик после обработки",
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
    onTxt: () -> Unit,
    onJson: () -> Unit,
) {
    Text("Результат: ${transcript.segments.size} реплик")
    Text("Время обработки: ${formatElapsed(transcript.processingTimeMs)}")
    transcript.segments.forEach { segment ->
        Text("${segment.speaker} [${segment.startMs}–${segment.endMs}]\n${segment.text}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTxt) { Text("TXT") }
        Button(onClick = onJson) { Text("JSON") }
    }
}
