package com.prostovoice.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavPcmAudioDecoderTest {
    @Test
    fun decodesStereoPcmAndResamplesTo16KhzMono() {
        val wav = wavPcm(sampleRate = 8_000, channels = 2, samples = listOf(32767 to 0, 0 to 32767))
        val chunks = WavPcmAudioDecoder(inputFramesPerChunk = 1)
            .decodeStream(ByteArrayInputStream(wav)).toList()

        assertEquals(4, chunks.sumOf { it.samples.size })
        assertEquals(0.5f, chunks.first().samples.first(), 0.01f)
        assertTrue(chunks.all { it.samples.all { sample -> sample in -1f..1f } })
    }

    @Test(expected = AudioDecodeException::class)
    fun rejectsNonWavInput() {
        WavPcmAudioDecoder().decodeStream(ByteArrayInputStream(byteArrayOf(1, 2, 3))).toList()
    }

    private fun wavPcm(sampleRate: Int, channels: Int, samples: List<Pair<Int, Int>>): ByteArray {
        val data = ByteArrayOutputStream()
        samples.forEach { (left, right) ->
            writeShort(data, left)
            if (channels == 2) writeShort(data, right)
        }
        val payload = data.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        writeInt(out, 36 + payload.size)
        out.write("WAVEfmt ".toByteArray())
        writeInt(out, 16)
        writeShort(out, 1)
        writeShort(out, channels)
        writeInt(out, sampleRate)
        writeInt(out, sampleRate * channels * 2)
        writeShort(out, channels * 2)
        writeShort(out, 16)
        out.write("data".toByteArray())
        writeInt(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }
}
