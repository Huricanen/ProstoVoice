package com.prostovoice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16StreamConverterTest {
    @Test
    fun downmixesStereoAndResamplesEightKhzToSixteenKhz() {
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(32767)
            .putShort(0)
            .putShort(0)
            .putShort(32767)
        input.flip()

        val converter = Pcm16StreamConverter(sampleRate = 8_000, channels = 2)
        val chunks = converter.consume(input) + converter.finish()

        assertEquals(4, chunks.sumOf { it.samples.size })
        assertEquals(0.5f, chunks.first().samples.first(), 0.01f)
        assertEquals(0.5f, chunks.last().samples.last(), 0.01f)
    }
}
