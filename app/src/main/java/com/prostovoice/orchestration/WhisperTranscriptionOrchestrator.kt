package com.prostovoice.orchestration

import android.content.Context
import android.net.Uri
import android.util.Log
import com.prostovoice.audio.MediaCodecAudioDecoder
import com.prostovoice.audio.PcmAudioChunk
import com.prostovoice.audio.AudioChunker
import com.prostovoice.domain.AsrSegment
import com.prostovoice.domain.SpeakerSegment
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptAligner
import com.prostovoice.nativebridge.NativePipeline
import com.prostovoice.diarization.WindowEmbedding
import com.prostovoice.diarization.WindowedSpeakerClustering
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

/** Local Whisper-only pipeline. Diarization is intentionally a later stage. */
class WhisperTranscriptionOrchestrator(
    private val context: Context,
    private val modelFileName: String = DEFAULT_MODEL_FILE,
) : TranscriptionOrchestrator {
    override suspend fun transcribe(uri: Uri): Transcript {
        return transcribe(uri) { _, _ -> }
    }

    suspend fun transcribe(uri: Uri, onProgress: (Float, String) -> Unit): Transcript {
        check(NativePipeline.isAvailable) { "Native Whisper backend недоступен" }
        onProgress(0.02f, "Подготовка модели")
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
        onProgress(0.10f, "Аудио декодировано")
        Log.i(TAG, "Decoded ${samples.size} samples (${samples.size / 16_000f}s), starting Whisper")
        val startedAt = System.nanoTime()
        val asr = mutableListOf<AsrSegment>()
        val inferenceChunks = AudioChunker.split(samples)
        inferenceChunks.forEachIndexed { index, chunk ->
            Log.i(TAG, "Whisper chunk ${index + 1}/${inferenceChunks.size}: ${chunk.samples.size / 16_000f}s")
            val response = JSONObject(NativePipeline.transcribePcm(chunk.samples, 16_000, model.absolutePath))
            check(response.optString("status") == "ok") {
                "Whisper chunk ${index + 1} завершился с ошибкой: ${response.optString("status", "unknown")}"
            }
            val offsetMs = chunk.startSample * 1_000L / 16_000L
            val segments = response.optJSONArray("segments") ?: return@forEachIndexed
            for (segmentIndex in 0 until segments.length()) {
                val item = segments.getJSONObject(segmentIndex)
                asr += AsrSegment(
                    item.optString("text"),
                    item.optLong("start_ms") + offsetMs,
                    item.optLong("end_ms") + offsetMs,
                )
            }
            onProgress(0.10f + 0.65f * (index + 1).toFloat() / inferenceChunks.size, "Распознавание речи")
        }
        val deduplicatedAsr = AudioChunker.deduplicateAsrSegments(asr)
        Log.i(TAG, "Whisper finished in ${(System.nanoTime() - startedAt) / 1_000_000} ms across ${inferenceChunks.size} chunks")
        val durationMs = samples.size * 1_000L / 16_000L
        val speakerSegments: List<SpeakerSegment> = runCatching {
            onProgress(0.80f, "Разделение по говорящим")
            val embedding = WhisperModelStore(context, EMBEDDING_MODEL).ensureModel()
            val windowed = JSONObject(
                NativePipeline.extractWindowEmbeddings(samples, 16_000, embedding.absolutePath),
            )
            check(windowed.optString("status") == "ok")
            val windows = buildList {
                val items = windowed.optJSONArray("windows") ?: return@buildList
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val values = item.optJSONArray("values") ?: continue
                    add(WindowEmbedding(
                        item.optLong("start_ms"), item.optLong("end_ms"),
                        FloatArray(values.length()) { values.optDouble(it).toFloat() },
                    ))
                }
            }
            val maxClusters = if (durationMs < 45_000L) 2 else 4
            val windowedSegments = WindowedSpeakerClustering.cluster(windows, maxClusters)
            if (windowedSegments.isNotEmpty()) {
                windowedSegments
            } else {
                val segmentation = WhisperModelStore(context, SEGMENTATION_MODEL).ensureModel()
                val diarization = JSONObject(
                    NativePipeline.diarizePcm(samples, 16_000, segmentation.absolutePath, embedding.absolutePath),
                )
                check(diarization.optString("status") == "ok")
                val rawSegments = buildList {
                    val segments = diarization.optJSONArray("segments") ?: return@buildList
                    for (index in 0 until segments.length()) {
                        val item = segments.getJSONObject(index)
                        add(SpeakerSegment(
                            item.optString("speaker"), item.optLong("start_ms"), item.optLong("end_ms"),
                        ))
                    }
                }
                TranscriptAligner.normalizeSpeakerLabels(rawSegments)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Diarization unavailable; using UNKNOWN speakers", error)
            emptyList()
        }
        onProgress(0.98f, "Сборка результата")
        return Transcript(
            sourceName = uri.lastPathSegment ?: "audio",
            durationMs = durationMs,
            segments = TranscriptAligner.align(deduplicatedAsr, speakerSegments, durationMs),
        )
    }

    companion object {
        const val DEFAULT_MODEL_FILE = "ggml-large-v3-turbo-q5_0.bin"
        const val SEGMENTATION_MODEL = "pyannote-segmentation-3.0-int8.onnx"
        const val EMBEDDING_MODEL = "eres2net-base-3dspeaker-16k.onnx"
        private const val TAG = "ProstoVoiceWhisper"
    }

}
