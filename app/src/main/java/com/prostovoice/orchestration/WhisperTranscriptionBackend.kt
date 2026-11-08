package com.prostovoice.orchestration

import android.content.Context
import android.net.Uri
import android.util.Log
import com.prostovoice.audio.MediaCodecAudioDecoder
import com.prostovoice.audio.PcmAudioChunk
import com.prostovoice.domain.AsrSegment
import com.prostovoice.domain.Transcript
import com.prostovoice.domain.TranscriptAligner
import com.prostovoice.modelmanager.ModelFamily
import com.prostovoice.modelmanager.ModelInfo
import com.prostovoice.nativebridge.NativePipeline
import org.json.JSONObject

class WhisperTranscriptionBackend(private val context: Context) : TranscriptionBackend {
    private val appContext = context.applicationContext

    override suspend fun transcribe(uri: Uri, model: ModelInfo): Transcript {
        check(NativePipeline.isAvailable) { "Native Whisper backend unavailable" }
        
        val chunks = mutableListOf<PcmAudioChunk>()
        MediaCodecAudioDecoder().decode(appContext.contentResolver, uri).collect { chunks += it }
        val samples = FloatArray(chunks.sumOf { it.samples.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.samples.copyInto(samples, offset)
            offset += chunk.samples.size
        }
        
        Log.i(TAG, "Starting Whisper with ${model.path}")
        val startedAt = System.nanoTime()
        val response = JSONObject(NativePipeline.transcribePcm(samples, 16_000, model.path))
        Log.i(TAG, "Whisper finished in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
        
        check(response.optString("status") == "ok") {
            "Whisper error: ${response.optString("status", "unknown")}"
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

    override fun isSupported(model: ModelInfo): Boolean {
        return model.family == ModelFamily.WHISPER
    }

    companion object {
        private const val TAG = "WhisperBackend"
    }
}
