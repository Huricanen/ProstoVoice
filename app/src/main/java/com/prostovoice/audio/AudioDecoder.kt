package com.prostovoice.audio

import android.content.ContentResolver
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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

class MediaCodecAudioDecoder : AudioDecoder {
    override fun decode(resolver: ContentResolver, uri: Uri): Flow<PcmAudioChunk> = flow {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r")
            ?: throw AudioDecodeException("Unable to open audio URI: $uri")
        descriptor.use { asset ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw AudioDecodeException("No audio track found in $uri")
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: throw AudioDecodeException("Audio MIME type is missing for $uri")
                extractor.selectTrack(trackIndex)
                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val converter = Pcm16StreamConverter(
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    )
                    var inputDone = false
                    var outputDone = false
                    val info = MediaCodec.BufferInfo()
                    while (!outputDone) {
                        if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                            if (inputIndex >= 0) {
                                val input = codec.getInputBuffer(inputIndex)
                                    ?: throw AudioDecodeException("Decoder input buffer is unavailable")
                                val size = extractor.readSampleData(input, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> converter.updateFormat(codec.outputFormat)
                            else -> if (outputIndex >= 0) {
                                if (info.size > 0) {
                                    val output = codec.getOutputBuffer(outputIndex)
                                        ?: throw AudioDecodeException("Decoder output buffer is unavailable")
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    converter.consume(output).forEach { emit(it) }
                                }
                                codec.releaseOutputBuffer(outputIndex, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    converter.finish().forEach { emit(it) }
                                    outputDone = true
                                }
                            }
                        }
                    }
                } finally {
                    runCatching { codec.stop() }
                    codec.release()
                }
            } catch (error: AudioDecodeException) {
                throw error
            } catch (error: Exception) {
                throw AudioDecodeException("Unable to decode audio URI: $uri", error)
            } finally {
                extractor.release()
            }
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

/** Converts bounded PCM16 decoder output to bounded mono 16 kHz chunks. */
internal class Pcm16StreamConverter(
    sampleRate: Int,
    channels: Int,
    private val outputChunkSize: Int = 4_096,
) {
    private var sourceRate = sampleRate
    private var channelCount = channels
    private var sourceFrameIndex = 0L
    private var outputSampleIndex = 0L
    private val pending = ArrayList<Float>(outputChunkSize)

    init {
        require(sourceRate > 0) { "sampleRate must be positive" }
        require(channelCount in 1..2) { "Only mono/stereo PCM is supported" }
        require(outputChunkSize > 0) { "outputChunkSize must be positive" }
    }

    fun updateFormat(format: MediaFormat) {
        sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        require(sourceRate > 0 && channelCount in 1..2) { "Unsupported decoder output format" }
    }

    fun consume(buffer: ByteBuffer): List<PcmAudioChunk> {
        val bytes = buffer.remaining() - (buffer.remaining() % (channelCount * 2))
        val result = mutableListOf<PcmAudioChunk>()
        var offset = buffer.position()
        val end = offset + bytes
        while (offset < end) {
            var mono = 0f
            repeat(channelCount) {
                mono += buffer.getShort(offset).toFloat() / 32768f
                offset += 2
            }
            val sourceIndex = sourceFrameIndex++
            while (outputSampleIndex * sourceRate < (sourceIndex + 1L) * TARGET_RATE) {
                pending += (mono / channelCount).coerceIn(-1f, 1f)
                outputSampleIndex++
                if (pending.size == outputChunkSize) result += drain()
            }
        }
        buffer.position(end)
        return result
    }

    fun finish(): List<PcmAudioChunk> = if (pending.isEmpty()) emptyList() else listOf(drain())

    private fun drain(): PcmAudioChunk {
        val samples = pending.toFloatArray()
        pending.clear()
        return PcmAudioChunk(samples, outputSampleIndex - samples.size)
    }

    private companion object {
        const val TARGET_RATE = 16_000L
    }
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
