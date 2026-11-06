package com.prostovoice.orchestration

import android.content.Context
import android.net.Uri
import android.util.Log
import com.prostovoice.audio.MediaCodecAudioDecoder
import com.prostovoice.audio.PcmAudioChunk
import com.prostovoice.domain.AsrSegment
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptAligner
import com.prostovoice.nativebridge.NativePipeline
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

/** Local Whisper-only pipeline. Diarization is intentionally a later stage. */
class WhisperTranscriptionOrchestrator(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
) : TranscriptionOrchestrator {
    override suspend fun transcribe(uri: Uri): Transcript {
        check(NativePipeline.isAvailable) { "Native Whisper backend недоступен" }
        val model = runCatching { WhisperModelStore(context, modelFileName).ensureModel() }
            .getOrElse { error ->
                throw IllegalStateException("Не удалось подготовить встроенную Whisper-модель", error)
            }
        val chunks = mutableListOf<PcmAudioChunk>()
        MediaCodecAudioDecoder().decode(context.contentResolver, uri).collect { chunks += it }
        val samples = FloatArray(chunks.sumOf { it.samples.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.samples.copyInto(samples, offset)
            offset += chunk.samples.size
        }
        Log.i(TAG, "Decoded ${samples.size} samples (${samples.size / 16_000f}s), starting Whisper")
        val startedAt = System.nanoTime()
        val response = JSONObject(NativePipeline.transcribePcm(samples, 16_000, model.absolutePath))
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
        val speakerSegments = runCatching {
            val segmentation = WhisperModelStore(context, SEGMENTATION_MODEL).ensureModel()
            val embedding = WhisperModelStore(context, EMBEDDING_MODEL).ensureModel()
            val diarization = JSONObject(
                NativePipeline.diarizePcm(samples, 16_000, segmentation.absolutePath, embedding.absolutePath),
            )
            check(diarization.optString("status") == "ok")
            buildList {
                val segments = diarization.optJSONArray("segments") ?: return@buildList
                for (index in 0 until segments.length()) {
                    val item = segments.getJSONObject(index)
                    add(com.prostovoice.domain.SpeakerSegment(
                        item.optString("speaker"), item.optLong("start_ms"), item.optLong("end_ms"),
                    ))
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Diarization unavailable; using UNKNOWN speakers", error)
            emptyList()
        }
        return Transcript(
            sourceName = uri.lastPathSegment ?: "audio",
            durationMs = durationMs,
            segments = TranscriptAligner.align(asr, speakerSegments, durationMs),
        )
    }

    companion object {
        const val DEFAULT_MODEL_FILE = "ggml-small-q5_1.bin"
        const val SEGMENTATION_MODEL = "pyannote-segmentation-3.0-int8.onnx"
        const val EMBEDDING_MODEL = "eres2net-base-3dspeaker-16k.onnx"
        private const val TAG = "ProstoVoiceWhisper"
    }
}
