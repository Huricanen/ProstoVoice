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
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * Production-ready implementation for Sherpa-ONNX based models (Parakeet, GigaAM).
 * Supports NeMo CTC architecture.
 */
class SherpaTranscriptionBackend(private val context: Context) : TranscriptionBackend {
    
    override suspend fun transcribe(uri: Uri, model: ModelInfo): Transcript {
        val modelFile = File(model.path)
        val tokensFile = File(model.tokensPath)
        
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            throw TranscriptionUnavailableException("Файл модели отсутствует или поврежден: ${modelFile.name}")
        }
        if (!tokensFile.exists() || tokensFile.length() < 10) {
            throw TranscriptionUnavailableException("Файл словаря (tokens) не найден. Переустановите модель.")
        }

        Log.i("SherpaBackend", "Initializing Sherpa-ONNX for: ${model.name}")

        // Initialize Sherpa-ONNX configuration with NeMo CTC support
        // We use the empty constructor and set properties to be safe with different SDK versions
        val config = OfflineRecognizerConfig()
        
        config.modelConfig.tokens = tokensFile.absolutePath
        config.modelConfig.numThreads = 4
        config.modelConfig.debug = true
        config.modelConfig.provider = "cpu"
        config.modelConfig.nemo.model = modelFile.absolutePath
        config.decodingMethod = "greedy_search"
        
        val recognizer = try {
            // AssetManager is required by some versions, providing it from context
            OfflineRecognizer(context.assets, config)
        } catch (e: Exception) {
            Log.e("SherpaBackend", "Failed to init recognizer", e)
            throw TranscriptionUnavailableException("Ошибка инициализации движка Sherpa-ONNX: ${e.message}")
        }

        try {
            val chunks = mutableListOf<PcmAudioChunk>()
            MediaCodecAudioDecoder().decode(context.contentResolver, uri).collect { chunks += it }
            val samples = FloatArray(chunks.sumOf { it.samples.size })
            var offset = 0
            chunks.forEach { chunk ->
                chunk.samples.copyInto(samples, offset)
                offset += chunk.samples.size
            }

            Log.i("SherpaBackend", "Decoding ${samples.size} samples")
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            
            val result = recognizer.getResult(stream)
            val durationMs = samples.size * 1000L / 16000L
            
            stream.release()

            Log.i("SherpaBackend", "Transcription finished successfully. Result: ${result.text.take(50)}...")

            val asr = listOf(AsrSegment(result.text, 0, durationMs))
            return Transcript(
                sourceName = uri.lastPathSegment ?: "audio",
                durationMs = durationMs,
                segments = TranscriptAligner.align(asr, emptyList(), durationMs),
            )
        } catch (e: Exception) {
            Log.e("SherpaBackend", "Error during transcription", e)
            throw TranscriptionUnavailableException("Ошибка при распознавании: ${e.message}")
        } finally {
            recognizer.release()
        }
    }

    override fun isSupported(model: ModelInfo): Boolean {
        return model.family == ModelFamily.PARAKEET || 
               model.family == ModelFamily.GIGAAM_V3 || 
               model.family == ModelFamily.GIGAAM_V3_MULTILINGUAL
    }
}
