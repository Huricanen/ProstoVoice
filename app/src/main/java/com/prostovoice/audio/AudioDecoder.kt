package com.prostovoice.audio

import android.content.ContentResolver
import android.net.Uri
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

/** Bounded decoder for uncompressed PCM WAV files. MP3/M4A remain MediaCodec work. */
class WavPcmAudioDecoder(
    private val inputFramesPerChunk: Int = 4_096,
) : AudioDecoder {
    init {
        require(inputFramesPerChunk > 0) { "inputFramesPerChunk must be positive" }
    }

    override fun decode(resolver: ContentResolver, uri: Uri): Flow<PcmAudioChunk> = flow {
        val input = resolver.openInputStream(uri)
            ?: throw AudioDecodeException("Unable to open audio URI: $uri")
        input.use { stream -> decodeStream(stream).forEach { emit(it) } }
    }

    internal fun decodeStream(input: InputStream): Sequence<PcmAudioChunk> = sequence {
        val header = WavHeader.read(input)
        if (header.audioFormat != 1 || header.bitsPerSample != 16 || header.channels !in 1..2 || header.sampleRate <= 0) {
            throw AudioDecodeException("Only PCM 16-bit mono/stereo WAV is supported")
        }
        val bytesPerFrame = header.channels * 2
        val block = ByteArray(inputFramesPerChunk * bytesPerFrame)
        var sourceFrameOffset = 0L
        var outputSampleIndex = 0L
        var remaining = header.dataSize.toLong()
        while (remaining > 0) {
            val requested = minOf(block.size.toLong(), remaining).toInt()
            val bytesRead = input.readFully(block, requested)
            if (bytesRead == 0) break
            val framesRead = bytesRead / bytesPerFrame
            val output = FloatArray((framesRead * 16_000L / header.sampleRate).toInt() + 2)
            var outputCount = 0
            val blockEnd = sourceFrameOffset + framesRead
            while (true) {
                val sourceIndex = outputSampleIndex * header.sampleRate / 16_000L
                if (sourceIndex < sourceFrameOffset || sourceIndex >= blockEnd) break
                val frameOffset = (sourceIndex - sourceFrameOffset).toInt() * bytesPerFrame
                var sample = 0f
                repeat(header.channels) { channel ->
                    val offset = frameOffset + channel * 2
                    sample += ByteBuffer.wrap(block, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short / 32768f
                }
                output[outputCount++] = (sample / header.channels).coerceIn(-1f, 1f)
                outputSampleIndex++
            }
            if (outputCount > 0) yield(PcmAudioChunk(output.copyOf(outputCount), outputSampleIndex - outputCount))
            sourceFrameOffset = blockEnd
            remaining -= bytesRead
            if (bytesRead < requested) break
        }
    }
}

private data class WavHeader(
    val audioFormat: Int,
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataSize: Int,
) {
    companion object {
        fun read(input: InputStream): WavHeader {
            val reader = DataInputStream(input)
            val riff = try {
                reader.readFourCc()
            } catch (error: EOFException) {
                throw AudioDecodeException("Invalid WAV RIFF header", error)
            }
            if (riff != "RIFF") throw AudioDecodeException("Invalid WAV RIFF header")
            reader.readIntLE()
            if (reader.readFourCc() != "WAVE") throw AudioDecodeException("Invalid WAV WAVE header")
            var format: WavHeader? = null
            var dataSize: Int? = null
            while (format == null || dataSize == null) {
                val id = try { reader.readFourCc() } catch (_: EOFException) { break }
                val size = reader.readIntLE()
                when (id) {
                    "fmt " -> {
                        val audioFormat = reader.readShortLE()
                        val channels = reader.readShortLE()
                        val sampleRate = reader.readIntLE()
                        reader.readIntLE()
                        reader.readShortLE()
                        val bits = reader.readShortLE()
                        if (size > 16) reader.skipFully(size - 16)
                        format = WavHeader(audioFormat, channels, sampleRate, bits, 0)
                    }
                    "data" -> { dataSize = size; break }
                    else -> reader.skipFully(size)
                }
                if (size % 2 == 1) reader.skipFully(1)
            }
            val parsed = format ?: throw AudioDecodeException("WAV fmt chunk is missing")
            return parsed.copy(dataSize = dataSize ?: throw AudioDecodeException("WAV data chunk is missing"))
        }
    }
}

private fun DataInputStream.readFourCc(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.US_ASCII)
private fun DataInputStream.readIntLE(): Int = Integer.reverseBytes(readInt())
private fun DataInputStream.readShortLE(): Int {
    val value = readShort().toInt()
    return ((value ushr 8) and 0xff) or ((value and 0xff) shl 8)
}
private fun DataInputStream.skipFully(bytes: Int) {
    var left = bytes.toLong()
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) left -= skipped else { readByte(); left-- }
    }
}
private fun InputStream.readFully(buffer: ByteArray, length: Int): Int {
    var offset = 0
    while (offset < length) {
        val count = read(buffer, offset, length - offset)
        if (count < 0) break
        if (count == 0) continue
        offset += count
    }
    return offset
}
