package com.prostovoice.audio

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/** A bounded PCM block produced by the decoder. Samples are mono float PCM at 16 kHz. */
data class PcmAudioChunk(
    val samples: FloatArray,
    val startSample: Long,
) {
    init {
        require(startSample >= 0L) { "startSample must be non-negative" }
        require(samples.isNotEmpty()) { "samples must not be empty" }
        require(samples.all { it in -1f..1f }) { "samples must be normalized to [-1, 1]" }
    }

    val endSample: Long
        get() = startSample + samples.size
}

interface AudioDecoder {
    /**
     * Decodes an Android content URI into bounded 16 kHz mono PCM chunks.
     * Implementations must not copy the complete recording into the Java heap.
     */
    fun decode(resolver: ContentResolver, uri: Uri): Flow<PcmAudioChunk>
}

class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Temporary boundary for the MediaExtractor/MediaCodec implementation.
 * It fails explicitly until codec handling and resampling are wired and tested.
 */
class MediaCodecAudioDecoder : AudioDecoder {
    override fun decode(resolver: ContentResolver, uri: Uri): Flow<PcmAudioChunk> =
        throw AudioDecodeException(
            "Audio decoding is not configured yet for $uri; MediaExtractor/MediaCodec adapter is required",
        )
}
