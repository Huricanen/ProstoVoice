package com.prostovoice.orchestration

import android.content.Context
import android.net.Uri
import android.util.Log
import com.prostovoice.audio.MediaCodecAudioDecoder
import com.prostovoice.audio.PcmAudioChunk
import com.prostovoice.domain.AsrSegment
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptAligner
import com.prostovoice.modelmanager.ModelManager
import com.prostovoice.modelmanager.ModelSelector
import com.prostovoice.nativebridge.NativePipeline
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

/** Local Whisper-only pipeline with a centralized model selection fallback. */
class WhisperTranscriptionOrchestrator(
    context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
) : TranscriptionOrchestrator {
    private val appContext = context.applicationContext
    private val modelManager = ModelManager(appContext)
    private val selector = ModelSelector(modelManager, appContext)

    override suspend fun transcribe(uri: Uri): Transcript {
        check(NativePipeline.isAvailable) { "Native Whisper backend недоступен" }

        val activeModel = runCatching { selector.pickPreferredModel() }
            .getOrElse { modelManager.ensureBuiltInModel() }

        val modelPath = if (modelManager.validateModelPath(activeModel.path)) {
            activeModel.path
        } else {
            modelManager.ensureBuiltInModel().path
        }
        check(modelManager.validateModelPath(modelPath)) {
            "Не удалось подготовить встроенную Whisper-модель для обработки аудио"
        }

        val chunks = mutableListOf<PcmAudioChunk>()
        MediaCodecAudioDecoder().decode(appContext.contentResolver, uri).collect { chunks += it }
        val samples = FloatArray(chunks.sumOf { it.samples.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.samples.copyInto(samples, offset)
            offset += chunk.samples.size
        }
        Log.i(TAG, "Decoded ${samples.size} samples (${samples.size / 16_000f}s), starting Whisper with $modelPath")
        val startedAt = System.nanoTime()
        val response = JSONObject(NativePipeline.transcribePcm(samples, 16_000, modelPath))
        Log.i(TAG, "Whisper finished in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
        check(response.optString("status") == "ok") {
            "Whisper завершился с ошибкой: ${response.optString("status", "unknown")}"
        }
        val asr = buildList {
            val segments = response.optJSONArray("segments") ?: return@buildList
            for (index in 0 until segments.length()) {
                val item = segments.getJSONObject(index)
                add(AsrSegment(item.optString("text"), item.optLong("start_ms"), item.optLong("end_ms")))
            }
        }
        val durationMs = samples.size * 1_000L / 16_000L
        return Transcript(
            sourceName = uri.lastPathSegment ?: "audio",
            durationMs = durationMs,
            segments = TranscriptAligner.align(asr, emptyList(), durationMs),
        )
    }

    companion object {
        const val DEFAULT_MODEL_FILE = "ggml-base.bin"
        private const val TAG = "ProstoVoiceWhisper"
    }
}
